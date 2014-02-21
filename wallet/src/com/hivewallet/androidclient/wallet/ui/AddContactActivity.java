package com.hivewallet.androidclient.wallet.ui;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.hivewallet.androidclient.wallet_test.R;

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

public class AddContactActivity extends SherlockFragmentActivity
{
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
	}
	
	private OnClickListener shareInvitationOnClickListener = new OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			Intent intent = new Intent(android.content.Intent.ACTION_SEND);
			intent.setType("text/plain");
			intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Hive invitation");
			intent.putExtra(android.content.Intent.EXTRA_TEXT, getString(R.string.sample_hive_invitation));
			startActivity(Intent.createChooser(intent, getString(R.string.send_hive_invitiation_via)));
		}
	};
	
	private OnCheckedChangeListener contactTypeOnCheckedChangeListener = new OnCheckedChangeListener()
	{
		@Override
		public void onCheckedChanged(RadioGroup group, int checkedId)
		{
			switch (checkedId) {
				case R.id.rb_connect_via_hive:
					shareInvitationButton.setEnabled(true);
					addContactButton.setEnabled(false);
					break;
				case R.id.rb_add_bitcoin_address:
					shareInvitationButton.setEnabled(false);
					addContactButton.setEnabled(true);
					break;
			}
		}
	};
}
