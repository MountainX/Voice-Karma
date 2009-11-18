package com.evancharlton.googlevoice.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.evancharlton.googlevoice.DropDownAdapter;
import com.evancharlton.googlevoice.GoogleVoice;
import com.evancharlton.googlevoice.PreferencesProvider;
import com.evancharlton.googlevoice.R;
import com.evancharlton.googlevoice.dialogs.HelpDialog;
import com.evancharlton.googlevoice.net.GVCommunicator;
import com.evancharlton.googlevoice.objects.Message;

public class CallLogActivity extends Activity {
	private static final String CACHE = "cache";
	private static final String PROGRESS_MSG = "progress message";
	private static final String PROGRESS_VIS = "progress visiblity";

	private static final int POS_PLACED = 0;
	private static final int POS_RECEIVED = 1;
	private static final int POS_MISSED = 2;

	private static final int DIALOG_LOGIN_FAILED = 1;
	private static final int DIALOG_DELETE = 2;

	private static final int MENU_NEXT_PAGE = 100;
	private static final int MENU_REFRESH = 101;

	private CallLogHandler m_callLogHandler;
	private GoogleVoice m_parent = null;

	private HashMap<String, ArrayList<Message>> m_cache = null;
	private ArrayList<Map<String, ?>> m_adapterData = new ArrayList<Map<String, ?>>();
	private ArrayList<Message> m_messages = new ArrayList<Message>();
	private SimpleAdapter m_adapter;

	private int m_page = 1;
	private Message m_selectedMessage = null;
	private LoadLogTask m_task;

	private Spinner m_typeSpinner;
	private ListView m_list;
	private LinearLayout m_progress;
	private TextView m_progressMsg;

	@SuppressWarnings("unchecked")
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		m_parent = (GoogleVoice) getParent();

		setContentView(R.layout.call_log);

		m_typeSpinner = (Spinner) findViewById(R.id.call_log_type);
		m_progress = (LinearLayout) findViewById(R.id.progress);
		m_progressMsg = (TextView) findViewById(R.id.progress_message);
		m_list = (ListView) findViewById(android.R.id.list);

		m_messages = new ArrayList<Message>();
		m_adapterData = new ArrayList<Map<String, ?>>();
		m_cache = new HashMap<String, ArrayList<Message>>();

		String[] from = new String[] {
				Message.FIELD_CONTACT,
				Message.FIELD_NUMBER,
				Message.FIELD_DISPLAY_DATE,
				Message.FIELD_IS_READ
		};

		int[] to = new int[] {
				R.id.contact,
				R.id.callback_number,
				R.id.date,
				R.id.read
		};

		m_adapter = new SimpleAdapter(this, m_adapterData, R.layout.voicemail_row, from, to);
		m_list.setAdapter(m_adapter);
		m_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				m_callLogHandler = new CallLogHandler(m_messages.get(position));

				ArrayAdapter<String> arr = new ArrayAdapter<String>(CallLogActivity.this, android.R.layout.select_dialog_item);
				m_callLogHandler.buildOptions(arr);

				final DropDownAdapter adapter = new DropDownAdapter(arr);

