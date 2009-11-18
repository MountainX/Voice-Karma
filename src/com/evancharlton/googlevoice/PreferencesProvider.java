package com.evancharlton.googlevoice;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesProvider {
	private static PreferencesProvider s_instance = null;
	private Context m_context;
	private SharedPreferences m_settings;

	private static final String PREFS_NAME = "com.evancharlton.googlevoice_preferences";
	private static final int BOOLEAN = 0;
	private static final int FLOAT = 1;
	private static final int INT = 2;
	private static final int LONG = 3;
	private static final int STRING = 4;

	public static final String USERNAME = "username";
	public static final String PASSWORD = "password";
	public static final String GV_NUMBER = "gv_number";
	public static final String PIN_NUMBER = "pin_number";
	public static final String USE_CALLBACK = "use_callback";
	public static final String PIN_REQUIRED = "require_pin";
	public static final String ALWAYS_USE_GV = "always_use_gv";
	public static final String LAST_CALL_LOG = "last_call_log";
	public static final String VOICEMAIL_CHECK_INTERVAL = "voicemail_check_interval";
	public static final String HOST_NUMBER = "host_number";
	public static final String SETUP_COMPLETED = "completed_setup";
	public static final String DO_NOT_DISTURB = "do_not_disturb";
	public static final String CLIENT_TOKEN = "ClientLogin_token";
	public static final String RNR_SE_TOKEN = "_rnr_se";

	public static final int PIN_REQUIRED_MASK = 1;
	public static final int PIN_NOT_REQUIRED_MASK = 3;

	private PreferencesProvider(Context context) {
		m_context = context;
		m_settings = m_context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
	}

	public static PreferencesProvider getInstance(Context context) {
		if (s_instance == null) {
			s_instance = new PreferencesProvider(context);
		}
		return s_instance;
	}

	public Context getContext() {
		return m_context;
	}

	public boolean allSet() {
		return getString(USERNAME, "").length() > 0 && getString(PASSWORD, "").length() > 0 && getString(GV_NUMBER, "").length() > 0 && getString(PIN_NUMBER, "").length() > 0;
	}

	public boolean write(String key, boolean value) {
		return write(key, value, BOOLEAN);
	}

	public boolean write(String key, float value) {
		return write(key, value, FLOAT);
	}

	public boolean write(String key, int value) {
		return write(key, value, INT);
	}

	public boolean write(String key, long value) {
		return write(key, value, LONG);
	}

	public boolean write(String key, String value) {
		return write(key, value, STRING);
	}

	private boolean write(String key, Object value, int type) {
		SharedPreferences.Editor editor = m_settings.edit();
		switch (type) {
			case BOOLEAN:
				editor.putBoolean(key, (Boolean) value);
				break;
			case FLOAT:
				editor.putFloat(key, (Float) value);
				break;
			case INT:
				editor.putInt(key, (Integer) value);
				break;
			case LONG:
				editor.putLong(key, (Long) value);
				break;
			case STRING:
				editor.putString(key, (String) value);
				break;
			default:
				throw new IllegalArgumentException("Unrecognized type: " + value.toString());
		}
		return editor.commit();
	}

	public boolean getBoolean(String key, boolean defValue) {
		return m_settings.getBoolean(key, defValue);
	}

	public float getFloat(String key, float defValue) {
		return m_settings.getFloat(key, defValue);
	}

	public int getInt(String key, int defValue) {
		return m_settings.getInt(key, defValue);
	}

	public long getLong(String key, long defValue) {
		return m_settings.getLong(key, defValue);
	}

	public String getString(String key, String defValue) {
		return m_settings.getString(key, defValue);
	}

	public String getString(int array, String key) {
		try {
			int index = getInt(key, 0);
			String data = m_context.getResources().getStringArray(array)[index];
			return data;
		} catch (IndexOutOfBoundsException e) {
			return "";
		}
	}
}