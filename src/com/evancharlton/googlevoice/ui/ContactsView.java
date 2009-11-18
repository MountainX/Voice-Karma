package com.evancharlton.googlevoice.ui;

import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Contacts;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.evancharlton.googlevoice.GoogleVoice;
import com.evancharlton.googlevoice.R;

public class ContactsView extends ListActivity implements View.OnCreateContextMenuListener {
	private GoogleVoice m_parent;

	private static final int MENU_SMS = 0;
	private static final int MENU_CALL = 2;

	private static final int CODE_CALL = 1;

	public static final String[] PROJECTION = new String[] {
			Contacts.People._ID,
			Contacts.People.DISPLAY_NAME,
			Contacts.People.NUMBER,
			Contacts.People.TYPE
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		m_parent = (GoogleVoice) getParent();

		ListView lv = getListView();
		lv.setTextFilterEnabled(true);
		lv.setFastScrollEnabled(true);

		lv.setOnCreateContextMenuListener(this);

		final String[] from = new String[] {
				Contacts.People.DISPLAY_NAME,
				Contacts.People.NUMBER,
				Contacts.People.TYPE
		};
		int[] to = new int[] {
				R.id.contact,
				R.id.number,
				R.id.type
		};

		SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, R.layout.contacts, getFilterCursor(""), from, to);
		adapter.setFilterQueryProvider(new FilterQueryProvider() {
			public Cursor runQuery(final CharSequence query) {
				return getFilterCursor(query);
			}
		});
		adapter.setViewBinder(m_viewBinder);
		setListAdapter(adapter);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo info) {
		menu.setHeaderTitle(R.string.operations);
		menu.add(Menu.NONE, MENU_SMS, Menu.NONE, R.string.send_sms);
		menu.add(Menu.NONE, MENU_CALL, Menu.NONE, R.string.call);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		Cursor c = (Cursor) getListAdapter().getItem(info.position);
		String number = c.getString(c.getColumnIndex(Contacts.People.NUMBER)).replaceAll("\\s", "");
		Intent i = new Intent();
		switch (item.getItemId()) {
			case MENU_SMS:
				i.setAction(Intent.ACTION_SENDTO);
				i.setData(Uri.parse(String.format("gvsms:%s", number)));
				startActivity(i);
				return true;
			case MENU_CALL:
				m_parent.call(number);
				return true;
		}
		return super.onContextItemSelected(item);
	}

	protected Cursor getFilterCursor(CharSequence query) {
		Uri uri = Contacts.People.CONTENT_URI;
		if (query.length() > 0) {
			uri = Uri.withAppendedPath(Contacts.People.CONTENT_FILTER_URI, query.toString());
		}
		return managedQuery(uri, PROJECTION, Contacts.People.NUMBER + " != ''", null, Contacts.People.DISPLAY_NAME + " ASC");
	}

	@Override
	protected void onListItemClick(ListView lv, View v, int position, long id) {
		super.onListItemClick(lv, v, position, id);

		Intent intent = new Intent(this, ViewContactActivity.class);
		intent.setData(ContentUris.withAppendedId(Contacts.People.CONTENT_URI, id));

		startActivityForResult(intent, CODE_CALL);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == RESULT_OK) {
			switch (requestCode) {
				case CODE_CALL:
					m_parent.call(data.getData().getSchemeSpecificPart());
					break;
			}
		}
	}

	// FIXMEI don't like this; figure out a way to make this cleaner
	public static String getPhoneNumberType(int t) {
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

	private SimpleCursorAdapter.ViewBinder m_viewBinder = new SimpleCursorAdapter.ViewBinder() {
		@Override
		public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
			if (columnIndex == 3) {// TYPE
				((TextView) view).setText(getPhoneNumberType(cursor.getInt(columnIndex)) + ": ");
				return true;
			}
			return false;
		}
	};
}
