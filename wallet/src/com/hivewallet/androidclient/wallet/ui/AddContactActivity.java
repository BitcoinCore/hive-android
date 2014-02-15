package com.hivewallet.androidclient.wallet.ui;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.hivewallet.androidclient.wallet_test.R;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

public class AddContactActivity extends SherlockFragmentActivity
{
	private static final String[] CONTACTS = new String[] {
		"Linda Greenfield", "Bob Smith", "John Doe"
	};

	private AutoCompleteTextView contactNameAutoCompleteTextView;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.add_contact_activity);

		contactNameAutoCompleteTextView = (AutoCompleteTextView)findViewById(R.id.actv_contact_name);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(
				this, android.R.layout.simple_dropdown_item_1line, CONTACTS);
		contactNameAutoCompleteTextView.setThreshold(1);
		contactNameAutoCompleteTextView.setAdapter(adapter);
	}
}
