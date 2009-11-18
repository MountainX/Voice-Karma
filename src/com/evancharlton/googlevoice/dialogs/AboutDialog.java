package com.evancharlton.googlevoice.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.view.View;
import android.widget.Button;

import com.evancharlton.googlevoice.R;

public class AboutDialog extends Dialog {
	public static final int DIALOG_ID = 0xDEADBE47;

	public AboutDialog(Context context) {
		super(context);
		setContentView(R.layout.about);

		Button close = (Button) findViewById(R.id.close_about_btn);
		close.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				dismiss();
			}
		});
		String title = getContext().getString(R.string.app_name);
		try {
			title += " version " + getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), PackageManager.GET_ACTIVITIES).versionName;
		} catch (NameNotFoundException e) {
			title += " Unknown version";
		}
		setTitle(title);
	}

	public static AboutDialog create(Context context) {
		return new AboutDialog(context);
	}
}