package com.hivewallet.androidclient.wallet.ui;

import javax.annotation.Nonnull;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.google.bitcoin.core.Transaction;
import com.hivewallet.androidclient.wallet.PaymentIntent;
import com.hivewallet.androidclient.wallet.ui.InputParser.StringInputParser;
import com.hivewallet.androidclient.wallet_test.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.Toast;

public class AddContactActivity extends SherlockFragmentActivity
{
	private static final int REQUEST_CODE_SCAN = 0;
	
	private static final String[] CONTACTS = new String[] {
		"Linda Greenfield", "Bob Smith", "John Doe"
	};

	private AutoCompleteTextView contactNameAutoCompleteTextView;
	private RadioGroup contactTypeRadioGroup;
	private Button shareInvitationButton;
	private Button addContactButton;
	private ImageButton cameraImageButton;
	private EditText hiveInvitationEditText;
	private EditText bitcoinAddressEditText;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.add_contact_activity);

		contactNameAutoCompleteTextView = (AutoCompleteTextView)findViewById(R.id.actv_contact_name);
		contactTypeRadioGroup = (RadioGroup)findViewById(R.id.rg_contact_type);
		shareInvitationButton = (Button)findViewById(R.id.b_share_invitation);
		addContactButton = (Button)findViewById(R.id.b_add_contact);
		cameraImageButton = (ImageButton)findViewById(R.id.ib_camera);
		hiveInvitationEditText = (EditText)findViewById(R.id.et_hive_invitation);
		bitcoinAddressEditText = (EditText)findViewById(R.id.et_bitcoin_address);
		
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(
				this, android.R.layout.simple_dropdown_item_1line, CONTACTS);
		contactNameAutoCompleteTextView.setThreshold(1);
		contactNameAutoCompleteTextView.setAdapter(adapter);

		contactTypeRadioGroup.setOnCheckedChangeListener(contactTypeOnCheckedChangeListener);
		shareInvitationButton.setOnClickListener(shareInvitationOnClickListener);
		cameraImageButton.setOnClickListener(cameraOnClickListener);
		
		changeEnabledElements(true);
	}
	
	public void handleScan()
	{
		startActivityForResult(new Intent(this, ScanActivity.class), REQUEST_CODE_SCAN);
	}	
	
	private void changeEnabledElements(boolean isViaHive) {
		shareInvitationButton.setEnabled(isViaHive);
		hiveInvitationEditText.setEnabled(isViaHive);
		
		addContactButton.setEnabled(!isViaHive);
		cameraImageButton.setEnabled(!isViaHive);
		bitcoinAddressEditText.setEnabled(!isViaHive);
	}
	
	private OnClickListener shareInvitationOnClickListener = new OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			Toast.makeText(getApplicationContext(),
					R.string.hive_connect_not_implemented_yet, Toast.LENGTH_LONG).show();
			
//			Intent intent = new Intent(android.content.Intent.ACTION_SEND);
//			intent.setType("text/plain");
//			intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Hive invitation");
//			intent.putExtra(android.content.Intent.EXTRA_TEXT, getString(R.string.sample_hive_invitation));
//			startActivity(Intent.createChooser(intent, getString(R.string.send_hive_invitiation_via)));
		}
	};
	
	private OnClickListener cameraOnClickListener = new OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			handleScan();
		}
	};
	
	private OnCheckedChangeListener contactTypeOnCheckedChangeListener = new OnCheckedChangeListener()
	{
		@Override
		public void onCheckedChanged(RadioGroup group, int checkedId)
		{
			switch (checkedId) {
				case R.id.rb_connect_via_hive:
					changeEnabledElements(true);
					break;
				case R.id.rb_add_bitcoin_address:
					changeEnabledElements(false);
					break;
			}
		}
	};
	
	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent intent)
	{
		if (requestCode == REQUEST_CODE_SCAN && resultCode == Activity.RESULT_OK)
		{
			final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);

			new StringInputParser(input)
			{
				@Override
				protected void handlePaymentIntent(@Nonnull PaymentIntent paymentIntent)
				{
					if (!paymentIntent.hasAddress())
						return;
					
					bitcoinAddressEditText.setText(paymentIntent.getAddress().toString());
				}

				@Override
				protected void handleDirectTransaction(@Nonnull Transaction transaction)
				{
					/* ignore - do nothing */
				}

				@Override
				protected void error(int messageResId, Object... messageArgs)
				{
					/* ignore - do nothing */
				}
			}.parse();
		}
	}	
}
