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
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.evancharlton.googlevoice.R;
import com.evancharlton.googlevoice.dialogs.HelpDialog;
import com.evancharlton.googlevoice.net.GVCommunicator;
import com.evancharlton.googlevoice.objects.SMS;
import com.evancharlton.googlevoice.objects.SMSThread;

public class SMSThreads extends Activity {
	private static final int DIALOG_LOGGING_IN = 1;
	private static final int DIALOG_LOADING = 2;
	private static final int DIALOG_DELETE = 3;
	private static final int DIALOG_LOGIN_FAILED = 4;
	private static int REQUEST_THREAD = 1;
	private static final int MENU_DELETE = Menu.FIRST;
	private static final int MENU_RELOAD = Menu.FIRST + 1;

	private List<SMSThread> m_threads = new ArrayList<SMSThread>();
	private LoadTask m_task;
	private ListView m_list;
	private SMSThread m_selectedThread;
	private List<Map<String, ?>> m_adapterData;
	private SimpleAdapter m_adapter;

	@SuppressWarnings("unchecked")
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		setContentView(R.layout.sms_threads);

		LinearLayout header = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.sms_compose_row, null);

		m_list = (ListView) findViewById(R.id.threads);
		if (m_list.getHeaderViewsCount() == 0) {
			m_list.addHeaderView(header);
		}
		m_adapterData = new ArrayList<Map<String, ?>>();

		String[] from = new String[] {
				SMSThread.SENDER,
				SMSThread.WHEN,
				SMSThread.COUNT,
				SMSThread.READ,
		};

		int[] to = new int[] {
				android.R.id.text1,
				R.id.when,
				R.id.count,
				R.id.read
		};

		m_adapter = new SimpleAdapter(this, m_adapterData, R.layout.sms_list, from, to);

		m_list.setAdapter(m_adapter);

		m_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> lv, View v, int position, long id) {
				Intent i;
				if (position == 0) {
					setProgress(-1);
					i = new Intent(SMSThreads.this, SMSCompose.class);
				} else {
					SMSThread t = m_threads.get(position - m_list.getHeaderViewsCount());
					i = new Intent(SMSThreads.this, ThreadView.class);
					i.putExtra(SMSThread.EXTRA, t);
				}
				m_task.cancel(true);
				startActivityForResult(i, REQUEST_THREAD);
			}
		});

		m_list.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo info) {
				AdapterContextMenuInfo aInfo = (AdapterContextMenuInfo) info;
				int count = m_list.getHeaderViewsCount();
				if (aInfo.position >= count) {
					menu.setHeaderTitle(R.string.operations);
					menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, R.string.delete);
					m_selectedThread = m_threads.get(aInfo.position - count);
				}
			}
		});

		Object[] saved = (Object[]) getLastNonConfigurationInstance();
		if (saved != null) {
			m_task = (LoadTask) saved[0];
			m_threads = (List<SMSThread>) saved[1];
			setTitle((CharSequence) saved[2]);
		}

		if (m_task == null) {
			m_task = new LoadTask();
		}
		m_task.activity = this;

		if (m_threads == null || m_threads.size() == 0) {
			if (m_task.getStatus() == AsyncTask.Status.PENDING) {
				m_task.execute();
			}
		} else {
			for (SMSThread t : m_threads) {
				m_adapterData.add(buildMap(t));
			}
			m_adapter.notifyDataSetChanged();
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return new Object[] {
				m_task,
				m_threads,
				getTitle()
		};
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, MENU_RELOAD, Menu.NONE, R.string.reload).setIcon(R.drawable.ic_menu_refresh);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_RELOAD:
				m_adapterData.clear();
				m_threads.clear();
				m_adapter.notifyDataSetChanged();
				m_task = new LoadTask();
				m_task.activity = this;
				m_task.execute();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_DELETE:
				showDialog(DIALOG_DELETE);
				return true;
		}
		return super.onContextItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_THREAD) {
			if (resultCode == RESULT_OK || m_threads == null || m_threads.size() == 0) {
				startActivity(getIntent());
				finish();
			}
		}
	}

	private Map<String, String> buildMap(SMSThread thread) {
		Map<String, String> map = new HashMap<String, String>();
		map.put(SMSThread.SENDER, thread.getSender());
		map.put(SMSThread.WHEN, thread.getWhen());
		map.put(SMSThread.COUNT, thread.getCount());
		map.put(SMSThread.READ, thread.getRead());
		return map;
	}

	private void showProgress(int which) {
		switch (which) {
			case DIALOG_LOGGING_IN:
				setTitle("Logging in to Google Voice");
				setProgressBarIndeterminateVisibility(true);
				break;
			case DIALOG_LOADING:
				setTitle("Downloading messages");
				setProgressBarIndeterminateVisibility(true);
				break;
			default:
				setTitle("Google Voice SMS Conversations");
				setProgressBarIndeterminateVisibility(false);
				break;
		}
	}

	@Override
	protected Dialog onCreateDialog(int which) {
		showProgress(-1);
		switch (which) {
			case HelpDialog.DIALOG_ID:
				return HelpDialog.create(this, R.string.help, R.string.help_contents);
			case DIALOG_LOGIN_FAILED:
				return new AlertDialog.Builder(this).setTitle(R.string.login_failed_title).setMessage(R.string.login_failed_message).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_LOGIN_FAILED);
					}
				}).create();
			case DIALOG_DELETE:
				return new AlertDialog.Builder(this).setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_DELETE);
						new DeleteSMSTask().execute(m_selectedThread);
					}
				}).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_DELETE);
					}
				}).setTitle(R.string.delete_sms_confirm).setMessage(R.string.delete_sms_confirm_msg).create();
		}
		return null;
	}

	private class DeleteSMSTask extends AsyncTask<SMSThread, Integer, Boolean> {
		@Override
		protected Boolean doInBackground(SMSThread... params) {
			GVCommunicator comm = GVCommunicator.getInstance(SMSThreads.this);
			return comm.deleteMessageById(m_selectedThread.getId());
		}

		@Override
		protected void onPostExecute(Boolean result) {
			int index = m_threads.indexOf(m_selectedThread);
			synchronized (m_threads) {
				m_threads.remove(index);
			}
			m_selectedThread = null;
			synchronized (m_adapterData) {
				m_adapterData.remove(index);
			}
			m_adapter.notifyDataSetChanged();
		}
	}

	private static class LoadTask extends AsyncTask<String, SMSThread, Boolean> {
		public SMSThreads activity;
		private boolean m_cancelled = false;
		private GVCommunicator m_comm;

		@Override
		protected void onPreExecute() {
			m_cancelled = false;
			activity.showProgress(DIALOG_LOGGING_IN);
		}

		@Override
		protected Boolean doInBackground(String... params) {
			m_comm = GVCommunicator.getInstance(activity);
			if (!m_comm.isLoggedIn()) {
				m_comm.login();
				if (!m_comm.isLoggedIn()) {
					return false;
				}
			}
			if (m_cancelled == true) {
				return true;
			}
			activity.m_threads = new ArrayList<SMSThread>();
			return load(1);
		}

		private Boolean load(int page) {
			if (page > 20) {
				return true;
			}
			DefaultHttpClient client = m_comm.getHttpClient();
			HttpGet get = new HttpGet(GVCommunicator.BASE + "/voice/m/i/sms?p=" + String.valueOf(page));
			try {
				HttpResponse response = client.execute(get);
				String data = GVCommunicator.getContent(response.getEntity());
				String[] threads = data.split("reply</a>&nbsp;");
				Pattern thread = Pattern.compile("<div id=\"([^\"]+)\"[^>]*>\\s+<div>\\s+<span>\\s+<img[^>]+>\\s+</span>\\s+(<b>)?\\s*<[^>]+>([^<]+)</(?:a|span)>\\s+(?:</b>\\s+)?<span[^>]+>([^<]+)</span>\\s+<a class=\"ms2\" href=\"/voice/m/caller\\?number=([^\"]+)\"");
				Pattern message = Pattern.compile("<div[^>]+>\\s+<span[^>]+><b>([^<]+)</b></span>\\s+<span[^>]+>([^<]+)</span>\\s+<span[^>]+>([^<]+)</span>");
				for (String t : threads) {
					if (m_cancelled == true) {
						return true;
					}
					Matcher tm = thread.matcher(t);
					Matcher smsMatcher = message.matcher(t);
					if (tm.find()) {
						SMSThread found = new SMSThread(tm.group(1), tm.group(2), tm.group(3), tm.group(4), tm.group(5));
						while (smsMatcher.find()) {
							if (m_cancelled == true) {
								return true;
							}
							SMS msg = new SMS(smsMatcher.group(1), smsMatcher.group(2), smsMatcher.group(3));
							found.addMessage(msg);
						}
						publishProgress(found);
					}
				}
				if (data.indexOf("/voice/m/i/sms?p=" + String.valueOf(page + 1)) >= 0) {
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
		protected void onProgressUpdate(SMSThread... updates) {
			activity.showProgress(DIALOG_LOADING);
			synchronized (activity.m_threads) {
				activity.m_threads.add(updates[0]);
			}
			synchronized (activity.m_adapterData) {
				activity.m_adapterData.add(activity.buildMap(updates[0]));
				activity.m_adapter.notifyDataSetChanged();
			}
		}

		@Override
		protected void onPostExecute(Boolean result) {
			activity.showProgress(-1);
			if (!result) {
				activity.showDialog(DIALOG_LOGIN_FAILED);
			}
		}
	}
}
