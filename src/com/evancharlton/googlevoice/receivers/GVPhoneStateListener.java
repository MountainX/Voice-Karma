package com.evancharlton.googlevoice.receivers;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.evancharlton.googlevoice.GoogleVoice;
import com.evancharlton.googlevoice.PreferencesProvider;

public class GVPhoneStateListener extends PhoneStateListener {
	private Context m_context;

	public GVPhoneStateListener(Context context) {
		m_context = context;
	}

	public void onCallStateChanged(int state, String incomingNumber) {
		NotificationManager notificationMgr = (NotificationManager) m_context.getSystemService(Activity.NOTIFICATION_SERVICE);
		switch (state) {
			case TelephonyManager.CALL_STATE_IDLE:
				if (notificationMgr != null) {
					notificationMgr.cancel(GoogleVoice.NOTIFICATION_CALL);
				}
				break;
			case TelephonyManager.CALL_STATE_RINGING:
				if (notificationMgr != null) {
					notificationMgr.cancel(GoogleVoice.NOTIFICATION_CALL);
				}
				PreferencesProvider prefs = PreferencesProvider.getInstance(m_context);
				String gv = prefs.getString(PreferencesProvider.GV_NUMBER, "").replaceAll("[^0-9]", "");
				String in = incomingNumber.replaceAll("[^0-9]", "");
				if (in.length() < 10 || gv.length() < 10) {
					// unknown number or something; just ignore it
					break;
				}
				boolean same = gv.endsWith(in) || in.endsWith(gv);
				if (same) {
					notificationMgr.notify(GoogleVoice.NOTIFICATION_CALL, OutgoingCallReceiver.buildOngoing(m_context, "Connected by Google Voice", "Connected by Google Voice", "You are connected through the Google Voice service"));
				}
				break;
		}
	}
}
