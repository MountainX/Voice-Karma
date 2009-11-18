package com.evancharlton.googlevoice.ui;

import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.evancharlton.googlevoice.GoogleVoice;
import com.evancharlton.googlevoice.PreferencesProvider;
import com.evancharlton.googlevoice.R;
import com.evancharlton.googlevoice.net.GVCommunicator;
import com.evancharlton.googlevoice.services.UnreadService;

public class SettingsView extends PreferenceActivity {
	private static final int MENU_HELP = 2;

	private static final int DIALOG_DO_NOT_DISTURB = 1;
	private static final int DIALOG_DO_NOT_DISTURB_ERROR = 2;

	private DoNotDisturbTask m_doNotDisturbTask;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		NotificationManager notificationMgr = (NotificationManager) this.getSystemService(Activity.NOTIFICATION_SERVICE);
		if (notificationMgr != null) {
			notificationMgr.cancel(GoogleVoice.NOTIFICATION_CALL);
		}

		addPreferencesFromResource(R.layout.settings);

		CheckBoxPreference dnd = (CheckBoxPreference) findPreference(PreferencesProvider.DO_NOT_DISTURB);
		dnd.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				m_doNotDisturbTask = new DoNotDisturbTask();
				m_doNotDisturbTask.activity = SettingsView.this;
				m_doNotDisturbTask.execute((Boolean) newValue);
				return true;
			}
		});
	}

	@Override
	protected void onPause() {
		super.onPause();
		UnreadService.actionCancel(this);
		UnreadService.actionReschedule(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, MENU_HELP, Menu.NONE, R.string.help).setIcon(android.R.drawable.ic_menu_help);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		switch (id) {
			case MENU_HELP:
				Intent i = new Intent(Intent.ACTION_VIEW);
				i.setData(Uri.parse("http://docs.evancharlton.com/docs/GV/Settings"));
				startActivity(Intent.createChooser(i, "Web browser"));
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected Dialog onCreateDialog(int which) {
		switch (which) {
			case DIALOG_DO_NOT_DISTURB:
				ProgressDialog dnd = new ProgressDialog(this);
				dnd.setMessage(getString(R.string.settings_do_not_disturb_updating));
				dnd.setIndeterminate(true);
				dnd.setCancelable(false);
				return dnd;
		}
		return null;
	}

	private static class DoNotDisturbTask extends AsyncTask<Boolean, Integer, Boolean> {
		public SettingsView activity;

		@Override
		protected void onPreExecute() {
			activity.showDialog(DIALOG_DO_NOT_DISTURB);
		}

		@Override
		protected Boolean doInBackground(Boolean... params) {
			return GVCommunicator.getInstance(activity).setDoNotDisturb(params[0]);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			activity.removeDialog(DIALOG_DO_NOT_DISTURB);
			if (!result) {
				activity.showDialog(DIALOG_DO_NOT_DISTURB_ERROR);
			}
		}
	}
}
