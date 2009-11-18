package com.evancharlton.googlevoice.objects;

import android.provider.BaseColumns;
import android.telephony.PhoneNumberUtils;

import com.evancharlton.googlevoice.net.GVCommunicator;

public class Message implements BaseColumns {
	public static final String FIELD_ID = "id";
	public static final String FIELD_CONTACT = "contactName";
	public static final String FIELD_NUMBER = "phoneNumber";
	public static final String FIELD_DISPLAY_NUMBER = "displayNumber";
	public static final String FIELD_DISPLAY_DATE = "displayStartDateTime";
	public static final String FIELD_IS_READ = "isRead";

	protected GVCommunicator m_comm;

	protected String m_id = "";
	protected String m_phoneNumber = "";
	protected String m_displayNumber = "";
	protected String m_displayDate = "";
	protected String m_contactName = "";
	protected boolean m_isRead = true;

	public Message(GVCommunicator comm) {
		m_comm = comm;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return m_id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(String id) {
		m_id = id;
	}

	/**
	 * @return the phoneNumber
	 */
	public String getPhoneNumber() {
		return m_phoneNumber;
	}

	/**
	 * @param phoneNumber the phoneNumber to set
	 */
	public void setPhoneNumber(String phoneNumber) {
		if (phoneNumber.startsWith("Unknown")) {
			m_phoneNumber = "";
			setDisplayNumber("Private");
		} else {
			m_phoneNumber = phoneNumber;
			setDisplayNumber(PhoneNumberUtils.formatNumber(phoneNumber));
		}
	}

	/**
	 * @return the displayNumber
	 */
	public String getDisplayNumber() {
		if (m_displayNumber.length() == 0) {
			return "Private";
		}
		return m_displayNumber;
	}

	public boolean isPrivateNumber() {
		// TODO: constants
		return "Private".equals(getDisplayNumber());
	}

	/**
	 * @param displayNumber the displayNumber to set
	 */
	public void setDisplayNumber(String displayNumber) {
		m_displayNumber = displayNumber;
	}

	public void setContactName(String contactName) {
		m_contactName = contactName;
	}

	public String getContactName() {
		if (m_displayNumber.length() == 0) {
			return "Unknown";
		}
		return m_contactName;
	}

	/**
	 * @return the displayStartDateTime
	 */
	public String getDisplayDate() {
		return m_displayDate;
	}

	/**
	 * @param displayStartDateTime the displayStartDateTime to set
	 */
	public void setDisplayDate(String displayStartDateTime) {
		m_displayDate = displayStartDateTime;
	}

	/**
	 * @return the isRead
	 */
	public boolean isRead() {
		return m_isRead;
	}

	public String getRead() {
		return isRead() ? "" : "(new)";
	}

	/**
	 * @param isRead the isRead to set
	 */
	public void setRead(boolean isRead) {
		m_isRead = isRead;
	}

	public void toggleRead() {
		if (m_comm != null) {
			m_comm.messageMarkRead(m_id, !m_isRead);
			m_isRead = !m_isRead;
		}
	}

	public void blockCaller() {
		if (m_comm != null) {
			m_comm.messageBlockCaller(m_id, true);
		}
	}

	public void unblockCaller() {
		if (m_comm != null) {
			m_comm.messageBlockCaller(m_id, false);
		}
	}
}