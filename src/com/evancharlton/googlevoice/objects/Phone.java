package com.evancharlton.googlevoice.objects;

import java.io.Serializable;
import java.util.ArrayList;

public class Phone implements Serializable {
	private static final long serialVersionUID = -2396587468859306983L;
	public static final long TYPE_UNKNOWN = -1;
	public static final long TYPE_HOME = 1;
	/**
	 * Only mobile phones can receive SMS messages.
	 */
	public static final long TYPE_MOBILE = 2;
	public static final long TYPE_WORK = 3;
	public static final long TYPE_GIZMO = 7;

	public static final long POLICY_UNKNOWN = -1;
	public static final long POLICY_NO_DIRECT_ACCESS = 0;
	public static final long POLICY_PIN_REQUIRED = 2;
	public static final long POLICY_PIN_NOT_REQUIRED = 3;

	public static final String ID = "id";
	public static final String NAME = "name";
	public static final String NUMBER = "phoneNumber";
	public static final String TYPE = "type";
	public static final String VERIFIED = "verified";
	public static final String POLICY = "policyBitmask";
	public static final String SMS = "smsEnabled";
	public static final String WEEKDAYS = "wd";
	public static final String WEEKENDS = "we";
	public static final String IS_ACTIVE = "active";

	/**
	 * The internal ID used to reference this phone
	 */
	public long id = 0L;

	/**
	 * The name used for this phone
	 */
	public String name = "";

	/**
	 * The phone number for this phone
	 */
	public String number = "";

	/**
	 * The type of phone. See Phone.TYPE_*
	 */
	public long type = TYPE_UNKNOWN;

	/**
	 * Whether or not this phone has been verified
	 */
	public boolean verified = false;

	/**
	 * The voicemail access policy set for this phone
	 */
	public long policy = POLICY_UNKNOWN;

	/**
	 * Whether or not SMS is enabled for this phone.
	 */
	public boolean sms = false;

	/**
	 * When the phone is scheduled to ring on weekdays
	 */
	public RingSchedule weekdays = new RingSchedule();

	/**
	 * When the phone is scheduled to ring on weekends
	 */
	public RingSchedule weekends = new RingSchedule();

	/**
	 * Whether the phone is active or not.
	 */
	public boolean active = false;

	class RingSchedule implements Serializable {
		private static final long serialVersionUID = -7131978041685229790L;
		public ArrayList<TimeRange> ranges = new ArrayList<TimeRange>();
		public boolean allDay = true;
	}

	class TimeRange implements Serializable {
		private static final long serialVersionUID = 4490097147529900288L;
		public static final String START = "startTime";
		public static final String END = "endTime";

		public String start = "";
		public String end = "";
	}
}