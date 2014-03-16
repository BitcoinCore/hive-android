package com.hivewallet.androidclient.wallet.ui;

import com.actionbarsherlock.app.SherlockFragment;
import com.hivewallet.androidclient.wallet.AddressBookProvider;
import com.hivewallet.androidclient.wallet.util.PhoneContactsLookupToolkit;
import com.hivewallet.androidclient.wallet_test.R;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;

public class ContactsFragment extends SherlockFragment implements LoaderCallbacks<Cursor>
{
	private ListView contactsListView;
	private ImageButton addContactImageButton;
	
	private SimpleCursorAdapter contactsSimpleCursorAdapter;

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

		final String[] from_columns = { AddressBookProvider.KEY_LABEL, AddressBookProvider.KEY_LABEL };
		final int[] to_ids = { R.id.tv_contact_name, R.id.iv_contact_photo };
		contactsSimpleCursorAdapter = new SimpleCursorAdapter
				( getActivity()
				, R.layout.contacts_list_item
				, null
				, from_columns
				, to_ids
				, 0
				);
		
		contactsSimpleCursorAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder()
		{
			@Override
			public boolean setViewValue(View view, Cursor cursor, int columnIndex)
			{
				switch (view.getId()) {
					case R.id.iv_contact_photo:
						return setContactPhoto((ImageView)view, cursor, columnIndex);
					default:
						return false;
				}
			}
			
			private boolean setContactPhoto(ImageView view, Cursor cursor, int columnIndex) {
				String label = cursor.getString(columnIndex);
				
				// this query runs on the UI thread - should probably be changed
				// to run in the background at some point
				Uri uri = PhoneContactsLookupToolkit.lookupPhoneContactPicture(getActivity(), label);
				ImageView imageView = (ImageView)view;
				if (uri != null) {
					imageView.setImageURI(uri);
				} else {
					imageView.setImageResource(R.drawable.ic_contact_picture);
				}
	    	
		    	return true;
			}
		});		
						
		contactsListView = (ListView) getActivity().findViewById(R.id.lv_contacts);
		contactsListView.setAdapter(contactsSimpleCursorAdapter);
		
		getActivity().getSupportLoaderManager().initLoader(0, null, this);
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

	@Override
	public Loader<Cursor> onCreateLoader(int loaderId, Bundle args)
	{
		final Uri uri = AddressBookProvider.contentUri(getActivity().getPackageName());
		CursorLoader loader = new CursorLoader
				( getActivity()
				, uri
				, null
				, null
				, null
				, AddressBookProvider.KEY_LABEL + " COLLATE LOCALIZED ASC"
				);
		return loader;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor)
	{
		contactsSimpleCursorAdapter.swapCursor(cursor);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> arg0)
	{
		contactsSimpleCursorAdapter.swapCursor(null);
	}
}
