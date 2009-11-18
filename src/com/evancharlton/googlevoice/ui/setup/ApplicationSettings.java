package com.evancharlton.googlevoice.ui.setup;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.evancharlton.googlevoice.GoogleVoice;
import com.evancharlton.googlevoice.PreferencesProvider;
import com.evancharlton.googlevoice.R;
import com.evancharlton.googlevoice.services.UnreadService;

public class ApplicationSettings extends SetupActivity {
	private static final int DIALOG_NO_CALLBACK = 11;
	private static final int DIALOG_NO_PIN = 12;

	private CheckBox m_alwaysCheck;
	private Spinner m_callMethodSpinner;
	private Spinner m_unreadSpinner;
	private EditText m_phoneEdit;
	private EditText m_pinEdit;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState, R.layout.setup_application_settings);

		m_alwaysCheck = (CheckBox) findViewById(R.id.always_use_gv);
		m_callMethodSpinner = (Spinner) findViewById(R.id.call_methods);
		m_unreadSpinner = (Spinner) findViewById(R.id.unread_interval);
		m_phoneEdit = (EditText) findViewById(R.id.phone_number);
		m_pinEdit = (EditText) findViewById(R.id.pin);

		((TextView) findViewById(R.id.always_label)).setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				m_alwaysCheck.setChecked(!m_alwaysCheck.isChecked());
			}
		});

		m_alwaysCheck.setChecked(PREFS.getBoolean(PreferencesProvider.ALWAYS_USE_GV, false));
		m_callMethodSpinner.setSelection(PREFS.getBoolean(PreferencesProvider.USE_CALLBACK, false) ? 0 : 1);
		String[] times = getResources().getStringArray(R.array.voicemail_intervals_values);
		String time = PREFS.getString(PreferencesProvider.VOICEMAIL_CHECK_INTERVAL, "30");
		int length = times.length;
		for (int i = 0; i < length; i++) {
			if (time.equals(times[i])) {
				m_unreadSpinner.setSelection(i);
				break;
			}
		}

		m_nextButton.setText(R.string.finish);

		TelephonyManager mgr = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
		m_phoneEdit.setText(PREFS.getString(PreferencesProvider.HOST_NUMBER, mgr.getLine1Number()));

		m_pinEdit.setText(PREFS.getString(PreferencesProvider.PIN_NUMBER, ""));
	}

	@Override
	protected Dialog onCreateDialog(int which) {
		switch (which) {
			case DIALOG_NO_PIN:
				return new AlertDialog.Builder(this).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_NO_PIN);
					}
				}).setTitle(R.string.settings_no_pin_title).setMessage(R.string.settings_no_pin_message).create();
			case DIALOG_NO_CALLBACK:
				return new AlertDialog.Builder(this).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_NO_PIN);
					}
				}).setTitle(R.string.settings_no_callback_title).setMessage(R.string.settings_no_callback_message).create();
		}
		return null;
	}

	@Override
	protected void moveToNext() {
		if (m_phoneEdit.getText().length() == 0) {
			showDialog(DIALOG_NO_CALLBACK);
			return;
		}

		if (m_pinEdit.getText().length() == 0) {
			showDialog(DIALOG_NO_PIN);
			return;
		}

		// save preferences
		PREFS.write(PreferencesProvider.ALWAYS_USE_GV, m_alwaysCheck.isChecked());
		PREFS.write(PreferencesProvider.USE_CALLBACK, m_callMethodSpinner.getSelectedItemPosition() == 0);
		String[] times = getResources().getStringArray(R.array.voicemail_intervals_values);
		PREFS.write(PreferencesProvider.VOICEMAIL_CHECK_INTERVAL, times[m_unreadSpinner.getSelectedItemPosition()]);
		PREFS.write(PreferencesProvider.HOST_NUMBER, m_phoneEdit.getText().toString());
		PREFS.write(PreferencesProvider.PIN_NUMBER, m_pinEdit.getText().toString());
		PREFS.write(PreferencesProvider.SETUP_COMPLETED, true);

		UnreadService.actionCancel(this);
		UnreadService.actionReschedule(this);
		// start up the app
		startActivity(new Intent(this, GoogleVoice.class));
	}
}