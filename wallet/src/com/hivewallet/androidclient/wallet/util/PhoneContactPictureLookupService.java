package com.hivewallet.androidclient.wallet.util;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;

public class PhoneContactPictureLookupService extends IntentService
{
	public static final String ACTION = "phonecontactpicturelookupresult";
	public static final String LABEL = "label";
	public static final String URI = "uri";
	
	public PhoneContactPictureLookupService()
	{
		super("PhoneContactPictureLookupService");
	}

	@Override
	protected void onHandleIntent(Intent intent)
	{
		String label = intent.getStringExtra(LABEL);
		Uri uri = PhoneContactsLookupToolkit.lookupPhoneContactPicture(this.getContentResolver(), label);
		
		if (uri != null) {
			Intent resultIntent = new Intent();
			resultIntent.setAction(ACTION);
			resultIntent.putExtra(LABEL, label);
			resultIntent.putExtra(URI, uri.toString());
			LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);
		}
	}
}