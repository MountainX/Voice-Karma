package com.evancharlton.googlevoice.ui.setup;

import org.json.simple.JSONObject;

import android.os.Bundle;

import com.evancharlton.googlevoice.objects.Phone;

public class AddPhone extends EditPhone {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void phoneSaved(JSONObject response) {
		JSONObject info = (JSONObject) response.get("data");
		Long id = (Long) info.get(Phone.ID);

		m_phoneId = id;

		verify();
	}

	@Override
	protected void hideOverlay() {
		finish();
	}
}
