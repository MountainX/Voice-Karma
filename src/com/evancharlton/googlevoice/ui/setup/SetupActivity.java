package com.evancharlton.googlevoice.ui.setup;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.evancharlton.googlevoice.PreferencesProvider;
import com.evancharlton.googlevoice.R;
import com.evancharlton.googlevoice.net.GVCommunicator;

public abstract class SetupActivity extends OverlayActivity {
	protected Button m_nextButton;
	protected Button m_backButton;
	protected GVCommunicator COMM;
	protected PreferencesProvider PREFS;

	private static final String TITLE = "title";

	@Override
	protected void onCreate(Bundle savedInstanceState, int layout) {
		super.onCreate(savedInstanceState, layout);

		if (savedInstanceState != null) {
			setTitle(savedInstanceState.getString(TITLE));
		}

		COMM = GVCommunicator.getInstance(SetupActivity.this);
		PREFS = PreferencesProvider.getInstance(this);

		initUI();
	}

	@Override
	protected void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);
		icicle.putString(TITLE, getTitle().toString());
	}

	protected abstract void moveToNext();

	protected void initUI() {
		m_nextButton = (Button) findViewById(R.id.next);
		m_backButton = (Button) findViewById(R.id.previous);

		m_nextButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				moveToNext();
			}
		});

		m_backButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				finish();
			}
		});
	}
}
