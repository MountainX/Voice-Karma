package com.evancharlton.googlevoice.ui;

import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.text.Html;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import com.evancharlton.googlevoice.PreferencesProvider;
import com.evancharlton.googlevoice.R;
import com.evancharlton.googlevoice.net.GVCommunicator;
import com.evancharlton.googlevoice.objects.Voicemail;

public class VoicemailPlayer extends Activity {
	public static final String MESSAGE = "voicemail message";

	private static final int DIALOG_DELETE = 1;
	private static final int DIALOG_RETURN_CALL = 3;
	private static final int DIALOG_DELETING = 4;
	private static final int DIALOG_DELETE_FAILED = 5;

	private static final int MENU_DELETE = 1;
	private static final int MENU_SPEAKERPHONE = 2;
	private static final int MENU_COPY = 3;

	private MediaPlayer m_player;

	private Voicemail m_message;

	private Button m_playPauseBtn;
	private Button m_callbackBtn;
	private Button m_smsBtn;
	private SeekBar m_seekBar;
	private TextView m_contactName;
	private TextView m_contactNumber;
	private TextView m_transcript;

	private PlayTask m_playTask = new PlayTask();
	private DownloadTask m_downloadTask = new DownloadTask();
	private DeleteVoicemailTask m_deleteTask = new DeleteVoicemailTask();

	private boolean m_speakerPhone = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		Intent i = getIntent();
		Bundle extras = i.getExtras();
		GVCommunicator comm = GVCommunicator.getInstance(this);

		m_message = new Voicemail(comm);
		m_message.setContactName(extras.getString(Voicemail.FIELD_CONTACT));
		m_message.setId(extras.getString(Voicemail.FIELD_ID));
		m_message.setPhoneNumber(extras.getString(Voicemail.FIELD_NUMBER));
		m_message.setDisplayDate(extras.getString(Voicemail.FIELD_DISPLAY_DATE));
		m_message.setRead(extras.getBoolean(Voicemail.FIELD_IS_READ));
		m_message.setTranscript(extras.getString(Voicemail.FIELD_TRANSCRIPT));

		setContentView(R.layout.voicemail_player);
		setTitle(m_message.getDisplayDate());

		initUI();

		AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		am.setRouting(AudioManager.MODE_NORMAL, AudioManager.ROUTE_EARPIECE, AudioManager.ROUTE_ALL);
		m_player = new MediaPlayer();
		m_player.setLooping(false);
		m_downloadTask.execute(m_message);

		setResult(RESULT_CANCELED);

