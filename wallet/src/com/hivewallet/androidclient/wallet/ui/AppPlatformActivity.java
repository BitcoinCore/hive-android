package com.hivewallet.androidclient.wallet.ui;

import com.hivewallet.androidclient.wallet_test.R;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

public class AppPlatformActivity extends FragmentActivity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.app_platform_activity);
	}
}