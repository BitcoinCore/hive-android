package com.hivewallet.androidclient.wallet.ui;

import com.actionbarsherlock.app.SherlockFragment;
import com.hivewallet.androidclient.wallet.data.ExampleContact;
import com.hivewallet.androidclient.wallet.util.ContactArrayAdapter;
import com.hivewallet.androidclient.wallet_test.R;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ListView;

public class ContactsFragment extends SherlockFragment
{
	private static final ExampleContact[] exampleContacts =
		{ new ExampleContact("Jackie Quinn", R.drawable.example_contact_woman_1, true)
		, new ExampleContact("David Jwohnson", R.drawable.example_contact_man_1, false)
		, new ExampleContact("Lisa Greenfield", R.drawable.example_contact_woman_2, false)
		, new ExampleContact("Carl Smith", R.drawable.example_contact_man_2, true)
		};
	
	private ListView contactsListView;
	private ImageButton addContactImageButton;
	
	private ContactArrayAdapter contactsArrayAdapter;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.contacts_list_view, container, false);

		addContactImageButton = (ImageButton)view.findViewById(R.id.ib_add_contact);
		addContactImageButton.setOnClickListener(addContactOnClickListener);

		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		contactsArrayAdapter = new ContactArrayAdapter(getActivity(), exampleContacts);
		
		contactsListView = (ListView) getActivity().findViewById(R.id.lv_contacts);
		contactsListView.setAdapter(contactsArrayAdapter);
	}

	private OnClickListener addContactOnClickListener = new OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			Intent intent = new Intent(getActivity(), AddContactActivity.class);
			startActivity(intent);
		}
	};
}