		new ReadTask().execute();
	}

	@Override
	protected void onPause() {
		super.onPause();
		m_player.release();

		// restore the audio state
		AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		am.setRouting(AudioManager.MODE_NORMAL, AudioManager.ROUTE_SPEAKER, AudioManager.ROUTE_ALL);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, R.string.delete).setIcon(android.R.drawable.ic_menu_delete);
		menu.add(Menu.NONE, MENU_SPEAKERPHONE, Menu.NONE, R.string.toggle_speakerphone);
		// FIXME: This needs an icon. Anyone want to contribute one?
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_DELETE:
				showDialog(DIALOG_DELETE);
				return true;
			case MENU_SPEAKERPHONE:
				AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				int route = AudioManager.ROUTE_SPEAKER;
				if (m_speakerPhone) {
					route = AudioManager.ROUTE_EARPIECE;
				}
				m_speakerPhone = !m_speakerPhone;
				am.setRouting(AudioManager.MODE_NORMAL, route, AudioManager.ROUTE_ALL);
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case MENU_COPY:
				ClipboardManager board = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
				board.setText(m_message.getTranscript());
				return true;
		}
		return super.onContextItemSelected(item);
	}

	private void initUI() {
		m_smsBtn = (Button) findViewById(R.id.sms);
		m_seekBar = (SeekBar) findViewById(R.id.seek);
		m_callbackBtn = (Button) findViewById(R.id.reply);
		m_playPauseBtn = (Button) findViewById(R.id.play_pause);
		m_contactName = (TextView) findViewById(R.id.contact);
		m_contactNumber = (TextView) findViewById(R.id.contact_number);
		m_transcript = (TextView) findViewById(R.id.transcript);

		m_transcript.setLongClickable(true);
		m_transcript.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
			@Override
			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
			}
		});

		m_contactName.setText(m_message.getContactName());
		m_contactNumber.setText(m_message.getDisplayNumber());
		m_transcript.setText(Html.fromHtml(m_message.getTranscript()));

		m_transcript.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
			@Override
			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
				menu.add(Menu.NONE, MENU_COPY, Menu.NONE, R.string.copy);
			}
		});

		m_playPauseBtn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (m_player.isPlaying()) {
					pause();
				} else {
					play();
				}
			}
		});

		m_seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				if (m_player != null) {
					pause();
				}
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				double progress = seekBar.getProgress();
				if (m_player != null) {
					m_player.seekTo((int) progress);
					play();
				} else {
					seekBar.setProgress(0);
				}
			}
		});

		m_smsBtn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent i = new Intent(Intent.ACTION_SENDTO);
				Uri uri = Uri.parse("sms:" + m_message.getPhoneNumber());
				i.setData(uri);
				startActivity(i);
			}
		});

		m_callbackBtn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				PreferencesProvider prefs = PreferencesProvider.getInstance(VoicemailPlayer.this);
				if (prefs.getBoolean(PreferencesProvider.ALWAYS_USE_GV, false)) {
					call();
				} else {
					showDialog(DIALOG_RETURN_CALL);
				}
			}
		});

		if (m_message.isPrivateNumber()) {
			m_callbackBtn.setEnabled(false);
			m_smsBtn.setEnabled(false);
		}
	}

	private void call() {
		PreferencesProvider prefs = PreferencesProvider.getInstance(this);
		if (prefs.getBoolean(PreferencesProvider.USE_CALLBACK, true)) {
			GVCommunicator.getInstance(this).connect(m_message.getPhoneNumber());
		} else {
			GVCommunicator.call(this, m_message.getPhoneNumber());
		}
		setResult(RESULT_CANCELED);
		finish();
	}

	private void play() {
		int position = m_player.getCurrentPosition();
		if (position > 0 && position == m_player.getDuration()) {
			m_player.stop();
			try {
				m_player.prepare();
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		m_player.start();
		m_playTask.cancel(true);
		m_playTask = new PlayTask();
		m_playTask.execute(m_player);
		m_playPauseBtn.setText(R.string.pause);
	}

	private void pause() {
		m_player.pause();
		m_playPauseBtn.setText(R.string.play);
		m_playTask.cancel(true);
	}

	@Override
	protected Dialog onCreateDialog(int which) {
		switch (which) {
			case DIALOG_DELETE:
				return new AlertDialog.Builder(this).setTitle(R.string.delete_voicemail_confirm).setMessage(R.string.delete_voicemail_confirm_msg).setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dlg, int which) {
						removeDialog(DIALOG_DELETE);
						m_deleteTask = new DeleteVoicemailTask();
						m_deleteTask.execute(m_message);
						setResult(RESULT_OK);
					}
				}).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dlg, int which) {
						removeDialog(DIALOG_DELETE);
					}
				}).create();
			case DIALOG_RETURN_CALL:
				return new AlertDialog.Builder(this).setTitle(R.string.return_call_title).setMessage(R.string.return_call_message).setPositiveButton(R.string.google_voice, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_RETURN_CALL);
						call();
					}
				}).setNegativeButton(R.string.normal_phone, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_RETURN_CALL);
						Intent i = new Intent(Intent.ACTION_CALL);
						Uri uri = Uri.parse("tel:" + m_message.getPhoneNumber());
						i.setData(uri);
						startActivity(i);
						finish();
					}
				}).create();
			case DIALOG_DELETING:
				ProgressDialog deleting = new ProgressDialog(this);
				deleting.setTitle(R.string.deleting_voicemail);
				deleting.setMessage(getString(R.string.deleting_voicemail_msg));
				deleting.setCancelable(false);
				deleting.setIndeterminate(true);
				return deleting;
			case DIALOG_DELETE_FAILED:
				return new AlertDialog.Builder(this).setTitle(R.string.delete_failed).setMessage(R.string.delete_failed_msg).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						removeDialog(DIALOG_DELETE_FAILED);
					}
				}).create();
		}
		return null;
	}

	private class DownloadTask extends AsyncTask<Voicemail, Integer, Boolean> {
		private String m_filename;
		private Boolean m_cancelled = false;

		@Override
		protected void onCancelled() {
			m_cancelled = true;
		}

		@Override
		protected void onPreExecute() {
			setTitle("Downloading message...");
			setProgressBarIndeterminateVisibility(true);
		}

		@Override
		protected Boolean doInBackground(Voicemail... params) {
			Voicemail message = params[0];
			m_filename = GVCommunicator.VOICEMAIL_FOLDER + message.getFilename();
			boolean success = message.download();
			return success && !m_cancelled;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			setTitle(m_message.getDisplayDate());
			setProgressBarIndeterminateVisibility(false);
			m_playPauseBtn.setEnabled(result);
			if (result) {
				try {
					m_player.setDataSource(m_filename);
					m_player.prepare();
					m_player.start();
					m_player.pause();
					if (m_playTask.getStatus() == AsyncTask.Status.PENDING) {
						m_playTask.execute(m_player);
					}
					m_seekBar.setMax(m_player.getDuration());
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (IllegalStateException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private class PlayTask extends AsyncTask<MediaPlayer, Integer, Boolean> {
		@Override
		protected Boolean doInBackground(MediaPlayer... params) {
			try {
				while (params[0].isPlaying()) {
					if (isCancelled()) {
						break;
					}
					publishProgress(params[0].getCurrentPosition());
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
					}
				}
			} catch (IllegalStateException e) {
				// FIXME this is a quick, terrible fix. Don't judge me.
			}
			return true;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			try {
				if (m_player.getDuration() == m_player.getCurrentPosition()) {
					// done
					m_player.stop();
					try {
						m_player.prepare();
					} catch (IllegalStateException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
					m_player.seekTo(0);
				}
			} catch (IllegalStateException e) {
				// FIXME this is a quick, terrible fix. Don't judge me.
			}
			// one final update
			m_seekBar.setProgress(0);
			m_playPauseBtn.setText(R.string.play);
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			m_seekBar.setProgress(values[0]);
		}
	}

	// TODO: This was copied from ThreadView--abstract this!
	private class ReadTask extends AsyncTask<String, Integer, Boolean> {
		@Override
		protected Boolean doInBackground(String... params) {
			GVCommunicator.getInstance(VoicemailPlayer.this).messageMarkRead(m_message.getId(), true);
			return true;
		}
	}

	private class DeleteVoicemailTask extends AsyncTask<Voicemail, Integer, Boolean> {
		@Override
		protected void onPreExecute() {
			showDialog(DIALOG_DELETING);
		}

		@Override
		protected Boolean doInBackground(Voicemail... params) {
			return params[0].delete();
		}

		@Override
		protected void onPostExecute(Boolean result) {
			removeDialog(DIALOG_DELETING);
			if (result) {
				finish();
			} else {
				showDialog(DIALOG_DELETE_FAILED);
			}
		}
	}
}