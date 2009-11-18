package com.evancharlton.googlevoice.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.evancharlton.googlevoice.services.UnreadService;

public class BootReceiver extends BroadcastReceiver {
	private static final String TAG = "GV_BR";

	@Override
	public void onReceive(Context context, Intent intent) {
		if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
			Log.d(TAG, "Boot received, starting up unread checker");
			UnreadService.actionReschedule(context);
		} else if (Intent.ACTION_DEVICE_STORAGE_LOW.equals(intent.getAction())) {
			Log.d(TAG, "Low storage, shutting down unread service");
			UnreadService.actionCancel(context);
		} else if (Intent.ACTION_DEVICE_STORAGE_OK.equals(intent.getAction())) {
			Log.d(TAG, "Storage OK, starting up unread service again");
			UnreadService.actionReschedule(context);
		}
	}
}
