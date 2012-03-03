package com.hivewallet.androidclient.wallet.ui;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

import com.hivewallet.androidclient.wallet.R;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.Html;
import android.widget.TextView;

public class EndOfLifeActivity extends FragmentActivity
{
	private static final String SUCCESSOR_APP = "market://details?id=com.hivewallet.hive.cordova";
	
	@InjectView(R.id.tv_end_of_life_message) TextView messageView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.end_of_life_activity);
		ButterKnife.inject(this);
		
		messageView.setText(Html.fromHtml(getString(R.string.end_of_life_message)));
	}
	
	@OnClick(R.id.tv_end_of_life_message) void upgradeHive() {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse(SUCCESSOR_APP));
		startActivity(intent);
	}
}
