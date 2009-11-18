package com.evancharlton.googlevoice.receivers;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.provider.CallLog;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.evancharlton.googlevoice.PreferencesProvider;

public class PhoneStateReceiver extends BroadcastReceiver {
	private static final String TAG = "GV_PSR";

	private Context m_context;

	private class CallLogObserver extends ContentObserver {
		public CallLogObserver(Handler handler) {
			super(handler);
		}

		public void onChange(boolean selfChange) {
			if (!selfChange) {
				// modify the call log to remove the GV entry
				String[] projection = new String[] {
						CallLog.Calls._ID,
						CallLog.Calls.NUMBER,
						CallLog.Calls.DATE,
						CallLog.Calls.DURATION,
						CallLog.Calls.TYPE,
						CallLog.Calls.NEW,
						CallLog.Calls.CACHED_NAME,
						CallLog.Calls.CACHED_NUMBER_LABEL,
						CallLog.Calls.CACHED_NUMBER_TYPE
				};
				Cursor c = m_context.getContentResolver().query(CallLog.Calls.CONTENT_URI, projection, null, null, CallLog.Calls.DATE + " DESC");
				if (c.getCount() >= 2) {
					c.moveToFirst();
					String realId = c.getString(c.getColumnIndex(CallLog.Calls._ID));
					PreferencesProvider prefs = PreferencesProvider.getInstance(m_context);
					String gvNumber = prefs.getString(PreferencesProvider.GV_NUMBER, "");
					String num = c.getString(c.getColumnIndex(CallLog.Calls.NUMBER));
					if (gvNumber.length() > 0 && !"-1".equals(num) && (num.endsWith(gvNumber) || gvNumber.endsWith(num))) {
						Log.d(TAG, "Modifying call log");
						c.moveToNext();
						long fakeId = c.getLong(c.getColumnIndex(CallLog.Calls._ID));
						String number = c.getString(c.getColumnIndex(CallLog.Calls.NUMBER));
						ContentValues changes = new ContentValues();
						changes.put(CallLog.Calls.NUMBER, number);
						changes.put(CallLog.Calls.TYPE, CallLog.Calls.OUTGOING_TYPE);
						c.close();
						m_context.getContentResolver().update(CallLog.Calls.CONTENT_URI, changes, CallLog.Calls._ID + " = ?", new String[] {
							realId
						});
						m_context.getContentResolver().delete(CallLog.Calls.CONTENT_URI, CallLog.Calls._ID + " = ?", new String[] {
							String.valueOf(fakeId)
						});
					}
					c.close();
				}

				m_context.getContentResolver().unregisterContentObserver(this);
			}
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		m_context = context;
		m_context.getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI, true, new CallLogObserver(new Handler()));
		GVPhoneStateListener phoneListener = new GVPhoneStateListener(context);
		TelephonyManager telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		telephony.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE);
	}
}
