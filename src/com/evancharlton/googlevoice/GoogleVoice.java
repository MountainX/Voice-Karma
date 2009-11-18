package com.evancharlton.googlevoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;

import com.evancharlton.googlevoice.dialogs.HelpDialog;
import com.evancharlton.googlevoice.net.GVCommunicator;
import com.evancharlton.googlevoice.services.UnreadService;
import com.evancharlton.googlevoice.ui.CallLogActivity;
import com.evancharlton.googlevoice.ui.ContactsView;
import com.evancharlton.googlevoice.ui.DialerView;
import com.evancharlton.googlevoice.ui.SMSThreads;
import com.evancharlton.googlevoice.ui.SettingsView;
import com.evancharlton.googlevoice.ui.VoicemailView;
import com.evancharlton.googlevoice.ui.setup.WizardSplash;

public class GoogleVoice extends TabActivity {
	public static final int NOTIFICATION_CALL = 0xF00DF00D;
	public static final int NOTIFICATION_VOICEMAIL = 0xDEADBEEF;
	public static final int NOTIFICATION_SMS = 0xFACECA2D;

	private static final String CURRENT_TAB = "current_tab";

	private static final int MENU_SETTINGS = Menu.FIRST;
	private static final int MENU_SMS = Menu.FIRST + 1;

	private static final String TAG_DIALER = "dialer";
	private static final String TAG_CONTACTS = "contacts";
	private static final String TAG_VOICEMAIL = "voicemail";
	private static final String TAG_CALL_LOG = "call_log";

	private static final int DIALOG_LOGGING_IN = 1;
	private static final int DIALOG_REGISTERING = 2;
	private static final int DIALOG_WAITING = 3;
	private static final int DIALOG_LOGIN_FAILED = 4;
	private static final int DIALOG_CALL_FAILED = 5;

	private TabHost m_tabHost;
	private PreferencesProvider m_prefs;

	private CallTask m_task;
	private int m_lastDialog = -1;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		NotificationManager notificationMgr = (NotificationManager) this.getSystemService(Activity.NOTIFICATION_SERVICE);
		if (notificationMgr != null) {
			notificationMgr.cancel(NOTIFICATION_CALL);
		}

		UnreadService.actionReschedule(this);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		m_prefs = PreferencesProvider.getInstance(this);

		setContentView(R.layout.main);

		m_tabHost = getTabHost();

		addDialerTab();
		addContactsTab();
		addVoicemailTab();
		addCallLogTab();

		if (savedInstanceState != null) {
			m_tabHost.setCurrentTab(savedInstanceState.getInt(CURRENT_TAB, 0));
		}

		// see if we're supposed to be calling someone right away
		Intent i = getIntent();
		if (i != null) {
			String action = i.getAction();
			Uri uri = i.getData();
			if (Intent.ACTION_CALL.equals(action)) {
				// call the person
				String scheme = uri.getScheme();
				if (scheme.equalsIgnoreCase("gv")) {
					String number = uri.getSchemeSpecificPart();
					call(number);
				}
			}
		}

		m_task = (CallTask) getLastNonConfigurationInstance();
		if (m_task != null) {
			m_task.activity = this;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		checkSettings();
	}

	@Override
	protected void onPause() {
		super.onPause();
		clearLastDialog();
	}

	private void checkSettings() {
		PreferencesProvider prefs = PreferencesProvider.getInstance(this);
		if (!prefs.getBoolean(PreferencesProvider.SETUP_COMPLETED, false)) {
			startActivity(new Intent(this, WizardSplash.class));
			finish();
		}
	}

	protected void onSaveInstanceState(Bundle outState) {
		outState.putInt(CURRENT_TAB, m_tabHost.getCurrentTab());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, MENU_SETTINGS, Menu.NONE, R.string.settings).setShortcut('1', 's').setIcon(android.R.drawable.ic_menu_preferences);
		menu.add(Menu.NONE, MENU_SMS, Menu.NONE, "SMS").setIcon(android.R.drawable.ic_menu_send);
		HelpDialog.injectHelp(menu, 'h');
		return super.onCreateOptionsMenu(menu);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case HelpDialog.MENU_HELP:
				showDialog(HelpDialog.DIALOG_ID);
				return true;
			case MENU_SETTINGS:
				goToSettings();
				return true;
			case MENU_SMS:
				Intent i = new Intent(this, SMSThreads.class);
				startActivity(i);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void addDialerTab() {
		Intent i = getIntent();
		String action = "";
		if (i != null) {
			action = i.getAction();
			if (Intent.ACTION_VIEW.equals(action) == false && Intent.ACTION_VIEW.equals(action) == false) {
				i = new Intent();
			}
		}
		i.setClass(GoogleVoice.this, DialerView.class);

		TabSpec spec = m_tabHost.newTabSpec(TAG_DIALER);
		spec.setIndicator(getString(R.string.dialer), getResources().getDrawable(R.drawable.ic_tab_dialer));
		spec.setContent(i);
		m_tabHost.addTab(spec);
	}

