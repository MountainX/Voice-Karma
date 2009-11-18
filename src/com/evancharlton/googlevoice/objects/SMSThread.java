package com.evancharlton.googlevoice.objects;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SMSThread implements Serializable {
	private static final long serialVersionUID = 1358959281872589213L;

	public static final String SENDER = "sender";
	public static final String WHEN = "when";
	public static final String COUNT = "count";
	public static final String EXTRA = "extra";
	public static final String READ = "read";

	private String m_sender;
	private String m_when;
	private String m_number;
	private String m_id;
	private Boolean m_isRead;

	private List<SMS> m_messages = new ArrayList<SMS>();

	public SMSThread(String id, String unread, String sender, String when, String number) {
		m_id = id;
		m_isRead = unread == null;
		m_sender = sender;
		m_when = when;
		m_number = number;
	}

	public String getId() {
		return m_id;
	}

	public void addMessage(SMS msg) {
		m_messages.add(msg);
	}

	public List<SMS> getMessages() {
		return m_messages;
	}

	public String getSender() {
		return m_sender;
	}

	public String getNumber() {
		return m_number;
	}

	public String getCount() {
		return String.format("(%d messages)", m_messages.size());
	}

	public String getWhen() {
		return m_when;
	}

	public String getRead() {
		if (!m_isRead) {
			return "(unread)";
		}
		return "";
	}
}
