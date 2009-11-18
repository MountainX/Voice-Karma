package com.evancharlton.googlevoice.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Contacts;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.Filterable;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.evancharlton.googlevoice.R;
import com.evancharlton.googlevoice.net.GVCommunicator;

public class SMSCompose extends Activity {
	protected AutoCompleteTextView m_to;
	protected EditText m_message;
	protected TextView m_messageCounter;
	protected Button m_send;

	protected String m_threadId = "undefined";
	protected String m_number = "";

	protected SendSMSTask m_task;

	protected static final int MESSAGE_LENGTH = 160;
	protected static final int MESSAGE_THRESHOLD = MESSAGE_LENGTH - 10;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.sms_compose);

		setTitle(R.string.compose_sms);

		ContentResolver content = getContentResolver();
		Cursor cursor = content.query(Contacts.People.CONTENT_URI, PEOPLE_PROJECTION, null, null, Contacts.People.DEFAULT_SORT_ORDER);
		ContactListAdapter adapter = new ContactListAdapter(this, cursor);

		m_to = (AutoCompleteTextView) findViewById(R.id.to);

		m_to.setAdapter(adapter);

		m_to.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
				ListAdapter adapter = m_to.getAdapter();
				if (adapter.getCount() > 0) {
					Cursor c = (Cursor) adapter.getItem(0);
					c.move(position);
					m_number = c.getString(3);
					m_to.setText(c.getString(5));
				}
				m_message.requestFocus();
				updateCounter();
			}
		});

		m_to.setOnKeyListener(new View.OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				m_number = "";
				if (keyCode == KeyEvent.KEYCODE_ENTER) {
					ListAdapter adapter = m_to.getAdapter();
					if (adapter.getCount() > 0) {
						Cursor c = (Cursor) adapter.getItem(0);
						c.moveToFirst();
						m_number = c.getString(3);
						m_to.setText(c.getString(5));
					}
					m_message.requestFocus();
					updateCounter();
					return true;
				}
				return false;
			}
		});
		initUI();

		loadIntent();
	}

	protected void initUI() {
		m_message = (EditText) findViewById(R.id.compose);
		m_send = (Button) findViewById(R.id.send);
		m_messageCounter = (TextView) findViewById(R.id.text_counter);

		m_send.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				sendMessage();
			}
		});

		m_send.setEnabled(false);

		m_message.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (keyCode == KeyEvent.KEYCODE_ENTER) {
					sendMessage();
				}
				return false;
			}
		});

		m_message.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				updateCounter();
			}
		});

		m_message.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (hasFocus) {
					if (m_number.length() == 0) {
						m_number = m_to.getText().toString();
					}
				}
			}
		});

		Object[] data = (Object[]) getLastNonConfigurationInstance();
		if (data != null) {
			m_task = (SendSMSTask) data[0];
			m_to.setText((CharSequence) data[1]);
			m_message.setText((CharSequence) data[2]);
		}
		if (m_task != null) {
			if (m_task.getStatus() == AsyncTask.Status.RUNNING) {
				m_send.setText("Sending...");
				m_send.setEnabled(false);
				m_message.setEnabled(false);
			}
		} else {
			m_task = new SendSMSTask();
		}
		m_task.activity = this;

		setResult(RESULT_CANCELED);
	}

	protected void updateCounter() {
		String text = m_message.getText().toString().trim();
		int length = text.length();
		if (length > MESSAGE_THRESHOLD) {
			int messageCount = (int) Math.ceil((double) length / (double) MESSAGE_LENGTH);
			int len = (MESSAGE_LENGTH - (length % MESSAGE_LENGTH));
			m_messageCounter.setText(String.format("%d/%d", len, messageCount));
			m_messageCounter.setVisibility(View.VISIBLE);
		} else {
			m_messageCounter.setVisibility(View.GONE);
		}

		m_send.setEnabled(!(TextUtils.isEmpty(m_number) || TextUtils.isEmpty(text)));
	}

	@Override
	public void onResume() {
		super.onResume();
		updateCounter();
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return new Object[] {
				m_task,
				m_to.getText().toString(),
				m_message.getText().toString()
		};
	}

	private void loadIntent() {
		Intent i = getIntent();
		if (i != null) {
			String action = i.getAction();
			String scheme = i.getScheme();
			Bundle extras = i.getExtras();
			String msg = "";
			if (extras != null) {
				msg = extras.getString(Intent.EXTRA_TEXT);
			}
			if (Intent.ACTION_SEND.equals(action)) {
				m_message.setText(msg);
			} else if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SENDTO.equals(action)) {
				m_message.setText(msg);
				if ("smsto".equalsIgnoreCase(scheme) || "sms".equalsIgnoreCase(scheme) || "gvsms".equalsIgnoreCase(scheme)) {
					Uri uri = i.getData();
					m_number = Uri.decode(uri.getEncodedSchemeSpecificPart()).replaceAll("[^0-9]", "");
					m_to.setText(m_number);
					if (extras != null) {
						m_message.setText(extras.getString("sms_body"));
					}
					m_message.requestFocus();
					m_send.setEnabled(true);
				}
			}
		}
	}

	private void sendMessage() {
		String msg = m_message.getText().toString().trim();
		if (msg.length() > 0) {
			m_task.execute(m_number, msg);
		}
	}

	protected void messageSent() {
		m_message.setText("");
		m_message.setEnabled(true);
		m_send.setText("Send");
		m_send.setEnabled(true);
		setResult(RESULT_OK);
		finish();
	}

	protected static class SendSMSTask extends AsyncTask<String, Integer, Boolean> {
		public SMSCompose activity;

		@Override
		protected void onPreExecute() {
			activity.m_message.setEnabled(false);
			activity.m_send.setText("Sending...");
			activity.m_send.setEnabled(false);
		}

		@Override
		protected Boolean doInBackground(String... params) {
			GVCommunicator comm = GVCommunicator.getInstance(activity);
			return comm.sendSMS(params[0], params[1], activity.m_threadId);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			activity.messageSent();
		}
	}

	public static class ContactListAdapter extends CursorAdapter implements Filterable {
		public ContactListAdapter(Context context, Cursor c) {
			super(context, c);
			mContent = context.getContentResolver();
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			final LayoutInflater inflater = LayoutInflater.from(context);
			final View view = inflater.inflate(R.layout.simple_expandable_list_item_2, parent, false);
			final TextView name = (TextView) view.findViewById(android.R.id.text1);
			final TextView number = (TextView) view.findViewById(android.R.id.text2);
			name.setText(cursor.getString(5));
			number.setText(PhoneNumberUtils.formatNumber(cursor.getString(3)));
			return view;
		}

		private String getPhoneNumberType(int t) {
			String type;
			switch (t) {
				case Contacts.People.Phones.TYPE_CUSTOM:
					type = "Custom";
					break;
				case Contacts.People.Phones.TYPE_FAX_HOME:
					type = "Fax (Home)";
					break;
				case Contacts.People.Phones.TYPE_FAX_WORK:
					type = "Fax (Work)";
					break;
				case Contacts.People.Phones.TYPE_HOME:
					type = "Home";
					break;
				case Contacts.People.Phones.TYPE_MOBILE:
					type = "Mobile";
					break;
				case Contacts.People.Phones.TYPE_OTHER:
					type = "Other";
					break;
				case Contacts.People.Phones.TYPE_PAGER:
					type = "Pager";
					break;
				case Contacts.People.Phones.TYPE_WORK:
					type = "Work";
					break;
				default:
					type = "Default";
					break;
			}
			return type;
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			final TextView name = (TextView) view.findViewById(android.R.id.text1);
			final TextView number = (TextView) view.findViewById(android.R.id.text2);
			name.setText(cursor.getString(5));
			number.setText(String.format("%s: %s", getPhoneNumberType(cursor.getInt(2)), PhoneNumberUtils.formatNumber(cursor.getString(3))));
		}

		@Override
		public String convertToString(Cursor cursor) {
			return cursor.getString(5);
		}

		@Override
		public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
			if (getFilterQueryProvider() != null) {
				return getFilterQueryProvider().runQuery(constraint);
			}

			StringBuilder buffer = null;
			String[] args = null;
			if (constraint != null) {
				buffer = new StringBuilder();
				buffer.append(Contacts.Phones.NUMBER);
				buffer.append(" != ''");
				buffer.append(" AND ");
				buffer.append(Contacts.People.NAME);
				buffer.append(" LIKE ?");
				args = new String[] {
					constraint.toString() + "%"
				};
			}

			return mContent.query(Contacts.Phones.CONTENT_URI, PEOPLE_PROJECTION, buffer == null ? null : buffer.toString(), args, Contacts.People.DEFAULT_SORT_ORDER);
		}

		private ContentResolver mContent;
	}

	private static final String[] PEOPLE_PROJECTION = new String[] {
			Contacts.People._ID,
			Contacts.People.PRIMARY_PHONE_ID,
			Contacts.People.TYPE,
			Contacts.People.NUMBER,
			Contacts.People.LABEL,
			Contacts.People.NAME,
	};
}
