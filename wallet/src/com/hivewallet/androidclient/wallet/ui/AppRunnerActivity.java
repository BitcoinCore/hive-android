package com.hivewallet.androidclient.wallet.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

public class AppRunnerActivity extends FragmentActivity
{
	public static final String EXTRA_APP_ID = "app_id";
	
	public static void start(final Context context, final String appId) {
		Intent intent = new Intent(context, AppRunnerActivity.class);
		intent.putExtra(EXTRA_APP_ID, appId);
		context.startActivity(intent);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		if (savedInstanceState == null) {
			Bundle extras = getIntent().getExtras();
			if (extras == null)
				throw new IllegalArgumentException("This activity requires extra data.");
			
			String appId = extras.getString(EXTRA_APP_ID);
			if (appId == null)
				throw new IllegalArgumentException("App id needs to be provided");			
			
			AppRunnerFragment f = AppRunnerFragment.newInstance(appId);
			getSupportFragmentManager().beginTransaction().add(android.R.id.content, f).commit();
		}
	}
}