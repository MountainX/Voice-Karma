package com.evancharlton.googlevoice;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;

import com.evancharlton.googlevoice.net.GVCommunicator;
import com.evancharlton.googlevoice.receivers.OutgoingCallReceiver;

public class CancelCallback extends Activity {
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		OutgoingCallReceiver.cancel();
		new CancelCallTask().execute();
	}

	private class CancelCallTask extends AsyncTask<String, Integer, Boolean> {
		@Override
		protected Boolean doInBackground(String... params) {
			return GVCommunicator.getInstance(CancelCallback.this).cancelCall();
		}

		@Override
		protected void onPostExecute(Boolean result) {
			finish();
		}
	}
}
