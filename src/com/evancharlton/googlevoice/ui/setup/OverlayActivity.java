package com.evancharlton.googlevoice.ui.setup;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.evancharlton.googlevoice.R;

public class OverlayActivity extends Activity {
	protected LinearLayout m_blockingOverlay;
	protected TextView m_progressMessage;

	private static final String PROGRESS_MESSAGE = "progress message";
	private static final String PROGRESS_OVERLAY = "progress overlay";

	protected void onCreate(Bundle savedInstanceState, int resLayoutId) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		setContentView(resLayoutId);

		m_blockingOverlay = (LinearLayout) findViewById(R.id.progress_overlay);
		m_progressMessage = (TextView) findViewById(R.id.progress_message);

		if (savedInstanceState != null) {
			if (m_progressMessage != null) {
				m_progressMessage.setText(savedInstanceState.getString(PROGRESS_MESSAGE));
			}
			if (m_blockingOverlay != null) {
				m_blockingOverlay.setVisibility(savedInstanceState.getInt(PROGRESS_OVERLAY));
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);
		if (m_progressMessage != null) {
			icicle.putString(PROGRESS_MESSAGE, m_progressMessage.getText().toString());
			icicle.putInt(PROGRESS_OVERLAY, m_blockingOverlay.getVisibility());
		}
	}

	public void showProgressOverlay(boolean visible) {
		if (visible) {
			m_blockingOverlay.setVisibility(View.VISIBLE);
		} else {
			m_blockingOverlay.setVisibility(View.GONE);
		}
	}

	public void setProgressMessage(String msg) {
		m_progressMessage.setText(msg);
	}

	public void setProgressMessage(int stringId) {
		setProgressMessage(getString(stringId));
	}
}
