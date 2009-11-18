package com.evancharlton.googlevoice.ui.setup;

import java.text.DecimalFormat;
import java.util.Random;

import org.json.simple.JSONObject;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.evancharlton.googlevoice.PreferencesProvider;
import com.evancharlton.googlevoice.R;
import com.evancharlton.googlevoice.net.GVCommunicator;
import com.evancharlton.googlevoice.objects.Phone;

public class EditPhone extends OverlayActivity {
	private static final int MENU_DELETE = 10;
	protected static final int MENU_DISCARD = 11;
	protected static final int MENU_VERIFY = 12;

	private static final int DIALOG_DELETE = 10;
	private static final int DIALOG_DELETE_FAILED = 11;
	protected static final int DIALOG_VERIFY_FAILED = 12;
	protected static final int DIALOG_SAVE_FAILED = 13;

	protected EditText m_nameEdit;
	protected EditText m_numberEdit;
	protected Spinner m_typeSpinner;
	protected Spinner m_policySpinner;
	protected CheckBox m_smsCheck;

	protected LinearLayout m_verifyOverlay;
	protected TextView m_verifyCode;
	protected Button m_verifyNow;
	protected Button m_verifyLater;

	protected int m_verificationCode = -1;
	protected long m_phoneId = 0L;

	private SavePhoneTask m_saveTask;
	private DeletePhoneTask m_deleteTask;
	private VerifyTask m_verifyTask;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState, R.layout.setup_edit_phone);

		m_nameEdit = (EditText) findViewById(R.id.name);
		m_numberEdit = (EditText) findViewById(R.id.number);
		m_typeSpinner = (Spinner) findViewById(R.id.type);
		m_policySpinner = (Spinner) findViewById(R.id.policy);
		m_smsCheck = (CheckBox) findViewById(R.id.sms);

		m_verifyOverlay = (LinearLayout) findViewById(R.id.activation);
		m_verifyCode = (TextView) findViewById(R.id.code);
		m_verifyNow = (Button) findViewById(R.id.connect);
		m_verifyLater = (Button) findViewById(R.id.cancel);

		m_typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> list, View row, int position, long id) {
				m_smsCheck.setEnabled(position == 1); // mobile
			}

			@Override
			public void onNothingSelected(AdapterView<?> list) {
			}
		});

		Intent i = getIntent();
		Bundle extras = i.getExtras();
		if (extras != null) {
			long policy = extras.getLong(Phone.POLICY);
			int position = 0;
			if (policy == 3L) {
				position = 2; // PIN not required
			} else if (policy == 2L) {
				position = 1; // PIN required
			} else {
				position = 0;
			}
			m_policySpinner.setSelection(position);
			m_phoneId = extras.getLong(Phone.ID);
			m_nameEdit.setText(extras.getString(Phone.NAME));
			m_numberEdit.setText(extras.getString(Phone.NUMBER));
			switch ((int) extras.getLong(Phone.TYPE)) {
				case (int) Phone.TYPE_HOME:
					m_typeSpinner.setSelection(0);
					break;
				case (int) Phone.TYPE_MOBILE:
					m_typeSpinner.setSelection(1);
					break;
				case (int) Phone.TYPE_WORK:
					m_typeSpinner.setSelection(2);
					break;
				case (int) Phone.TYPE_GIZMO:
					m_typeSpinner.setSelection(3);
					break;
				default:
					m_typeSpinner.setSelection(0);
					break;
			}

			m_smsCheck.setChecked(extras.getBoolean(Phone.SMS));

			if (extras.getBoolean(Phone.VERIFIED) != true) {
				verify();
			}

			Object[] saved = (Object[]) getLastNonConfigurationInstance();
			if (saved != null) {
				m_saveTask = (SavePhoneTask) saved[0];
				m_verifyTask = (VerifyTask) saved[1];
				m_deleteTask = (DeletePhoneTask) saved[2];

				if (m_saveTask != null) {
					m_saveTask.activity = this;
				}

				if (m_verifyTask != null) {
					m_verifyTask.activity = this;
				}

				if (m_deleteTask != null) {
					m_deleteTask.activity = this;
				}
			}
		}

		setResult(RESULT_OK);
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return new Object[] {
				m_saveTask,
				m_verifyTask,
				m_deleteTask
		};
	}

	protected long getPhonePolicy() {
		int position = m_policySpinner.getSelectedItemPosition();
		switch (position) {
			case 0:
				return Phone.POLICY_NO_DIRECT_ACCESS;
			case 1:
				return Phone.POLICY_PIN_REQUIRED;
			case 2:
				return Phone.POLICY_PIN_NOT_REQUIRED;
		}
		return Phone.POLICY_UNKNOWN;
	}

	protected long getPhoneType() {
		int position = m_typeSpinner.getSelectedItemPosition();
		switch (position) {
			case 0:
				return Phone.TYPE_HOME;
			case 1:
				return Phone.TYPE_MOBILE;
			case 2:
				return Phone.TYPE_WORK;
			case 3:
				return Phone.TYPE_GIZMO;
		}
		return Phone.TYPE_UNKNOWN;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:
				if (m_saveTask == null || m_saveTask.getStatus() != AsyncTask.Status.FINISHED) {
					if (m_numberEdit.getText().length() == 0 && m_nameEdit.getText().length() == 0) {
						setResult(RESULT_CANCELED);
						return super.onKeyDown(keyCode, event);
					} else {
						m_saveTask = new SavePhoneTask();
						m_saveTask.activity = this;
						m_saveTask.execute();
					}
				}
				return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	protected void verify() {
		setTitle(R.string.phone_verify);
		// generate a random number
		m_verificationCode = new Random().nextInt(100);
		DecimalFormat df = new DecimalFormat("00");
		m_verifyCode.setText(df.format(m_verificationCode));

		m_verifyOverlay.setVisibility(View.VISIBLE);

		m_verifyLater.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				hideOverlay();
			}
		});

		m_verifyNow.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				m_verifyTask = new VerifyTask();
				m_verifyTask.activity = EditPhone.this;
				m_verifyTask.execute();
			}
		});

		m_verifyNow.requestFocus();
	}

	protected void hideOverlay() {
		m_verifyOverlay.setVisibility(View.GONE);
		m_nameEdit.requestFocus();
	}

	protected void phoneSaved(JSONObject response) {
		finish();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, R.string.phone_delete).setIcon(R.drawable.ic_menu_delete);
		menu.add(Menu.NONE, MENU_DISCARD, Menu.NONE, android.R.string.cancel).setIcon(R.drawable.ic_menu_close_clear_cancel);
		if (m_verificationCode >= 0) {
			menu.add(Menu.NONE, MENU_VERIFY, Menu.NONE, R.string.phone_verify).setIcon(R.drawable.ic_menu_mark);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_DELETE:
				showDialog(DIALOG_DELETE);
				return true;
			case MENU_DISCARD:
				finish();
				return true;
			case MENU_VERIFY:
				verify();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected Dialog onCreateDialog(int which) {
		switch (which) {
			case DIALOG_DELETE:
				return new AlertDialog.Builder(this).setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						m_deleteTask = new DeletePhoneTask();
						m_deleteTask.activity = EditPhone.this;
						m_deleteTask.execute();
						removeDialog(DIALOG_DELETE);
					}
				}).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_DELETE);
					}
				}).setTitle(R.string.phone_delete_title).setMessage(R.string.phone_delete_message).create();
			case DIALOG_DELETE_FAILED:
				return new AlertDialog.Builder(this).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_DELETE_FAILED);
					}
				}).setTitle(R.string.phone_delete_failed_title).setMessage(R.string.phone_delete_failed_message).create();
			case DIALOG_VERIFY_FAILED:
				return new AlertDialog.Builder(this).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_VERIFY_FAILED);
					}
				}).setTitle(R.string.phone_verify_failed_title).setMessage(R.string.phone_verify_failed_message).create();
			case DIALOG_SAVE_FAILED:
				return new AlertDialog.Builder(this).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_SAVE_FAILED);
					}
				}).setTitle(R.string.phone_save_failed_title).setMessage(R.string.phone_save_failed_message).create();
		}
		return null;
	}

	private static class DeletePhoneTask extends AsyncTask<Long, Integer, Boolean> {
		public EditPhone activity;

		@Override
		protected void onPreExecute() {
			activity.setTitle(R.string.phone_deleting);
			activity.setProgressBarIndeterminateVisibility(true);
			activity.setProgressMessage(R.string.phone_deleting);
			activity.showProgressOverlay(true);
		}

		@Override
		protected Boolean doInBackground(Long... params) {
			return GVCommunicator.getInstance(activity).deletePhone(activity.m_phoneId);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			activity.showProgressOverlay(false);
			if (result) {
				Toast.makeText(activity, activity.getString(R.string.phone_deleted), Toast.LENGTH_SHORT);
				activity.finish();
			} else {
				activity.showDialog(DIALOG_DELETE_FAILED);
			}
		}
	}

	private static class VerifyTask extends AsyncTask<String, Integer, Boolean> {
		public EditPhone activity;

		@Override
		protected void onPreExecute() {
			activity.setTitle(R.string.phone_verifying);
			activity.setProgressBarIndeterminateVisibility(true);
			activity.setProgressMessage(R.string.phone_verifying);
			activity.showProgressOverlay(true);
		}

		@Override
		protected Boolean doInBackground(String... params) {
			String number = activity.m_numberEdit.getText().toString().trim();
			return GVCommunicator.getInstance(activity).verifyPhone(number, activity.m_phoneId, activity.m_verificationCode);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			activity.showProgressOverlay(false);
			activity.setTitle(R.string.phone_verify);
			activity.setProgressBarIndeterminateVisibility(false);
			if (result) {
				activity.m_verifyNow.setText(R.string.phone_verified);
				activity.m_verifyNow.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						activity.finish();
					}
				});
				activity.m_verifyLater.setVisibility(View.GONE);
			} else {
				activity.showDialog(DIALOG_VERIFY_FAILED);
			}
		}
	}

	private static class SavePhoneTask extends AsyncTask<String, Integer, JSONObject> {
		public EditPhone activity;

		@Override
		protected void onPreExecute() {
			activity.setTitle(R.string.phone_saving);
			activity.setProgressBarIndeterminateVisibility(true);
			activity.setProgressMessage(R.string.phone_saving);
			activity.showProgressOverlay(true);
		}

		@Override
		protected JSONObject doInBackground(String... params) {
			Phone phone = new Phone();
			phone.policy = activity.getPhonePolicy();
			phone.number = activity.m_numberEdit.getText().toString().trim();
			phone.name = activity.m_nameEdit.getText().toString().trim();
			phone.id = activity.m_phoneId;
			phone.verified = activity.m_verificationCode == -1;
			phone.type = activity.getPhoneType();
			phone.sms = phone.type == Phone.TYPE_MOBILE && activity.m_smsCheck.isChecked();
			return GVCommunicator.getInstance(activity).savePhone(phone);
		}

		@Override
		protected void onPostExecute(JSONObject result) {
			activity.showProgressOverlay(false);
			activity.setTitle(R.string.setup_edit_phone);
			activity.setProgressBarIndeterminateVisibility(false);
			boolean res = (Boolean) result.get("ok");
			if (!res) {
				activity.showDialog(DIALOG_SAVE_FAILED);
			} else {
				if (activity.m_phoneId == 0L) {
					activity.verify();
				} else {
					activity.finish();
				}
				String number = activity.m_numberEdit.getText().toString().replaceAll("[^0-9]", "");
				TelephonyManager tm = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
				String pn = tm.getLine1Number().trim();
				if (number.endsWith(pn) || pn.endsWith(number)) {
					PreferencesProvider prefs = PreferencesProvider.getInstance(activity);
					prefs.write(PreferencesProvider.PIN_REQUIRED, activity.getPhonePolicy() != Phone.POLICY_PIN_NOT_REQUIRED);
				}
			}
		}
	}
}
