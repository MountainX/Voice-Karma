package com.evancharlton.googlevoice.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.evancharlton.googlevoice.R;
import com.evancharlton.googlevoice.net.GVCommunicator;
import com.evancharlton.googlevoice.objects.Voicemail;

public class VoicemailView extends Activity {
	private ArrayList<Voicemail> m_voicemail = new ArrayList<Voicemail>();
	private List<Map<String, ?>> m_adapterData = new ArrayList<Map<String, ?>>();
	private ListView m_list;

	private static final int DIALOG_LOGIN_FAILED = 3;
	private static final int REQUEST_CODE_VOICEMAIL_PLAYER = 1;

	private static final int MENU_REFRESH = 101;

	private LoadVoicemailTask m_task;
	private LinearLayout m_progress;
	private TextView m_progressMsg;
	private SimpleAdapter m_adapter;

	@SuppressWarnings("unchecked")
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.voicemail);

		m_progress = (LinearLayout) findViewById(R.id.progress);
		m_progressMsg = (TextView) findViewById(R.id.progress_message);

		String[] from = new String[] {
				Voicemail.FIELD_CONTACT,
				Voicemail.FIELD_NUMBER,
				Voicemail.FIELD_DISPLAY_DATE,
				Voicemail.FIELD_IS_READ
		};

		int[] to = new int[] {
				R.id.contact,
				R.id.callback_number,
				R.id.date,
				R.id.read
		};

		SimpleAdapter adapter = new SimpleAdapter(this, m_adapterData, R.layout.voicemail_row, from, to);
		m_list = (ListView) findViewById(android.R.id.list);
		m_list.setAdapter(adapter);
		m_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				if (position >= 0 && position < m_voicemail.size()) {
					Intent i = new Intent(VoicemailView.this, VoicemailPlayer.class);
					Voicemail v = m_voicemail.get(position);
					i.putExtra(Voicemail.FIELD_ID, v.getId());
					i.putExtra(Voicemail.FIELD_CONTACT, v.getContactName());
					i.putExtra(Voicemail.FIELD_NUMBER, v.getPhoneNumber());
					i.putExtra(Voicemail.FIELD_DISPLAY_DATE, v.getDisplayDate());
					i.putExtra(Voicemail.FIELD_IS_READ, v.isRead());
					i.putExtra(Voicemail.FIELD_TRANSCRIPT, v.getTranscript());
					startActivityForResult(i, REQUEST_CODE_VOICEMAIL_PLAYER);
					m_task.cancel(true);
				}
			}
		});
		m_list.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
			@Override
			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
			}
		});
		m_adapter = ((SimpleAdapter) m_list.getAdapter());

		Object[] saved = (Object[]) getLastNonConfigurationInstance();
		if (saved != null) {
			m_task = (LoadVoicemailTask) saved[0];
			m_voicemail = (ArrayList<Voicemail>) saved[1];
			m_progress.setVisibility((Integer) saved[2]);
			m_progressMsg.setText((CharSequence) saved[3]);
		}

		if (m_task == null) {
			m_task = new LoadVoicemailTask();
		}
		m_task.activity = this;

		if (m_voicemail == null || m_voicemail.size() == 0) {
			if (m_task.getStatus() == AsyncTask.Status.PENDING) {
				m_task.execute();
			}
		} else {
			for (Voicemail v : m_voicemail) {
				HashMap<String, String> vmMap = new HashMap<String, String>();
				vmMap.put(Voicemail.FIELD_CONTACT, v.getContactName());
				vmMap.put(Voicemail.FIELD_NUMBER, v.getDisplayNumber());
				vmMap.put(Voicemail.FIELD_DISPLAY_DATE, v.getDisplayDate());
				vmMap.put(Voicemail.FIELD_IS_READ, v.isRead() ? "" : "(NEW)");
				m_adapterData.add(vmMap);
			}
			m_adapter.notifyDataSetChanged();
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return new Object[] {
				m_task,
				m_voicemail,
				m_progress.getVisibility(),
				m_progressMsg.getText().toString()
		};
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, MENU_REFRESH, Menu.NONE, R.string.reload).setShortcut('3', 'r').setIcon(R.drawable.ic_menu_refresh);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_REFRESH:
				m_adapterData.clear();
				m_adapter.notifyDataSetChanged();
				m_task = new LoadVoicemailTask();
				m_task.activity = this;
				m_task.execute();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
			case DIALOG_LOGIN_FAILED:
				return new AlertDialog.Builder(VoicemailView.this).setTitle(R.string.login_failed_title).setMessage(getString(R.string.login_failed_message) + "\n" + GVCommunicator.getInstance(VoicemailView.this).getError()).setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								removeDialog(DIALOG_LOGIN_FAILED);
								Context c = VoicemailView.this;
								if (isChild()) {
									c = getParent();
								}
								startActivity(new Intent(c, SettingsView.class));
							}
						}).create();
		}
		return null;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == REQUEST_CODE_VOICEMAIL_PLAYER && resultCode == RESULT_OK) {
			m_task = new LoadVoicemailTask();
			m_task.activity = this;
			m_task.execute();
		}
	}

	private static class LoadVoicemailTask extends AsyncTask<String, HashMap<String, String>, Boolean> {
		public VoicemailView activity;
		private boolean m_cancelled = false;
		private GVCommunicator m_comm;

		@Override
		protected void onPreExecute() {
			m_cancelled = false;
			activity.m_progress.setVisibility(View.VISIBLE);
			activity.m_progressMsg.setText(R.string.logging_in);
		}

		@Override
		protected void onCancelled() {
			m_cancelled = true;
		}

		@Override
		protected Boolean doInBackground(String... params) {
			activity.m_voicemail = new ArrayList<Voicemail>();
			m_comm = GVCommunicator.getInstance(activity);
			return load(1);
		}

		@SuppressWarnings("unchecked")
		private Boolean load(int page) {
			if (!m_comm.isLoggedIn()) {
				m_comm.login();
				if (!m_comm.isLoggedIn()) {
					return false;
				}
			}
			if (page > 20) {
				// bail
				return true;
			}
			DefaultHttpClient client = m_comm.getHttpClient();
			HttpGet get = new HttpGet(GVCommunicator.BASE + "/voice/m/i/voicemail?p=" + String.valueOf(page));
			try {
				HttpResponse response = client.execute(get);
				String data = GVCommunicator.getContent(response.getEntity());
				Pattern message = Pattern
						.compile("<div id=\"([^\"]+)\"[^>]*>\\s*<div>\\s*<span>\\s*<img[^>]*>\\s*</span>\\s*(?:<b>\\s*)?<[span]+[^>]*>([^<]+)</[span]+>\\s*(?:</b>\\s*)?<span[^>]*>([^<]+)</span>\\s*<a[^?]+\\?number=([^\"]+)\">call</a>\\s*</div>\\s*<div[^>]*>([\\s\\S]*?)<div[^>]+><a[^>]+>play</a>[^<]+?<a[^>]+?&read=(0|1)");
				Matcher m = message.matcher(data);
				while (m.find()) {
					String transcript = m.group(5).replaceAll("<[^>]+>", "").replaceAll("\\s+", " ");
					HashMap<String, String> info = new HashMap<String, String>();
					info.put(Voicemail.FIELD_ID, m.group(1));
					info.put(Voicemail.FIELD_CONTACT, m.group(2));
					info.put(Voicemail.FIELD_DISPLAY_DATE, m.group(3));
					info.put(Voicemail.FIELD_NUMBER, m.group(4));
					info.put(Voicemail.FIELD_TRANSCRIPT, transcript);
					info.put(Voicemail.FIELD_IS_READ, m.group(6));
					if (m_cancelled == true) {
						return null;
					}
					publishProgress(info);
				}
				if (data.indexOf("/voice/m/i/voicemail?p=" + String.valueOf(page + 1)) >= 0) {
					load(page + 1);
				}
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return true;
		}

		@Override
		protected void onProgressUpdate(HashMap<String, String>... update) {
			activity.m_progressMsg.setText(R.string.loading_voicemail);

			Voicemail v = new Voicemail(m_comm, update[0]);
			update[0].put(Voicemail.FIELD_ID, v.getId());
			update[0].put(Voicemail.FIELD_CONTACT, v.getContactName());
			update[0].put(Voicemail.FIELD_DISPLAY_DATE, v.getDisplayDate());
			update[0].put(Voicemail.FIELD_NUMBER, v.getDisplayNumber());
			update[0].put(Voicemail.FIELD_IS_READ, v.isRead() ? "" : "(NEW)");

			synchronized (activity.m_voicemail) {
				activity.m_voicemail.add(v);
			}
			synchronized (activity.m_adapterData) {
				activity.m_adapterData.add(update[0]);
			}
			activity.m_adapter.notifyDataSetChanged();
		}

		@Override
		protected void onPostExecute(Boolean loaded) {
			activity.m_progress.setVisibility(View.GONE);
			if (!loaded) {
				activity.showDialog(DIALOG_LOGIN_FAILED);
			}
		}
	}
}
