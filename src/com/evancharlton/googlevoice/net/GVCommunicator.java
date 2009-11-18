package com.evancharlton.googlevoice.net;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectHandler;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.CallLog;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.evancharlton.googlevoice.PreferencesProvider;
import com.evancharlton.googlevoice.objects.Message;
import com.evancharlton.googlevoice.objects.Phone;
import com.evancharlton.googlevoice.objects.Voicemail;

public class GVCommunicator {
	private static final String TAG = "GV_GVC";
	public static final String DOMAIN = "www.google.com";
	public static final String BASE = "https://" + DOMAIN;
	public static final String VOICE = BASE + "/voice";
	public static final String MOBILE = VOICE + "/m";
	public static final String VOICEMAIL_FOLDER = Environment.getExternalStorageDirectory() + "/gv-voicemail/";
	public static final String PDA_COOKIE_NAME = "gv-ph";

	private static GVCommunicator s_instance;

	private String m_username = "";
	private String m_password = "";
	private String m_token = "";
	private DefaultHttpClient m_client;
	private String m_gvNumber = null;
	private String m_error = "";
	private PreferencesProvider m_prefs = null;
	private String m_auth = "";

	private static StringBuilder s_builder = new StringBuilder();

	protected GVCommunicator(Context context) {
		m_prefs = PreferencesProvider.getInstance(context);
		m_username = m_prefs.getString(PreferencesProvider.USERNAME, "");
		m_password = m_prefs.getString(PreferencesProvider.PASSWORD, "");
		m_gvNumber = m_prefs.getString(PreferencesProvider.GV_NUMBER, "");

		m_client = new DefaultHttpClient();
		m_client.setRedirectHandler(new DefaultRedirectHandler());
		m_client.getParams().setBooleanParameter("http.protocol.expect-continue", false);
	}

	public static GVCommunicator getInstance(Context context) {
		if (s_instance == null) {
			s_instance = new GVCommunicator(context);
		}
		return s_instance;
	}

	public boolean login() {
		if (!isLoggedIn()) {
			return login(m_username, m_password);
		}
		return isLoggedIn();
	}

	public void logout() {
		m_client = new DefaultHttpClient();
		m_token = "";
	}

	private void storeToken(String token) {
		m_prefs.write(PreferencesProvider.CLIENT_TOKEN, token);
		BasicClientCookie cookie = new BasicClientCookie("gv", token);
		cookie.setDomain("www.google.com");
		cookie.setPath("/voice");
		cookie.setSecure(true);
		m_client.getCookieStore().addCookie(cookie);
	}

