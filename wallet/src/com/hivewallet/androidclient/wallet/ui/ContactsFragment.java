package com.hivewallet.androidclient.wallet.ui;

import com.actionbarsherlock.app.SherlockFragment;
import com.hivewallet.androidclient.wallet_test.R;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
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
	@SuppressLint("InlinedApi")
	private final static String[] FROM_COLUMNS =
		{ Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
				Contacts.DISPLAY_NAME_PRIMARY : Contacts.DISPLAY_NAME
		, Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
				Contacts.PHOTO_URI : Contacts._ID
		, Contacts.STARRED
		};

	private final static int[] TO_IDS = 
		{ R.id.tv_contact_name
		, R.id.iv_contact_photo
		, R.id.iv_contact_photo_hive_indicator
		};

	@SuppressLint("InlinedApi")
	private static final String[] PROJECTION =
		{ Contacts._ID
		, Contacts.LOOKUP_KEY
		, Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
				Contacts.DISPLAY_NAME_PRIMARY : Contacts.DISPLAY_NAME
		, Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
				Contacts.PHOTO_URI : Contacts._ID
		, Contacts.STARRED
		};	

	private ListView contactsListView;
	private ImageButton addContactImageButton; 

	private SimpleCursorAdapter cursorAdapter;

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

		contactsListView = (ListView) getActivity().findViewById(R.id.lv_contacts);
		cursorAdapter = new SimpleCursorAdapter( getActivity()
				, R.layout.contacts_list_item
				, null
				, FROM_COLUMNS
				, TO_IDS
				, 0
				);

		cursorAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder()
		{
			@Override
			public boolean setViewValue(View view, Cursor cursor, int columnIndex)
			{
				switch (view.getId()) {
					case R.id.iv_contact_photo:
						return setContactPhoto((ImageView)view, cursor, columnIndex);
					case R.id.iv_contact_photo_hive_indicator:
						return setHiveIndicator((ImageView)view, cursor, columnIndex);
					default:
						return false;
				}
			}

			private boolean setContactPhoto(ImageView view, Cursor cursor, int columnIndex) {
				Uri uri = null;				
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					String uriString = cursor.getString(columnIndex);
					if (uriString != null) uri = Uri.parse(uriString);
				} else {
					int contactId = cursor.getInt(columnIndex);

					Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
					uri = Uri.withAppendedPath(baseUri, Contacts.Photo.CONTENT_DIRECTORY);
				}

				ImageView imageView = (ImageView)view;
				if (uri != null) {
					imageView.setImageURI(uri);
				} else {
					imageView.setImageResource(R.drawable.ic_contact_picture);
				}

				return true;
			}

			private boolean setHiveIndicator(ImageView view, Cursor cursor, int columnIndex) {
				int isStarred = cursor.getInt(columnIndex);

				if (isStarred > 0) {
					view.setVisibility(View.VISIBLE);
				}

				return true;
			}
		});
		contactsListView.setAdapter(cursorAdapter);

		getLoaderManager().initLoader(0, null, this);
	}

	@Override
	public Loader<Cursor> onCreateLoader(int loaderId, Bundle args)
	{
		return new CursorLoader( getActivity()
				, Contacts.CONTENT_URI
				, PROJECTION
				, null
				, null
				, null
				);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor)
	{
		cursorAdapter.swapCursor(cursor);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader)
	{
		cursorAdapter.swapCursor(null);
	}

	public static ContactsFragment newInstance() {
		ContactsFragment cf = new ContactsFragment();

		return cf;
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
