package com.evancharlton.googlevoice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.view.View;
import android.widget.AdapterView;
import android.widget.SimpleAdapter;

import com.evancharlton.googlevoice.ui.CallLogActivity;
import com.evancharlton.googlevoice.ui.SMSCompose;
import com.evancharlton.googlevoice.ui.SMSThreads;
import com.evancharlton.googlevoice.ui.VoicemailView;

public class ShortcutCreator extends ListActivity {
	private static final String TEXT = "text";
	private static final String CODE = "request_code";
	private static final String DATA = "data";

	private static final int CODE_QUICK_CALL = 10;
	private static final int CODE_QUICK_SMS = 11;

	private List<HashMap<String, Object>> m_data;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		m_data = new ArrayList<HashMap<String, Object>>();

		// quick call
		HashMap<String, Object> quickCall = new HashMap<String, Object>();
		quickCall.put(TEXT, getString(R.string.shortcut_text_direct_call));
		quickCall.put(CODE, CODE_QUICK_CALL);
		quickCall.put(DATA, new Intent(Intent.ACTION_PICK).setData(Phones.CONTENT_URI));
		m_data.add(quickCall);

		// quick SMS
		HashMap<String, Object> quickSMS = new HashMap<String, Object>();
		quickSMS.put(TEXT, getString(R.string.shortcut_text_direct_sms));
		quickSMS.put(CODE, CODE_QUICK_SMS);
		quickSMS.put(DATA, new Intent(Intent.ACTION_PICK).setData(Phones.CONTENT_URI));
		m_data.add(quickSMS);

