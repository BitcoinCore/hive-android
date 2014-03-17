package com.hivewallet.androidclient.wallet.util;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.widget.FilterQueryProvider;

public class PhoneContactsLookupToolkit implements LoaderCallbacks<Cursor>
{
	@SuppressLint("InlinedApi")
    private final static String CONTACTS_DISPLAY_NAME = 
        Build.VERSION.SDK_INT
	        >= Build.VERSION_CODES.HONEYCOMB ?
	        Contacts.DISPLAY_NAME_PRIMARY :
	        Contacts.DISPLAY_NAME;
	
	@SuppressLint("InlinedApi")
	private static final String[] PROJECTION =
	        { Contacts._ID
	        , Contacts.LOOKUP_KEY
	        , CONTACTS_DISPLAY_NAME
	        };
	private static int DISPLAY_NAME_COLUMN_INDEX = 2; 
	
	private Context context;
	private SimpleCursorAdapter simpleCursorAdapter;
	
	public PhoneContactsLookupToolkit(Context context, int layout, int viewId)
	{
		this.context = context;
		
		final String[] from_columns = { CONTACTS_DISPLAY_NAME };
		final int[] to_ids = { viewId };
		
		simpleCursorAdapter = new SimpleCursorAdapter( context
													 , layout
													 , null
													 , from_columns
													 , to_ids
													 , 0
													 );
		simpleCursorAdapter.setStringConversionColumn(DISPLAY_NAME_COLUMN_INDEX);
		
		// Setting this FilterQueryProvider probably runs counter to the whole
		// idea of using a CursorLoader to run the query in the background.
		// Unfortunately I could not make the combination of filter + CursorLoader
		// work, so I give up for now and return the query here directly.
		simpleCursorAdapter.setFilterQueryProvider(new FilterQueryProvider()
		{
			@Override
			public Cursor runQuery(CharSequence constraint)
			{
				return getContext().getContentResolver().query
						( Contacts.CONTENT_URI
						, PROJECTION
						, CONTACTS_DISPLAY_NAME + " LIKE ?"
						, new String[] { "%" + constraint.toString() + "%" }
						, null
						);
			}
		});
	}
	
	public CursorAdapter getAdapter() {
		return simpleCursorAdapter;
	}
	
	private Context getContext() {
		return context;
	}

	@Override
	public Loader<Cursor> onCreateLoader(int loaderId, Bundle args)
	{
    	return new CursorLoader( context
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
		simpleCursorAdapter.swapCursor(cursor);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader)
	{
		simpleCursorAdapter.swapCursor(null);
	}
	
	@SuppressLint("InlinedApi")
	static public Uri lookupPhoneContactPicture(ContentResolver contentResolver, String label) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			String[] projection = { Contacts.PHOTO_URI };
			Cursor cursor = contentResolver.query
				( Contacts.CONTENT_URI
				, projection
				, Contacts.DISPLAY_NAME_PRIMARY + " LIKE ?"
				, new String[] { label }
				, null
				);
			
			Uri uri = null;
			if (cursor.moveToNext()) {
				int cIdx = cursor.getColumnIndexOrThrow(Contacts.PHOTO_URI);
				uri = Uri.parse(cursor.getString(cIdx));
			}
			
			cursor.close();
			return uri;
		} else {
			// Contact picture lookup not implement for pre-Honeycomb
			return null;
		}
	}
}
