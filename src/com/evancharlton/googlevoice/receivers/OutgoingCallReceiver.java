package com.evancharlton.googlevoice.receivers;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;

import com.evancharlton.googlevoice.CancelCallback;
import com.evancharlton.googlevoice.GoogleVoice;
import com.evancharlton.googlevoice.PreferencesProvider;
import com.evancharlton.googlevoice.net.GVCommunicator;
import com.evancharlton.googlevoice.ui.SettingsView;

public class OutgoingCallReceiver extends BroadcastReceiver {
	@SuppressWarnings("unused")
	private static final String TAG = "GV_OCR";

	private Context m_context;

	private static CallTask m_task;

	@Override
	public void onReceive(Context context, Intent outgoing) {
		String number = outgoing.getStringExtra(Intent.EXTRA_PHONE_NUMBER).replaceAll("[^0-9]", "");
		if (number.length() < 10) {
			// don't do anything because Google Voice doesn't support numbers
			// less than 10 digits anyway. Also, it might be an emergency.
			return;
		}
		TelephonyManager tmgr = (TelephonyManager) context.getSystemService(Activity.TELEPHONY_SERVICE);
		String voicemailNumber = tmgr.getVoiceMailNumber();
		if (voicemailNumber != null && voicemailNumber.equals(number)) {
			// don't mess with voicemail numbers
			return;
		}
		PreferencesProvider prefs = PreferencesProvider.getInstance(context);
		String gvNumber = prefs.getString(PreferencesProvider.GV_NUMBER, "").replaceAll("[^0-9]", "");
		if (gvNumber.length() > 0 && (gvNumber.endsWith(number) || number.endsWith(gvNumber))) {
			// don't try and call our own GV number from GV
			return;
		}
		if (prefs.getBoolean(PreferencesProvider.ALWAYS_USE_GV, false)) {
			if (gvNumber.length() == 0) {
				// bail on the call
				showError("Call Failed!", "Error: Call failed!", "No GV number stored, so can not make the call through Google Voice.", settingsIntent());
				setResultData(null);
			} else {
				GVCommunicator.insertPlaceholderCall(context.getContentResolver(), number);
				if (prefs.getBoolean(PreferencesProvider.USE_CALLBACK, true)) {
					setResultData(null);
					m_context = context;
					m_task = new CallTask();
					m_task.execute(number);
				} else {
					String pin = "";
					if (prefs.getBoolean(PreferencesProvider.PIN_REQUIRED, true)) {
						pin = prefs.getString(PreferencesProvider.PIN_NUMBER, "");
					}
					setResultData(GVCommunicator.buildNumber(gvNumber, number, pin));
				}
			}
		}
	}

	private void showOngoing(String tickerText, String title, String description) {
		NotificationManager mgr = (NotificationManager) m_context.getSystemService(Activity.NOTIFICATION_SERVICE);
		if (mgr != null) {
			mgr.notify(GoogleVoice.NOTIFICATION_CALL, OutgoingCallReceiver.buildOngoing(m_context, tickerText, title, description));
		}
	}

	public static Notification buildOngoing(Context context, String tickerText, String title, String description) {
		Notification calling = new Notification(com.evancharlton.googlevoice.R.drawable.contacticon, tickerText, System.currentTimeMillis());
		Intent intent = new Intent(context, CancelCallback.class);
		PendingIntent settings = PendingIntent.getActivity(context, 0, intent, 0);
		calling.flags = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
		calling.setLatestEventInfo(context, title, description, settings);
		calling.vibrate = new long[] {
				250,
				250
		};
		return calling;
	}

	private void clear(int id) {
		NotificationManager mgr = (NotificationManager) m_context.getSystemService(Activity.NOTIFICATION_SERVICE);
		if (mgr != null) {
			mgr.cancel(id);
		}
	}

	private void showError(String tickerText, String title, String description, Intent intent) {
		Notification callFailed = new Notification(com.evancharlton.googlevoice.R.drawable.statusicon_error, tickerText, System.currentTimeMillis());
		PendingIntent settings = PendingIntent.getActivity(m_context, 0, intent, 0);
		callFailed.flags = Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_AUTO_CANCEL;
		callFailed.ledARGB = 0xFFFF0000;
		callFailed.ledOffMS = 100;
		callFailed.ledOnMS = 100;
		callFailed.vibrate = new long[] {
				250,
				250,
				250,
				250,
				250,
				250
		};
		callFailed.setLatestEventInfo(m_context, title, description, settings);
		NotificationManager mgr = (NotificationManager) m_context.getSystemService(Activity.NOTIFICATION_SERVICE);
		if (mgr != null) {
			mgr.notify(GoogleVoice.NOTIFICATION_CALL, callFailed);
		}
	}

	private Intent settingsIntent() {
		return i(SettingsView.class);
	}

	private Intent i(Class<?> cls) {
		return new Intent(m_context, cls);
	}

	public static void cancel() {
		if (m_task != null) {
			m_task.cancel(true);
		}
	}

	private class CallTask extends AsyncTask<String, Integer, Integer> {
		private static final int PROGRESS_LOGGING_IN = 1;
		private static final int PROGRESS_LOGIN_FAILED = 2;
		private static final int PROGRESS_REGISTERING = 3;
		private static final int PROGRESS_WAITING = 4;
		private static final int PROGRESS_CALL_FAILED = 5;
		private static final int PROGRESS_CANCELLED = 6;

		private boolean m_isCancelled = false;

		@Override
		protected void onPreExecute() {
			m_isCancelled = false;
		}

		@Override
		protected void onCancelled() {
			m_isCancelled = true;
		}

		@Override
		protected Integer doInBackground(String... params) {
			GVCommunicator comm = GVCommunicator.getInstance(m_context);
			// first we have to log in
			if (!comm.isLoggedIn()) {
				publishProgress(PROGRESS_LOGGING_IN);
				if (!comm.login()) {
					return PROGRESS_LOGIN_FAILED;
				}
			}
			if (!m_isCancelled) {
				// and then we have to try and register the call
				publishProgress(PROGRESS_REGISTERING);
				if (!comm.connect(params[0])) {
					// damn it, something failed. Abort.
					return PROGRESS_CALL_FAILED;
				}
				return PROGRESS_WAITING;
			}
			return PROGRESS_CANCELLED;
		}

		@Override
		protected void onProgressUpdate(Integer... updates) {
			show(updates[0]);
		}

		private void show(int which) {
			clear(GoogleVoice.NOTIFICATION_CALL);
			switch (which) {
				case PROGRESS_LOGGING_IN:
					showOngoing("Logging in...", "Logging in...", "Logging in to Google Voice");
					break;
				case PROGRESS_LOGIN_FAILED:
					showError("Login failed!", "Login failed!", "You could not be logged into Google Voice!", settingsIntent());
					break;
				case PROGRESS_REGISTERING:
					showOngoing("Placing call...", "Placing call...", "Select to cancel call");
					break;
				case PROGRESS_WAITING:
					showOngoing("Waiting for call...", "Waiting for call...", "Select to cancel call");
					break;
				case PROGRESS_CALL_FAILED:
					showError("Call failed!", "Call failed!", "The call could not be completed!", i(GoogleVoice.class));
					break;
			}
		}

		@Override
		protected void onPostExecute(Integer result) {
			show(result);
		}
	}
}