	private void addContactsTab() {
		Intent i = new Intent(GoogleVoice.this, ContactsView.class);

		TabSpec spec = m_tabHost.newTabSpec(TAG_CONTACTS);
		spec.setIndicator(getString(R.string.contacts), getResources().getDrawable(R.drawable.ic_tab_contacts));
		spec.setContent(i);
		m_tabHost.addTab(spec);
	}

	private void addVoicemailTab() {
		Intent i = new Intent(GoogleVoice.this, VoicemailView.class);

		TabSpec spec = m_tabHost.newTabSpec(TAG_VOICEMAIL);
		spec.setIndicator(getString(R.string.voicemail), getResources().getDrawable(R.drawable.ic_dialer_voicemail_black));
		spec.setContent(i);
		m_tabHost.addTab(spec);
	}

	private void addCallLogTab() {
		Intent i = new Intent(GoogleVoice.this, CallLogActivity.class);

		TabSpec spec = m_tabHost.newTabSpec(TAG_CALL_LOG);
		spec.setIndicator(getString(R.string.call_log), getResources().getDrawable(R.drawable.ic_tab_recent));
		spec.setContent(i);
		m_tabHost.addTab(spec);
	}

	public void goToSettings() {
		Intent i = new Intent();
		i.setClass(GoogleVoice.this, SettingsView.class);
		startActivity(i);
	}

	public void call(final String number) {
		if (m_prefs.getBoolean(PreferencesProvider.ALWAYS_USE_GV, false)) {
			Intent i = new Intent(Intent.ACTION_CALL);
			i.setData(Uri.parse("tel:" + number));
			startActivity(i);
		} else {
			if (m_prefs.getBoolean(PreferencesProvider.USE_CALLBACK, true)) {
				m_task = new CallTask();
				m_task.activity = this;
				m_task.execute(number);
			} else {
				GVCommunicator.call(this, number);
			}
		}
	}

	@Override
	protected Dialog onCreateDialog(int which) {
		m_lastDialog = which;
		switch (which) {
			case HelpDialog.DIALOG_ID:
				return HelpDialog.create(this, R.string.help, R.string.help_contents);
			case DIALOG_CALL_FAILED:
				return new AlertDialog.Builder(this).setTitle("Call failed!").setMessage(String.format("Your call could not be completed! Google Voice said:\n%s", GVCommunicator.getInstance(GoogleVoice.this).getError())).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_CALL_FAILED);
					}
				}).create();
			case DIALOG_LOGIN_FAILED:
				return new AlertDialog.Builder(this).setTitle(R.string.login_failed_title).setMessage(R.string.login_failed_message).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_LOGIN_FAILED);
					}
				}).create();
			case DIALOG_LOGGING_IN:
				ProgressDialog loggingIn = new ProgressDialog(this);
				loggingIn.setIndeterminate(true);
				loggingIn.setCancelable(true);
				loggingIn.setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						m_task.cancel(true);
					}
				});
				loggingIn.setTitle(R.string.logging_in);
				loggingIn.setMessage(getString(R.string.logging_in_msg));
				return loggingIn;
			case DIALOG_REGISTERING:
				ProgressDialog registering = new ProgressDialog(this);
				registering.setIndeterminate(true);
				registering.setCancelable(false);
				registering.setTitle("Registering call");
				registering.setMessage("Registering the call with Google Voice; please wait a moment...");
				return registering;
			case DIALOG_WAITING:
				ProgressDialog waiting = new ProgressDialog(this);
				waiting.setIndeterminate(true);
				waiting.setCancelable(true);
				waiting.setTitle("Waiting for call");
				waiting.setMessage("Waiting for Google Voice to call back. Please be patient, this might take a minute.");
				return waiting;
		}
		m_lastDialog = -1;
		return null;
	}

	private void clearLastDialog() {
		if (m_lastDialog != -1) {
			removeDialog(m_lastDialog);
			m_lastDialog = -1;
		}
	}

	private static class CallTask extends AsyncTask<String, Integer, Integer> {
		public GoogleVoice activity;
		private boolean m_cancelled = false;

		@Override
		protected void onPreExecute() {
			m_cancelled = false;
		}

		@Override
		protected void onCancelled() {
			m_cancelled = true;
		}

		@Override
		protected Integer doInBackground(String... params) {
			GVCommunicator comm = GVCommunicator.getInstance(activity);
			// first we have to log in
			if (!comm.isLoggedIn()) {
				publishProgress(DIALOG_LOGGING_IN);
				if (!comm.login()) {
					return DIALOG_LOGIN_FAILED;
				}
			}
			if (m_cancelled) {
				return DIALOG_LOGGING_IN;
			}
			// and then we have to try and register the call
			publishProgress(DIALOG_REGISTERING);
			if (!comm.connect(params[0])) {
				// damn it, something failed. Abort.
				return DIALOG_CALL_FAILED;
			}
			return DIALOG_WAITING;
		}

		@Override
		protected void onProgressUpdate(Integer... updates) {
			if (activity.m_lastDialog != -1) {
				activity.clearLastDialog();
			}
			activity.showDialog(updates[0]);
		}

		@Override
		protected void onPostExecute(Integer result) {
			activity.clearLastDialog();
			if (result != null) {
				activity.showDialog(result);
			}
		}
	}
}