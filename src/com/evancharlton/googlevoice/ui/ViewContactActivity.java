package com.evancharlton.googlevoice.ui;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Contacts;
import android.provider.Contacts.People;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.evancharlton.googlevoice.GoogleVoice;
import com.evancharlton.googlevoice.R;

public class ViewContactActivity extends Activity {
	private ListView m_list;
	private ImageView m_image;
	private TextView m_name;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setResult(RESULT_CANCELED);
		setContentView(R.layout.view_contact);
		m_name = (TextView) findViewById(R.id.name);
		m_list = (ListView) findViewById(android.R.id.list);
		m_image = (ImageView) findViewById(R.id.photo);

		String[] from = new String[] {
				Contacts.Phones.NUMBER,
				Contacts.Phones.TYPE
		};
		int[] to = new int[] {
				android.R.id.text1,
				android.R.id.text2
		};

		String[] projection = new String[] {
				Contacts.Phones._ID,
				Contacts.Phones.NUMBER,
				Contacts.Phones.TYPE
		};

		Intent i = getIntent();
		String[] args = null;
		String where = null;
		if (i != null) {
			Uri u = i.getData();
			if (u != null) {
				loadDetails(u);
				String id = u.getLastPathSegment();
				args = new String[] {
					id
				};
				where = Contacts.Phones.PERSON_ID + " = ?";
			}
		}

		Cursor cursor = managedQuery(Contacts.Phones.CONTENT_URI, projection, where, args, null);

		SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2, cursor, from, to);
		adapter.setViewBinder(m_viewBinder);
		m_list.setAdapter(adapter);

		m_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> list, View row, int position, long id) {
				Cursor c = (Cursor) m_list.getItemAtPosition(position);
				if (c != null) {
					Intent dial = new Intent(ViewContactActivity.this, GoogleVoice.class);
					dial.setAction(Intent.ACTION_CALL);
					dial.setData(Uri.parse(String.format("gv:%s", c.getString(1).replaceAll("\\s", ""))));
					setResult(RESULT_OK, dial);
					finish();
				}
			}
		});
	}

	private void loadDetails(Uri personUri) {
		String[] projection = new String[] {
			Contacts.People.DISPLAY_NAME,
		};
		Bitmap photo = People.loadContactPhoto(this, personUri, R.drawable.contacticon, null);
		Cursor person = managedQuery(personUri, projection, null, null, null);
		person.moveToFirst();
		m_name.setText(person.getString(0));
		m_image.setImageBitmap(photo);
	}

	private SimpleCursorAdapter.ViewBinder m_viewBinder = new SimpleCursorAdapter.ViewBinder() {
		@Override
		public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
			if (columnIndex == 2) {// TYPE
				((TextView) view).setText(ContactsView.getPhoneNumberType(cursor.getInt(columnIndex)));
				return true;
			}
			return false;
		}
	};
}
