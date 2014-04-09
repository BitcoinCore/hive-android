package com.hivewallet.androidclient.wallet.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

public class AppRunnerActivity extends FragmentActivity
{
	public static final String EXTRA_APP_BASE = "app_base";
	
	public static void start(final Context context, final String appBase) {
		Intent intent = new Intent(context, AppRunnerActivity.class);
		intent.putExtra(EXTRA_APP_BASE, appBase);
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
			
			String appBase = extras.getString(EXTRA_APP_BASE);
			if (appBase == null)
				throw new IllegalArgumentException("App base needs to be provided");			
			
			AppRunnerFragment f = AppRunnerFragment.newInstance(appBase);
			getSupportFragmentManager().beginTransaction().add(android.R.id.content, f).commit();
		}
	}
}