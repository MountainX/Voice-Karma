package com.evancharlton.googlevoice.ui.setup;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.EditText;

import com.evancharlton.googlevoice.PreferencesProvider;
import com.evancharlton.googlevoice.R;

public class LoginInformation extends SetupActivity {
	private LoginTask m_task;
	private EditText m_usernameEdit;
	private EditText m_passwordEdit;

	private static final int DIALOG_LOGIN_FAILED = 10;

	private static final String USERNAME = "username";
	private static final String PASSWORD = "password";

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState, R.layout.setup_login_information);

		m_task = (LoginTask) getLastNonConfigurationInstance();
		if (m_task != null) {
			m_task.activity = this;
		}

		m_usernameEdit = (EditText) findViewById(R.id.username);
		m_passwordEdit = (EditText) findViewById(R.id.password);

		if (savedInstanceState != null) {
			m_usernameEdit.setText(savedInstanceState.getString(USERNAME));
			m_passwordEdit.setText(savedInstanceState.getString(PASSWORD));
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);
		icicle.putString(USERNAME, m_usernameEdit.getText().toString());
		icicle.putString(PASSWORD, m_usernameEdit.getText().toString());
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return m_task;
	}

	@Override
	protected void onResume() {
		super.onResume();

		setTitle(R.string.setup_googlevoice);
		setProgressBarIndeterminateVisibility(false);

		m_backButton.setEnabled(true);
		m_nextButton.setEnabled(true);

		// populate with preferences
		if (m_usernameEdit.getText().length() == 0) {
			m_usernameEdit.setText(PREFS.getString(PreferencesProvider.USERNAME, ""));
		}
		if (m_passwordEdit.getText().length() == 0) {
			m_passwordEdit.setText(PREFS.getString(PreferencesProvider.PASSWORD, ""));
		}
	}

	@Override
	protected void moveToNext() {
		m_task = new LoginTask();
		m_task.activity = this;
		m_task.execute(getText(m_usernameEdit), getText(m_passwordEdit));
	}

	private String getText(EditText e) {
		return e.getText().toString().trim();
	}

	@Override
	protected Dialog onCreateDialog(int which) {
		switch (which) {
			case DIALOG_LOGIN_FAILED:
				return new AlertDialog.Builder(this).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_LOGIN_FAILED);
					}
				}).setTitle(R.string.login_failed_title).setMessage(COMM.getError()).create();
		}
		return null;
	}

	private static class LoginTask extends AsyncTask<String, Integer, Boolean> {
		private Boolean m_cancelled = false;
		private LoginInformation activity = null;

		@Override
		protected void onPreExecute() {
			activity.setTitle(R.string.setup_testing_login);
			activity.setProgressBarIndeterminateVisibility(true);
			activity.setProgressMessage(R.string.setup_testing_login);
			activity.showProgressOverlay(true);
			m_cancelled = false;
		}

		@Override
		protected void onCancelled() {
			m_cancelled = true;
		}

		@Override
		protected Boolean doInBackground(String... params) {
			return activity.COMM.login(params[0], params[1]);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (!m_cancelled) {
				activity.showProgressOverlay(false);
				if (result) {
					activity.PREFS.write(PreferencesProvider.USERNAME, activity.m_usernameEdit.getText().toString().trim());
					activity.PREFS.write(PreferencesProvider.PASSWORD, activity.m_passwordEdit.getText().toString().trim());
					activity.startActivity(new Intent(activity, PhoneNumbers.class));
				} else {
					activity.setTitle(R.string.setup_googlevoice);
					activity.setProgressBarIndeterminateVisibility(false);
					activity.showDialog(DIALOG_LOGIN_FAILED);
					activity.m_nextButton.setEnabled(true);
					activity.m_usernameEdit.setEnabled(true);
					activity.m_passwordEdit.setEnabled(true);
				}
			}
		}
	}
}
