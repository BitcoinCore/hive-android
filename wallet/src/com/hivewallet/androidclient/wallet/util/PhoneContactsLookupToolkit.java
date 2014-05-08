package com.hivewallet.androidclient.wallet.util;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract.Contacts;
import android.support.v4.widget.SimpleCursorAdapter;
import android.widget.FilterQueryProvider;

public class PhoneContactsLookupToolkit
{
	// Note: This class currently supports pre-Honeycomb devices _almost_.
	// Support will either be completed or removed at some point.
	
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
	        , Contacts.PHOTO_URI		/* specific to API >= 11 */
	        };
	private static int DISPLAY_NAME_COLUMN_INDEX = 2; 
	
	static public SimpleCursorAdapter getContactsAdapter(final Context context, int layout, int viewId) {
		final String[] from_columns = { CONTACTS_DISPLAY_NAME };
		final int[] to_ids = { viewId };
		
		SimpleCursorAdapter simpleCursorAdapter =
				new SimpleCursorAdapter( context
									   , layout
									   , null
									   , from_columns
									   , to_ids
									   , 0
										);
		simpleCursorAdapter.setStringConversionColumn(DISPLAY_NAME_COLUMN_INDEX);
		simpleCursorAdapter.setFilterQueryProvider(new FilterQueryProvider()
		{
			@Override
			public Cursor runQuery(CharSequence constraint)
			{
				return context.getContentResolver().query
						( Contacts.CONTENT_URI
						, PROJECTION
						, CONTACTS_DISPLAY_NAME + " LIKE ?"
						, new String[] { "%" + constraint.toString() + "%" }
						, null
						);
			}
		});
		
		return simpleCursorAdapter;
	}
	
	static public Uri getPhotoUriFromAdapter(SimpleCursorAdapter adapter, int position) {
		Cursor cursor = (Cursor) adapter.getItem(position);
		Uri photoUri = null;
		
		if (cursor != null && cursor.moveToPosition(position)) {
			String photo = cursor.getString(cursor.getColumnIndexOrThrow(Contacts.PHOTO_URI));
			if (photo != null) photoUri = Uri.parse(photo);
		}
		
		return photoUri;
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
				String uriString = cursor.getString(cIdx);
				if (uriString != null)
					uri = Uri.parse(uriString);
			}
			
			cursor.close();
			return uri;
		} else {
			String[] projection = { Contacts._ID };
			Cursor cursor = contentResolver.query
				( Contacts.CONTENT_URI
				, projection
				, Contacts.DISPLAY_NAME + " LIKE ?"
				, new String[] { label }
				, null
				);
			
			Uri uri = null;
			if (cursor.moveToNext()) {
				int cIdx = cursor.getColumnIndexOrThrow(Contacts._ID);
				int contactId = cursor.getInt(cIdx);
				
	    		Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
	    		uri = Uri.withAppendedPath(baseUri, Contacts.Photo.CONTENT_DIRECTORY);
			}
			
			cursor.close();
			return uri;
		}
	}
}
