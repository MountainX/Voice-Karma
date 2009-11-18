package com.evancharlton.googlevoice.ui.setup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.evancharlton.googlevoice.PreferencesProvider;
import com.evancharlton.googlevoice.R;
import com.evancharlton.googlevoice.objects.Phone;

public class PhoneNumbers extends SetupActivity {
	private LoadPhonesTask m_task;
	private ArrayList<Phone> m_phones = new ArrayList<Phone>();

	private ListView m_list;
	private ArrayList<Map<String, ?>> m_adapterData = new ArrayList<Map<String, ?>>();
	private SimpleAdapter m_adapter;

	private TextView m_gvNumber;

	public static final String PHONES = "phones";

	private static final int EDIT_PHONE = 1;

	private static final int DIALOG_ERROR = 10;

	@SuppressWarnings("unchecked")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState, R.layout.setup_phone_numbers);
		m_backButton.setEnabled(true);

		m_gvNumber = (TextView) findViewById(R.id.number);

		m_task = (LoadPhonesTask) getLastNonConfigurationInstance();
		if (m_task != null) {
			m_task.activity = this;
		} else {
			loadPhones();
		}

		m_gvNumber.setText(PREFS.getString(PreferencesProvider.GV_NUMBER, getString(R.string.loading)));

		if (savedInstanceState != null) {
			m_phones = (ArrayList<Phone>) savedInstanceState.getSerializable(PHONES);
		}

		m_list = (ListView) findViewById(R.id.phones);
		m_adapterData = new ArrayList<Map<String, ?>>();

		addAddPhone();

		for (Phone p : m_phones) {
			HashMap<String, String> map = new HashMap<String, String>();
			map.put(Phone.NAME, p.name);
			m_adapterData.add(map);
		}

		String[] from = new String[] {
			Phone.NAME
		};

		int[] to = new int[] {
			android.R.id.text1
		};

		m_adapter = new SimpleAdapter(this, m_adapterData, android.R.layout.simple_list_item_1, from, to);
		m_list.setAdapter(m_adapter);

		m_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> list, View row, int position, long id) {
				Intent i = null;
				if (position == 0) {
					// add new phone
					i = new Intent(PhoneNumbers.this, AddPhone.class);
				} else {
					position--;
					synchronized (m_phones) {
						Phone p = m_phones.get(position);
						i = new Intent(PhoneNumbers.this, EditPhone.class);
						i.putExtra(Phone.ID, p.id);
						i.putExtra(Phone.NAME, p.name);
						i.putExtra(Phone.NUMBER, p.number);
						i.putExtra(Phone.TYPE, p.type);
						i.putExtra(Phone.SMS, p.sms);
						i.putExtra(Phone.VERIFIED, p.verified);
						i.putExtra(Phone.POLICY, p.policy);
					}
				}
				if (i != null) {
					m_task = null;
					startActivityForResult(i, EDIT_PHONE);
				}
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == EDIT_PHONE) {
			if (resultCode == RESULT_OK) {
				loadPhones();
			}
		}
	}

	private void loadPhones() {
		m_task = new LoadPhonesTask();
		m_task.activity = this;
		m_task.execute();
		m_nextButton.setEnabled(false);
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return m_task;
	}

	@Override
	protected void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);

		icicle.putSerializable(PHONES, m_phones);
	}

	@Override
	protected void moveToNext() {
		startActivity(new Intent(this, ApplicationSettings.class));
	}

	private void addAddPhone() {
		if (m_adapterData != null && m_adapterData.size() == 0) {
			HashMap<String, String> addNew = new HashMap<String, String>();
			addNew.put(Phone.NAME, "Add new phone");
			m_adapterData.add(addNew);
		}
	}

	@Override
	protected Dialog onCreateDialog(int which) {
		switch (which) {
			case DIALOG_ERROR:
				return new AlertDialog.Builder(this).setTitle(R.string.error).setMessage(R.string.phones_error).setPositiveButton(android.R.string.ok, null).create();
		}
		return super.onCreateDialog(which);
	}

	private static class LoadPhonesTask extends AsyncTask<String, Phone, Boolean> {
		public PhoneNumbers activity = null;

		@Override
		protected void onPreExecute() {
			activity.m_phones = new ArrayList<Phone>();
			activity.m_adapterData.clear();
			activity.addAddPhone();
			activity.setTitle(R.string.setup_loading_phones);
			activity.setProgressBarIndeterminateVisibility(true);
			activity.showProgressOverlay(true);
			activity.setProgressMessage(R.string.setup_loading_phones);
		}

		@Override
		protected Boolean doInBackground(String... params) {
			JSONObject json = activity.COMM.getSettings();

			JSONObject settings = (JSONObject) json.get("settings");
			String gvn = (String) settings.get("primaryDid");
			Phone fake = new Phone();
			fake.name = null;
			fake.number = gvn;
			publishProgress(fake);

			JSONObject phones = (JSONObject) json.get("phones");
			if (phones == null) {
				return false;
			}
			for (Object key : phones.keySet()) {
				JSONObject phone = (JSONObject) phones.get(key);
				Phone p = new Phone();

				// build up the phone
				p.id = (Long) phone.get(Phone.ID);
				p.name = (String) phone.get(Phone.NAME);
				p.number = (String) phone.get(Phone.NUMBER);
				p.type = (Long) phone.get(Phone.TYPE);
				p.verified = (Boolean) phone.get(Phone.VERIFIED);
				p.policy = (Long) phone.get(Phone.POLICY);
				p.sms = (Boolean) phone.get(Phone.SMS);

				synchronized (activity.m_phones) {
					activity.m_phones.add(p);
				}

				publishProgress(p);
			}
			return true;
		}

		@Override
		protected void onProgressUpdate(Phone... phones) {
			if (phones[0].name == null) {
				// Google Voice number
				activity.m_gvNumber.setText(PhoneNumberUtils.formatNumber(phones[0].number));
				activity.PREFS.write(PreferencesProvider.GV_NUMBER, phones[0].number);
			} else {
				HashMap<String, String> map = new HashMap<String, String>();
				map.put(Phone.NAME, phones[0].name);

				synchronized (activity.m_adapterData) {
					activity.m_adapterData.add(map);
					activity.m_adapter.notifyDataSetChanged();
				}
			}
		}

		@Override
		protected void onPostExecute(Boolean result) {
			activity.setTitle(R.string.setup_edit_phones);
			activity.setProgressBarIndeterminateVisibility(false);
			activity.showProgressOverlay(false);
			if (result) {
				activity.m_nextButton.setEnabled(activity.m_phones.size() > 0);
			} else {
				activity.showDialog(DIALOG_ERROR);
			}
		}
	}
}
