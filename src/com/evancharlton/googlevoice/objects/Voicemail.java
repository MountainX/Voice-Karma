package com.evancharlton.googlevoice.objects;

import java.io.File;
import java.util.HashMap;

import android.telephony.PhoneNumberUtils;

import com.evancharlton.googlevoice.net.GVCommunicator;

public class Voicemail extends Message {
	public static final String FIELD_FILENAME = "filename";
	public static final String FIELD_TRANSCRIPT = "transcript";

	private String m_filename = "";
	private String m_transcript = "";

	public Voicemail(GVCommunicator comm) {
		super(comm);
	}

	public Voicemail(GVCommunicator comm, HashMap<String, String> data) {
		super(comm);
		String d = data.get(FIELD_ID);
		if (d != null) {
			m_id = d;
		}

		d = data.get(FIELD_CONTACT);
		if (d != null) {
			m_contactName = d;
		}

		d = data.get(FIELD_NUMBER);
		if (d != null) {
			if (d.startsWith("Unknown")) {
				m_phoneNumber = "";
				m_displayNumber = "Private";
			} else {
				m_phoneNumber = d;
				m_displayNumber = PhoneNumberUtils.formatNumber(d);
			}
		}

		d = data.get(FIELD_DISPLAY_DATE);
		if (d != null) {
			m_displayDate = d;
		}

		d = data.get(FIELD_IS_READ);
		if (d != null) {
			m_isRead = d.equals("0");
		}

		d = data.get(FIELD_TRANSCRIPT);
		if (d != null) {
			m_transcript = d;
		}
	}

	public boolean download() {
		return m_comm.downloadVoicemail(this);
	}

	public String getFilename() {
		if (m_filename.length() > 0) {
			return m_filename;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(getContactName()).append(" - ").append(getId());
		sb.append(".mp3");
		m_filename = sb.toString();
		return m_filename;
	}

	public String getTranscript() {
		return m_transcript;
	}

	public void setTranscript(String transcript) {
		m_transcript = transcript;
	}

	public boolean isDownloaded() {
		File vm = new File(GVCommunicator.VOICEMAIL_FOLDER + this.getFilename());
		return vm.exists();
	}

	public boolean delete() {
		if (isDownloaded()) {
			File vm = new File(GVCommunicator.VOICEMAIL_FOLDER + this.getFilename());
			vm.delete();
		}
		return m_comm.deleteMessage(this);
	}
}