	public boolean login(String username, String password) {
		m_error = "";
		if (username.trim().length() == 0 || password.trim().length() == 0) {
			m_error = "No Google Voice login information saved!";
			return false;
		}

		// see if they have a stored ClientLogin token so we can skip this
		// process
		String token = m_prefs.getString(PreferencesProvider.CLIENT_TOKEN, "");
		m_token = m_prefs.getString(PreferencesProvider.RNR_SE_TOKEN, "");
		// FIXME: Re-use of tokens stopped working and I don't know why.
		if (false && token.length() > 0 && m_token.length() > 0) {
			Log.d(TAG, "Reusing stored tokens");
			storeToken(token);
			return true;
		}
		Log.d(TAG, "No token(s) stored, so logging in to Google Voice");

		m_username = username;
		m_password = password;

		m_client = new DefaultHttpClient();
		m_client.setRedirectHandler(new DefaultRedirectHandler());
		m_client.getParams().setBooleanParameter("http.protocol.expect-continue", false);

		List<NameValuePair> data = new ArrayList<NameValuePair>();
		data.add(new BasicNameValuePair("accountType", "GOOGLE"));
		data.add(new BasicNameValuePair("Email", username));
		data.add(new BasicNameValuePair("Passwd", password));
		data.add(new BasicNameValuePair("service", "grandcentral"));
		data.add(new BasicNameValuePair("source", "com-evancharlton-googlevoice-android-GV"));

		HttpPost post = new HttpPost("https://www.google.com/accounts/ClientLogin");
		m_error = "";
		try {
			post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));
			HttpResponse response = m_client.execute(post);
			BufferedReader is = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			String line;
			while ((line = is.readLine()) != null) {
				if (line.startsWith("Auth")) {
					token = line.substring(5);
					storeToken(token);
					break;
				}
			}
			is.close();
			HttpGet get = new HttpGet("https://www.google.com/voice/m/i/voicemail?p=10000");
			response = m_client.execute(get);
			is = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			Pattern rnrse = Pattern.compile("name=\"_rnr_se\"\\s*value=\"([^\"]+)\"");
			Matcher m;
			boolean valid = false;
			while ((line = is.readLine()) != null) {
				if (line.indexOf("The username or password you entered is incorrect.") >= 0) {
					m_error = "The username or password you entered is incorrect.";
					break;
				} else {
					if (line.indexOf("google.com/support/voice/bin/answer.py?answer=142423") >= 0) {
						m_error = "This Google Account does not have a Google Voice account.";
						break;
					} else {
						m = rnrse.matcher(line);
						if (m.find()) {
							m_error = "";
							m_token = m.group(1);
							m_prefs.write(PreferencesProvider.RNR_SE_TOKEN, m_token);
							valid = true;
							break;
						}
					}
				}
			}
			is.close();
			return valid;
		} catch (Exception e) {
			e.printStackTrace();
			m_error = "Network error! Try cycling wi-fi (if enabled).";
		}
		return false;
	}

	public static boolean call(Context context, String number) {
		PreferencesProvider prefs = PreferencesProvider.getInstance(context);
		String gvNumber = prefs.getString(PreferencesProvider.GV_NUMBER, "");
		String pin = "";
		if (prefs.getBoolean(PreferencesProvider.PIN_REQUIRED, true)) {
			pin = prefs.getString(PreferencesProvider.PIN_NUMBER, "");
		}
		Intent i = new Intent(Intent.ACTION_CALL);
		Uri uri = Uri.parse("tel:" + buildNumber(gvNumber, number, pin));
		i.setData(uri);
		context.startActivity(i);
		insertPlaceholderCall(context.getContentResolver(), number);
		return true;
	}

	public static String buildNumber(String gvNumber, String number, String pin) {
		StringBuilder dial = new StringBuilder();
		dial.append(gvNumber).append(PhoneNumberUtils.PAUSE).append(PhoneNumberUtils.PAUSE);
		if (pin != null && pin.length() > 0) {
			dial.append(pin).append(PhoneNumberUtils.PAUSE);
		}
		number = number.replaceAll("[^0-9+]", "");
		dial.append(2).append(PhoneNumberUtils.PAUSE).append(number).append("#").append("#");
		Log.d(TAG, "Number built: " + dial.toString());
		return dial.toString();
	}

	public boolean connect(String number) {
		if (!isLoggedIn()) {
			login();
			if (!isLoggedIn()) {
				m_error = "Could not log in!";
				return false;
			}
		}
		TelephonyManager mgr = (TelephonyManager) m_prefs.getContext().getSystemService(Context.TELEPHONY_SERVICE);
		String lineNumber = m_prefs.getString(PreferencesProvider.HOST_NUMBER, mgr.getLine1Number());
		if (lineNumber == null || lineNumber.length() == 0) {
			m_error = "No callback number available! Please set this in 'GV Settings'";
			return false;
		}
		HttpPost post = new HttpPost(BASE + "/voice/call/connect/");
		List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
		data.add(new BasicNameValuePair("_rnr_se", m_token));
		data.add(new BasicNameValuePair("forwardingNumber", lineNumber));
		data.add(new BasicNameValuePair("outgoingNumber", number));
		data.add(new BasicNameValuePair("remember", "0"));
		data.add(new BasicNameValuePair("subscriberNumber", "undefined"));
		try {
			post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));
			HttpResponse response = m_client.execute(post);
			String responseText = getJSON(response.getEntity());
			JSONObject json = (JSONObject) JSONValue.parse(responseText);
			boolean success = (Boolean) json.get("ok");
			if (!success) {
				m_error = responseText;
			}
			return success;
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		m_error = "Unknown exception!";
		return false;
	}

	public void setUsername(String username) {
		m_username = username;
		m_prefs.write(PreferencesProvider.USERNAME, m_username);
	}

	public void setPassword(String password) {
		m_password = password;
		m_prefs.write(PreferencesProvider.PASSWORD, m_password);
	}

	public static String getContent(HttpEntity entity) throws ClientProtocolException, IOException {
		s_builder.setLength(0);
		InputStream is = entity.getContent();
		BufferedReader buffer = new BufferedReader(new InputStreamReader(is));
		String line = null;
		while ((line = buffer.readLine()) != null) {
			s_builder.append(line).append("\n");
		}
		buffer.close();
		return s_builder.toString().trim();
	}

	/**
	 * Strip non-numbers from a phone number
	 * 
	 * @param number A number of any format
	 * @return a number of just digits
	 */
	public static String normalizeNumber(String number) {
		return number.trim().replaceAll("[^0-9+]", "");
	}

	public String getJSON(HttpEntity entity) throws ClientProtocolException, IOException {
		s_builder.setLength(0);
		InputStream is = entity.getContent();
		BufferedReader buffer = new BufferedReader(new InputStreamReader(is));
		String line = null;
		while ((line = buffer.readLine()) != null) {
			s_builder.append(line).append("\n");
			if (line.indexOf("</json>") != -1) {
				break;
			}
		}
		buffer.close();
		entity.consumeContent();
		return s_builder.toString().substring(s_builder.indexOf("{"), s_builder.lastIndexOf("}") + 1);
	}

	@SuppressWarnings("unchecked")
	public Map<String, Long> getUnreadCounts() {
		if (!isLoggedIn()) {
			login(m_username, m_password);
		}

		try {
			// go to an extremely high page so that it loads faster. Examine the
			// JSON feeds if you don't know what I mean by this.
			HttpGet get = new HttpGet(BASE + "/voice/inbox/recent?page=p1000");
			HttpResponse response = m_client.execute(get);
			String rsp = getJSON(response.getEntity());
			Object obj = JSONValue.parse(rsp);
			JSONObject msgs = (JSONObject) obj;

			Map<String, Long> unread = new HashMap<String, Long>();
			msgs = (JSONObject) msgs.get("unreadCounts");
			for (String key : (Set<String>) msgs.keySet()) {
				Object data = msgs.get(key);
				unread.put(key, (Long) data);
			}
			return unread;
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new HashMap<String, Long>();
	}

	protected String getJSONString(JSONObject json, String key) {
		return getJSONString(json, key, "");
	}

	protected String getJSONString(JSONObject json, String key, String defValue) {
		String value = (String) json.get(key);
		return value == null ? defValue : value;
	}

	public boolean isLoggedIn() {
		return m_token.length() > 0;
	}

	public String getUsername() {
		return m_username;
	}

	public String getGVNumber() {
		return m_gvNumber;
	}

	public String getToken() {
		return m_token;
	}

	public void setToken(String token) {
		m_token = token;
	}

	public String getError() {
		return m_error;
	}

	public DefaultHttpClient getHttpClient() {
		return m_client;
	}

	public boolean sendSMS(String to, String message) {
		return sendSMS(to, message, "undefined");
	}

	public boolean sendSMS(String to, String message, String threadId) {
		if (!isLoggedIn()) {
			login();
			if (!isLoggedIn()) {
				return false;
			}
		}
		String c = "1";
		if (threadId.equals("undefined")) {
			c = "undefined";
		}
		if (message.trim().length() == 0) {
			return false;
		}
		HttpPost post = new HttpPost(BASE + "/voice/m/sendsms");

		List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
		data.add(new BasicNameValuePair("_rnr_se", m_token));
		data.add(new BasicNameValuePair("smstext", message));
		data.add(new BasicNameValuePair("number", normalizeNumber(to)));
		data.add(new BasicNameValuePair("id", threadId));
		data.add(new BasicNameValuePair("c", c));

		try {
			post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));
			HttpResponse response = m_client.execute(post);
			HttpEntity entity = response.getEntity();
			String rsp = getContent(entity);
			return rsp.toLowerCase().indexOf("sent") != -1;
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean downloadVoicemail(Voicemail v) {
		if (v.isDownloaded()) {
			return true;
		}
		String url = String.format(MOBILE + "/playvoicemail?id=%s&auth=%s", v.getId(), m_auth);
		HttpGet get = new HttpGet(url);
		try {
			HttpResponse response = m_client.execute(get);
			HttpEntity entity = response.getEntity();
			InputStream in = entity.getContent();
			final String folder = VOICEMAIL_FOLDER;
			File f = new File(folder);
			if (!f.exists()) {
				f.mkdirs();
			}
			FileOutputStream out = new FileOutputStream(folder + v.getFilename());

			byte[] tmp = new byte[4096];
			int l;
			while ((l = in.read(tmp)) != -1) {
				out.write(tmp, 0, l);
			}
			out.flush();
			in.close();

			return true;
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean messageMarkRead(String id, boolean read) {
		HttpPost post = new HttpPost(BASE + "/voice/inbox/mark/");
		List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
		data.add(new BasicNameValuePair("messages", id));
		data.add(new BasicNameValuePair("read", read ? "1" : "0"));
		data.add(new BasicNameValuePair("_rnr_se", m_token));
		try {
			post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));
			HttpResponse response = m_client.execute(post);
			String rsp = getContent(response.getEntity());
			JSONObject val = (JSONObject) JSONValue.parse(rsp);
			if (val == null) {
				return true;
			}
			return (Boolean) val.get("ok");
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean messageBlockCaller(String id, boolean block) {
		HttpPost post = new HttpPost(BASE + "/voice/inbox/block/");
		List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
		data.add(new BasicNameValuePair("messages", id));
		data.add(new BasicNameValuePair("blocked", block ? "1" : "0"));
		data.add(new BasicNameValuePair("_rnr_se", m_token));
		try {
			post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));
			HttpResponse response = m_client.execute(post);
			String rsp = getContent(response.getEntity());
			JSONObject val = (JSONObject) JSONValue.parse(rsp);
			return (Boolean) val.get("ok");
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean deleteMessage(Message message) {
		return deleteMessageById(message.getId());
	}

	public boolean deleteMessageById(String id) {
		HttpPost post = new HttpPost(BASE + "/voice/inbox/deleteMessages");
		List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
		data.add(new BasicNameValuePair("_rnr_se", m_token));
		data.add(new BasicNameValuePair("messages", id));
		data.add(new BasicNameValuePair("trash", "1"));
		try {
			post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));
			HttpResponse response = m_client.execute(post);
			String rsp = getContent(response.getEntity());
			JSONObject val = (JSONObject) JSONValue.parse(rsp);
			return (Boolean) val.get("ok");
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public JSONObject getSettings() {
		if (!isLoggedIn()) {
			if (!login()) {
				return null;
			}
		}
		try {
			HttpGet get = new HttpGet(BASE + "/voice/settings/tab/phones");
			HttpResponse response = m_client.execute(get);
			String json = getJSON(response.getEntity());
			return (JSONObject) JSONValue.parse(json);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public boolean deletePhone(long phoneId) {
		if (!isLoggedIn()) {
			if (!login()) {
				return false;
			}
		}
		try {
			HttpPost post = new HttpPost(BASE + "/voice/settings/deleteForwarding");
			List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();
			data.add(new BasicNameValuePair("id", String.valueOf(phoneId)));
			data.add(new BasicNameValuePair("_rnr_se", m_token));
			post.setEntity(new UrlEncodedFormEntity(data));

			HttpResponse response = m_client.execute(post);
			return Boolean.parseBoolean(getContent(response.getEntity()));
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean verifyPhone(String number, long phoneId, int verificationCode) {
		if (!isLoggedIn()) {
			if (!login()) {
				return false;
			}
		}
		try {
			HttpPost post = new HttpPost(BASE + "/voice/call/verifyForwarding");
			List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();

			data.add(new BasicNameValuePair("_rnr_se", m_token));
			data.add(new BasicNameValuePair("code", String.valueOf(verificationCode)));
			data.add(new BasicNameValuePair("forwardingNumber", number));
			data.add(new BasicNameValuePair("phoneId", String.valueOf(phoneId)));
			data.add(new BasicNameValuePair("subscriberNumber", "undefined"));

			post.setEntity(new UrlEncodedFormEntity(data));

			HttpResponse response = m_client.execute(post);
			String rsp = getContent(response.getEntity());
			JSONObject val = (JSONObject) JSONValue.parse(rsp);
			return (Boolean) val.get("ok");
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public JSONObject savePhone(Phone phone) {
		if (!isLoggedIn()) {
			if (!login()) {
				return null;
			}
		}
		try {
			HttpPost post = new HttpPost(BASE + "/voice/settings/editForwarding");
			List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();

			data.add(new BasicNameValuePair("_rnr_se", m_token));
			data.add(new BasicNameValuePair("id", String.valueOf(phone.id)));
			data.add(new BasicNameValuePair("name", phone.name));
			data.add(new BasicNameValuePair("phoneNumber", phone.number));
			data.add(new BasicNameValuePair("policyBitmask", String.valueOf(phone.policy)));
			data.add(new BasicNameValuePair("smsEnabled", phone.sms ? "1" : "0"));
			data.add(new BasicNameValuePair("type", String.valueOf(phone.type)));
			if (!phone.verified) {
				data.add(new BasicNameValuePair("fromTimewd0", "9:00am"));
				data.add(new BasicNameValuePair("fromTimewe0", "9:00am"));
				data.add(new BasicNameValuePair("ringwd", "0"));
				data.add(new BasicNameValuePair("ringwe", "0"));
				data.add(new BasicNameValuePair("toTimewd0", "5:00pm"));
				data.add(new BasicNameValuePair("toTimewe0", "5:00pm"));
			}

			post.setEntity(new UrlEncodedFormEntity(data));

			HttpResponse response = m_client.execute(post);
			String rsp = getContent(response.getEntity());
			return (JSONObject) JSONValue.parse(rsp);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public boolean saveSettings(Map<String, String> map) {
		if (!isLoggedIn()) {
			if (!login()) {
				return false;
			}
		}
		try {
			HttpPost post = new HttpPost(BASE + "/voice/settings/editGeneralSettings");
			List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();

			data.add(new BasicNameValuePair("_rnr_se", m_token));
			for (String key : map.keySet()) {
				data.add(new BasicNameValuePair(key, map.get(key)));
			}

			post.setEntity(new UrlEncodedFormEntity(data));

			HttpResponse response = m_client.execute(post);
			String rsp = getContent(response.getEntity());
			JSONObject val = (JSONObject) JSONValue.parse(rsp);
			return (Boolean) val.get("ok");
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	public static void insertPlaceholderCall(ContentResolver contentResolver, String number) {
		ContentValues values = new ContentValues();
		values.put(CallLog.Calls.NUMBER, number);
		values.put(CallLog.Calls.DATE, System.currentTimeMillis());
		values.put(CallLog.Calls.DURATION, 0);
		values.put(CallLog.Calls.TYPE, CallLog.Calls.OUTGOING_TYPE);
		values.put(CallLog.Calls.NEW, 1);
		values.put(CallLog.Calls.CACHED_NAME, "");
		values.put(CallLog.Calls.CACHED_NUMBER_TYPE, 0);
		values.put(CallLog.Calls.CACHED_NUMBER_LABEL, "");
		Log.d(TAG, "Inserting call log placeholder for " + number);
		contentResolver.insert(CallLog.Calls.CONTENT_URI, values);
	}

	public Boolean cancelCall() {
		if (!isLoggedIn()) {
			if (!login()) {
				return false;
			}
		}
		try {
			HttpPost post = new HttpPost(BASE + "/voice/");
			List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();

			data.add(new BasicNameValuePair("_rnr_se", m_token));
			data.add(new BasicNameValuePair("cancelType", "C2C"));
			data.add(new BasicNameValuePair("forwardingNumber", "undefined"));
			data.add(new BasicNameValuePair("outgoingNumber", "undefined"));

			post.setEntity(new UrlEncodedFormEntity(data));

			HttpResponse response = m_client.execute(post);
			String rsp = getContent(response.getEntity());
			JSONObject val = (JSONObject) JSONValue.parse(rsp);
			return (Boolean) val.get("ok");
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	public Boolean setDoNotDisturb(boolean enabled) {
		if (!isLoggedIn()) {
			if (!login()) {
				return false;
			}
		}
		try {
			HttpPost post = new HttpPost(MOBILE + "/savednd");
			List<BasicNameValuePair> data = new ArrayList<BasicNameValuePair>();

			data.add(new BasicNameValuePair("_rnr_se", m_token));
			data.add(new BasicNameValuePair("doNotDisturb", enabled ? "1" : "0"));

			post.setEntity(new UrlEncodedFormEntity(data));

			m_client.execute(post);
			return true;
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}
}