		// SMS threads
		HashMap<String, Object> smsThreads = new HashMap<String, Object>();
		smsThreads.put(TEXT, getString(R.string.shortcut_text_sms_threads));
		smsThreads.put(DATA, new Intent().putExtra(Intent.EXTRA_SHORTCUT_INTENT, new Intent(this, SMSThreads.class)).putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.shortcut_sms_threads)).putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
				Intent.ShortcutIconResource.fromContext(this, R.drawable.contacticon)));
		m_data.add(smsThreads);

		// compose SMS
		HashMap<String, Object> composeSMS = new HashMap<String, Object>();
		composeSMS.put(TEXT, getString(R.string.shortcut_text_compose_sms));
		composeSMS.put(DATA, new Intent().putExtra(Intent.EXTRA_SHORTCUT_INTENT, new Intent(this, SMSCompose.class)).putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.shortcut_sms_compose)).putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
				Intent.ShortcutIconResource.fromContext(this, R.drawable.contacticon)));
		m_data.add(composeSMS);

		// voicemail
		HashMap<String, Object> voicemail = new HashMap<String, Object>();
		voicemail.put(TEXT, getString(R.string.shortcut_text_voicemail));
		voicemail.put(DATA, new Intent().putExtra(Intent.EXTRA_SHORTCUT_INTENT, new Intent(this, VoicemailView.class)).putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.shortcut_voicemail)).putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
				Intent.ShortcutIconResource.fromContext(this, R.drawable.contacticon)));
		m_data.add(voicemail);

		// call log
		HashMap<String, Object> callLog = new HashMap<String, Object>();
		callLog.put(TEXT, getString(R.string.shortcut_text_call_log));
		callLog.put(DATA, new Intent().putExtra(Intent.EXTRA_SHORTCUT_INTENT, new Intent(this, CallLogActivity.class)).putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.shortcut_call_log)).putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
				Intent.ShortcutIconResource.fromContext(this, R.drawable.contacticon)));
		m_data.add(callLog);

		String[] from = new String[] {
			TEXT
		};

		int[] to = new int[] {
			android.R.id.text1
		};

		SimpleAdapter adapter = new SimpleAdapter(this, m_data, android.R.layout.simple_list_item_1, from, to);

		getListView().setAdapter(adapter);

		getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> list, View row, int position, long id) {
				HashMap<String, Object> info = m_data.get(position);

				Intent intent = (Intent) info.get(DATA);
				Object code = info.get(CODE);
				if (code != null) {
					startActivityForResult(intent, (Integer) code);
				} else {
					setResult(RESULT_OK, intent);
					finish();
				}
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			Intent i = new Intent();
			boolean set = false;
			Cursor c = null;
			switch (requestCode) {
				case CODE_QUICK_CALL:
					c = managedQuery(data.getData(), new String[] {
							Phones.PERSON_ID,
							Phones.DISPLAY_NAME,
							Phones.NUMBER,
							Phones.TYPE
					}, null, null, null);
					if (c.getCount() == 1) {
						c.moveToFirst();
						Uri uri = Uri.parse(String.format("gv:%s", c.getString(2)));
						Uri personUri = ContentUris.withAppendedId(People.CONTENT_URI, c.getLong(0));
						i.putExtra(Intent.EXTRA_SHORTCUT_INTENT, new Intent(Intent.ACTION_CALL).setData(uri));
						i.putExtra(Intent.EXTRA_SHORTCUT_NAME, c.getString(1));
						i.putExtra(Intent.EXTRA_SHORTCUT_ICON, generatePhoneNumberIcon(personUri, c.getInt(3), R.drawable.contacticon));
						set = true;
					}
					break;
				case CODE_QUICK_SMS:
					c = managedQuery(data.getData(), new String[] {
							Phones.PERSON_ID,
							Phones.DISPLAY_NAME,
							Phones.NUMBER,
							Phones.TYPE
					}, null, null, null);
					if (c.getCount() == 1) {
						c.moveToFirst();
						Uri uri = Uri.parse(String.format("gvsms:%s", c.getString(2)));
						Uri personUri = ContentUris.withAppendedId(People.CONTENT_URI, c.getLong(0));
						i.putExtra(Intent.EXTRA_SHORTCUT_INTENT, new Intent(Intent.ACTION_SENDTO).setData(uri));
						i.putExtra(Intent.EXTRA_SHORTCUT_NAME, c.getString(1));
						i.putExtra(Intent.EXTRA_SHORTCUT_ICON, generatePhoneNumberIcon(personUri, c.getInt(3), R.drawable.stat_notify_sms));
						set = true;
					}
					break;
			}
			if (c != null) {
				c.close();
			}
			if (set) {
				setResult(RESULT_OK, i);
			} else {
				setResult(RESULT_CANCELED);
			}
			finish();
		}
	}

	private Bitmap generatePhoneNumberIcon(Uri personUri, int type, int actionResId) {
		final Resources r = getResources();
		boolean drawPhoneOverlay = true;

		Bitmap photo = People.loadContactPhoto(this, personUri, 0, null);
		if (photo == null) {
			// If there isn't a photo use the generic phone action icon instead
			Bitmap phoneIcon = getPhoneActionIcon(r, R.drawable.contacticon);
			if (phoneIcon != null) {
				photo = phoneIcon;
				// TODO: this is terrible
				drawPhoneOverlay = actionResId == R.drawable.stat_notify_sms;
			} else {
				return null;
			}
		}

		// Setup the drawing classes
		int iconSize = (int) r.getDimension(android.R.dimen.app_icon_size);
		Bitmap icon = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(icon);

		// Copy in the photo
		Paint photoPaint = new Paint();
		photoPaint.setDither(true);
		photoPaint.setFilterBitmap(true);
		Rect src = new Rect(0, 0, photo.getWidth(), photo.getHeight());
		Rect dst = new Rect(0, 0, iconSize, iconSize);
		dst = scaleRect(src, dst);
		canvas.drawBitmap(photo, src, dst, photoPaint);

		// Create an overlay for the phone number type
		String overlay = null;
		switch (type) {
			case Phones.TYPE_HOME:
				overlay = "H";
				break;

			case Phones.TYPE_MOBILE:
				overlay = "M";
				break;

			case Phones.TYPE_WORK:
				overlay = "W";
				break;

			case Phones.TYPE_PAGER:
				overlay = "P";
				break;

			case Phones.TYPE_OTHER:
				overlay = "O";
				break;
		}
		if (overlay != null) {
			Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
			textPaint.setTextSize(20.0f);
			textPaint.setTypeface(Typeface.DEFAULT_BOLD);
			textPaint.setColor(0xFFFFFFFF);
			textPaint.setShadowLayer(3f, 1, 1, 0x99000000);
			canvas.drawText(overlay, 2, 16, textPaint);
		}

		// Draw the phone action icon as an overlay
		if (drawPhoneOverlay) {
			Bitmap phoneIcon = getPhoneActionIcon(r, actionResId);
			if (phoneIcon != null) {
				src.set(0, 0, phoneIcon.getWidth(), phoneIcon.getHeight());
				int iconWidth = icon.getWidth();
				final int WIDTH = 20;
				// TODO: calculate aspect ratio and all that jazz
				dst.set(iconWidth - WIDTH, 0, iconWidth, 17);
				canvas.drawBitmap(phoneIcon, src, dst, photoPaint);
			}
		}

		return icon;
	}

	private Rect scaleRect(Rect src, Rect dst) {
		// avoid distortion
		if (src.height() < dst.height() || src.width() < dst.width()) {
			double aspectRatio = (double) src.height() / (double) src.width();
			boolean landscape = src.height() < src.width();
			int width = dst.width();
			int height = dst.height();
			if (landscape) {
				width = Math.min(src.width(), width);
				height = (int) Math.floor(width * aspectRatio);
			} else {
				height = Math.min(src.height(), height);
				width = (int) Math.floor(height / aspectRatio);
			}
			int top = 0;
			int left = 0;
			int diffX = Math.abs(dst.width() - width);
			int diffY = Math.abs(dst.height() - height);
			top += diffY / 2;
			left += diffX / 2;
			width += diffX / 2;
			height += diffY / 2;
			dst.set(left, top, width, height);
		}
		return dst;
	}

	private Bitmap getPhoneActionIcon(Resources r, int resId) {
		Drawable phoneIcon = r.getDrawable(resId);
		if (phoneIcon instanceof BitmapDrawable) {
			BitmapDrawable bd = (BitmapDrawable) phoneIcon;
			return bd.getBitmap();
		} else {
			return null;
		}
	}
}