				AlertDialog.Builder builder = new AlertDialog.Builder(CallLogActivity.this);
				builder.setTitle("Call Log");
				builder.setSingleChoiceItems(adapter, 0, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						m_callLogHandler.handle(which);
						dialog.dismiss();
					}
				}).show();
			}
		});

		// rebuild the state of the application
		Object[] saved = (Object[]) getLastNonConfigurationInstance();
		if (saved != null) {
			m_task = (LoadLogTask) saved[0];
			m_cache = (HashMap<String, ArrayList<Message>>) saved[1];
			m_progress.setVisibility((Integer) saved[2]);
			m_progressMsg.setText((CharSequence) saved[3]);
			m_messages = (ArrayList<Message>) saved[4];
		}

		if (m_task == null) {
			m_task = new LoadLogTask();
		}
		m_task.activity = this;

		if (savedInstanceState != null) {
			m_cache = (HashMap<String, ArrayList<Message>>) savedInstanceState.getSerializable(CACHE);
			m_progress.setVisibility(savedInstanceState.getInt(PROGRESS_VIS));
			m_progressMsg.setText(savedInstanceState.getString(PROGRESS_MSG));
		}

		final PreferencesProvider prefs = PreferencesProvider.getInstance(this);

		final int savedPosition = prefs.getInt(PreferencesProvider.LAST_CALL_LOG, 0);
		ArrayList<Message> cached = m_messages;
		if (cached == null) {
			cached = m_cache.get(getType(savedPosition));
		}
		if (cached != null) {
			m_adapterData.clear();
			for (Message msg : cached) {
				m_adapterData.add(buildMap(msg));
				m_adapter.notifyDataSetChanged();
			}
		}
		m_typeSpinner.setSelection(savedPosition);

		if (m_task.getStatus() == AsyncTask.Status.PENDING) {
			load(savedPosition);
		}

		m_typeSpinner.postDelayed(new Runnable() {
			public void run() {
				m_typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
					@Override
					public void onItemSelected(AdapterView<?> list, View view, int position, long id) {
						m_page = 1;
						m_adapterData.clear();
						m_messages.clear();
						m_adapter.notifyDataSetInvalidated();
						load(position);
						prefs.write(PreferencesProvider.LAST_CALL_LOG, position);
					}

					@Override
					public void onNothingSelected(AdapterView<?> arg0) {
					}
				});
			}
		}, 500);
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return new Object[] {
				m_task,
				m_cache,
				m_progress.getVisibility(),
				m_progressMsg.getText().toString(),
				m_messages
		};
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(PROGRESS_VIS, m_progress.getVisibility());
		outState.putString(PROGRESS_MSG, m_progressMsg.getText().toString());
		outState.putSerializable(CACHE, m_cache);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, MENU_REFRESH, Menu.NONE, R.string.reload).setShortcut('3', 'r').setIcon(R.drawable.ic_menu_refresh);
		return super.onCreateOptionsMenu(menu);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_NEXT_PAGE:
				load(m_typeSpinner.getSelectedItemPosition());
				return true;
			case MENU_REFRESH:
				m_cache = new HashMap<String, ArrayList<Message>>();
				m_messages = new ArrayList<Message>();
				m_adapterData.clear();
				m_adapter.notifyDataSetChanged();
				m_page = 1;
				load(m_typeSpinner.getSelectedItemPosition());
				return true;
		}
		return m_parent.onOptionsItemSelected(item);
	}

	private String getType(final int position) {
		switch (position) {
			case POS_PLACED:
				return "placed";
			case POS_RECEIVED:
				return "received";
			case POS_MISSED:
				return "missed";
		}
		return null;
	}

	public void load(final int position) {
		String query = getType(position);
		if (query != null) {
			ArrayList<Message> cached = m_cache.get(query);
			if (cached != null && cached.size() > 0) {
				m_adapterData.clear();
				for (Message msg : cached) {
					m_adapterData.add(buildMap(msg));
				}
				m_adapter.notifyDataSetInvalidated();
			} else {
				// if it's running, then kill it
				if (m_task.getStatus() == AsyncTask.Status.RUNNING) {
					m_task.cancel(true);
					m_task = null;
				}
				// recreate the task if need be
				if (m_task == null || m_task.getStatus() == AsyncTask.Status.FINISHED) {
					m_task = new LoadLogTask();
					m_task.activity = this;
				}
				// finally run it!
				m_task.execute(query);
			}
		}
	}

	private Map<String, ?> buildMap(Message msg) {
		Map<String, String> msgMap = new HashMap<String, String>();
		msgMap.put(Message.FIELD_CONTACT, msg.getContactName());
		msgMap.put(Message.FIELD_NUMBER, msg.getDisplayNumber());
		msgMap.put(Message.FIELD_DISPLAY_DATE, msg.getDisplayDate());
		msgMap.put(Message.FIELD_IS_READ, msg.getRead());
		return msgMap;
	}

	public Dialog onCreateDialog(int which) {
		switch (which) {
			case HelpDialog.DIALOG_ID:
				return HelpDialog.create(this, R.string.help, R.string.help_contents);
			case DIALOG_LOGIN_FAILED:
				return new AlertDialog.Builder(this).setTitle(R.string.login_failed_title).setMessage(R.string.login_failed_message).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_LOGIN_FAILED);
					}
				}).create();
			case DIALOG_DELETE:
				return new AlertDialog.Builder(this).setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_DELETE);
						new DeleteMessageTask().execute(m_selectedMessage);
					}
				}).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_DELETE);
					}
				}).setTitle(R.string.delete_call_log_confirm).setMessage(R.string.delete_call_log_confirm_msg).create();
		}
		return null;
	}

	private class DeleteMessageTask extends AsyncTask<Message, Integer, Boolean> {
		@Override
		protected Boolean doInBackground(Message... params) {
			GVCommunicator comm = GVCommunicator.getInstance(CallLogActivity.this);
			return comm.deleteMessageById(params[0].getId());
		}

		@Override
		protected void onPostExecute(Boolean result) {
			int index = m_messages.indexOf(m_selectedMessage);
			synchronized (m_adapterData) {
				m_adapterData.remove(index);
			}
			m_selectedMessage = null;
			m_adapter.notifyDataSetChanged();
			synchronized (m_messages) {
				m_messages.remove(index);
			}
			// invalidate the cache
			m_cache.put(getType(m_typeSpinner.getSelectedItemPosition()), null);
		}
	}

	private class CallLogHandler {
		private ArrayList<MessageAction> m_actions;

		public CallLogHandler(Message msg) {
			m_selectedMessage = msg;
		}

		public void handle(int which) {
			m_actions.get(which).execute();
		}

		public void buildOptions(ArrayAdapter<String> arr) {
			m_actions = new ArrayList<MessageAction>();
			if (!m_selectedMessage.isPrivateNumber()) {
				m_actions.add(new MessageAction("Return Call") {
					public void execute() {
						m_parent.call(m_selectedMessage.getPhoneNumber());
					}
				});

				m_actions.add(new MessageAction("Reply via SMS") {
					public void execute() {
						Intent i = new Intent(Intent.ACTION_SENDTO);
						Uri uri = Uri.parse("sms:" + m_selectedMessage.getPhoneNumber());
						i.setData(uri);
						startActivity(i);
					}
				});
			}

			m_actions.add(new MessageAction("Delete") {
				public void execute() {
					showDialog(DIALOG_DELETE);
				}
			});

			for (MessageAction action : m_actions) {
				arr.add(action.toString());
			}
		}
	}

	private class MessageAction {
		private String m_action = "";

		public MessageAction(String action) {
			m_action = action;
		}

		public void execute() {
		}

		public String toString() {
			return m_action;
		}
	}

	private static class LoadLogTask extends AsyncTask<String, Message, Boolean> {
		public CallLogActivity activity;

		private boolean m_cancelled = false;
		private String type;
		private GVCommunicator m_comm;

		@Override
		protected void onPreExecute() {
			m_cancelled = false;
			activity.m_progress.setVisibility(View.VISIBLE);
			activity.m_progressMsg.setText(R.string.logging_in);
		}

		@Override
		protected void onCancelled() {
			m_cancelled = true;
		}

		@Override
		protected Boolean doInBackground(String... params) {
			type = params[0];
			activity.m_messages = new ArrayList<Message>();
			m_comm = GVCommunicator.getInstance(activity);
			activity.m_page = 1;
			return load(activity.m_page);
		}

		private Boolean load(int page) {
			activity.m_page = page;
			if (page > 20) {
				return true;
			}
			if (!m_comm.isLoggedIn()) {
				m_comm.login();
				if (!m_comm.isLoggedIn()) {
					return false;
				}
			}
			DefaultHttpClient client = m_comm.getHttpClient();
			HttpGet get = new HttpGet(GVCommunicator.BASE + "/voice/m/i/" + type + "?p=" + String.valueOf(page));
			try {
				HttpResponse response = client.execute(get);
				String data = GVCommunicator.getContent(response.getEntity());

				Pattern log = Pattern.compile("<div id=\"([^\"]+)\"[^>]+>\\s*<div>\\s*<span>\\s*<img[^>]+>\\s*</span>\\s*<[span]+[^>]*>([^<]+)</[span]+>\\s*<span[^>]+>([^<]+)</span>\\s*<a[^?]+\\?number=([^\"]+)\">");
				Matcher m = log.matcher(data);
				while (m.find()) {
					Message msg = new Message(m_comm);
					msg.setId(m.group(1));
					msg.setContactName(m.group(2));
					msg.setDisplayDate(m.group(3));
					msg.setPhoneNumber(m.group(4));

					if (m_cancelled) {
						return true;
					}
					publishProgress(msg);
				}
				if (data.indexOf("/voice/m/i/" + type + "?p=" + String.valueOf(page + 1)) >= 0) {
					load(page + 1);
				}
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return true;
		}

		@Override
		protected void onProgressUpdate(Message... update) {
			activity.m_progressMsg.setText(R.string.loading_log);
			synchronized (activity.m_messages) {
				activity.m_messages.add(update[0]);
			}
			synchronized (activity.m_adapterData) {
				activity.m_adapterData.add(activity.buildMap(update[0]));
			}
			activity.m_adapter.notifyDataSetChanged();
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void onPostExecute(Boolean loaded) {
			activity.m_progress.setVisibility(View.GONE);
			if (!loaded) {
				activity.showDialog(DIALOG_LOGIN_FAILED);
			} else {
				activity.m_cache.put(type, (ArrayList<Message>) activity.m_messages.clone());
			}
		}
	}
}
