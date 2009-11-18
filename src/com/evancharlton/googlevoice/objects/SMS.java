package com.evancharlton.googlevoice.objects;

import java.io.Serializable;

import android.content.Context;
import android.text.Html;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.evancharlton.googlevoice.R;

public class SMS implements Serializable {
	private static final long serialVersionUID = -6051884437956033382L;

	public static final String SENDER = "sender";
	public static final String MESSAGE = "message";
	public static final String WHEN = "when";

	private String m_message;
	private String m_when;
	private String m_who;
	private boolean m_self = false;

	public SMS(String who, String message, String when) {
		m_self = who.trim().equalsIgnoreCase("me:");
		m_message = String.format("%s", message.trim());
		m_when = when.trim();
		m_who = who.trim().replaceAll(":$", "");
	}

	public String getSender() {
		return m_who;
	}

	public String getMessage() {
		return m_message;
	}

	public String getWhen() {
		return m_when;
	}

	public void render(LinearLayout container) {
		Context context = container.getContext();
		LinearLayout msg;
		if (m_self) {
			msg = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.sms_self_message, null);
		} else {
			msg = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.sms_other_message, null);
			TextView who = (TextView) msg.findViewById(R.id.sender);
			who.setText(m_who);
		}
		TextView message = (TextView) msg.findViewById(R.id.message);
		TextView when = (TextView) msg.findViewById(R.id.when);
		message.setText(Html.fromHtml(m_message));
		when.setText(m_when);
		container.addView(msg);
	}
}
