/*
 * Copyright 2011-2014 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.hivewallet.androidclient.wallet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivewallet.androidclient.wallet.util.GenericUtils;
import com.hivewallet.androidclient.wallet.util.GenericUtils.BitmapSize;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Bundle;

/**
 * @author Andreas Schildbach
 */
public class AddressBookProvider extends ContentProvider
{
	private static final Logger log = LoggerFactory.getLogger(AddressBookProvider.class);
	
	private static final String DATABASE_TABLE = "address_book";

	public static final String KEY_ROWID = "_id";
	public static final String KEY_ADDRESS = "address";
	public static final String KEY_LABEL = "label";
	public static final String KEY_PHOTO = "photo";

	public static final String SELECTION_QUERY = "q";
	public static final String SELECTION_IN = "in";
	public static final String SELECTION_NOTIN = "notin";
	
	private static final String METHOD_INSERT_OR_UPDATE_PHOTO_URI = "insert_or_update_photo_uri";
	private static final String METHOD_DELETE_STALE_PHOTO_URIS = "delete_stale_photo_uris";
	private static final String PHOTO_URI_PRESENT = "photo_uri_present";
	private static final String STALE_PHOTO_URIS = "stale_photo_uris";
	
	private static final int REASONABLE_BITMAP_SIZE = 200; /* pixels */

	public static Uri contentUri(@Nonnull final String packageName)
	{
		return Uri.parse("content://" + packageName + '.' + DATABASE_TABLE);
	}
	
	public static String resolveLabel(final Context context, @Nonnull final String address)
	{
		AddressBookEntry entry = lookupEntry(context, address);
		return entry == null ? null : entry.getLabel();
	}
	
	public static AddressBookEntry lookupEntry(final Context context, @Nonnull final String address)
	{
		String label = null;
		String photo = null;

		final Uri uri = contentUri(context.getPackageName()).buildUpon().appendPath(address).build();
		final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);

		if (cursor != null)
		{
			if (cursor.moveToFirst()) {
				label = cursor.getString(cursor.getColumnIndexOrThrow(KEY_LABEL));
				photo = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PHOTO));
			}

			cursor.close();
		}
		
		if (label != null) {
			return new AddressBookEntry(address, label, photo);
		} else {
			return null;
		}
	}
	
	/** Records a photo uri in the photo assets table. Returns {@code true} if the uri was already present. */
	public static boolean insertOrUpdatePhotoUri(final Context context, @Nullable final Uri photoUri)
	{
		if (photoUri == null)
			return false;
		
		final Uri uri = contentUri(context.getPackageName());
		Bundle bundle = context.getContentResolver().call(
				uri, METHOD_INSERT_OR_UPDATE_PHOTO_URI, photoUri.toString(), null);
		return bundle.getBoolean(PHOTO_URI_PRESENT);
	}
	
	/** Finds non-permanent photo assets that have not been used in a while.
	 * Deletes them from the database and returns their uris.
	 */
	public static List<Uri> deleteStalePhotoAssets(final Context context)
	{
		final Uri uri = contentUri(context.getPackageName());
		Bundle bundle = context.getContentResolver().call(
				uri, METHOD_DELETE_STALE_PHOTO_URIS, null, null);
		
		List<Uri> photoUris = new ArrayList<Uri>();
		for (String photoUriStr : bundle.getStringArrayList(STALE_PHOTO_URIS)) {
			Uri photoUri = Uri.parse(photoUriStr);
			if (photoUri == null)
				throw new RuntimeException("Invalid URI in photo assets database table");
			photoUris.add(photoUri);
		}
		
		return photoUris;
	}
	
	public static Bitmap ensureReasonableSize(@Nullable Bitmap bitmap) {
		if (bitmap == null)
			return null;
		
		BitmapSize newSize = GenericUtils.calculateReasonableSize(
				bitmap.getWidth(), bitmap.getHeight(), REASONABLE_BITMAP_SIZE);
		
		return Bitmap.createScaledBitmap(bitmap, newSize.getWidth(), newSize.getHeight(), false);
	}
	
	/**
	 * Resizes the provided bitmap and stores it in private storage, returning a URI to it.
	 * It will be deleted on the next clean up cycle, unless marked as permanent in the mean time.  */
	public static Uri storeBitmap(@Nonnull Context context, @Nonnull Bitmap bitmap) {
		if (bitmap == null)
			return null;
		
		Bitmap bitmapScaled = ensureReasonableSize(bitmap);
		if (bitmapScaled == null)
			return null;
		
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		bitmapScaled.compress(CompressFormat.PNG, 100, outStream);
		byte[] photoScaled = outStream.toByteArray();
		
		Uri photoUri = null;
		try {
			/* for some reason calling DigestUtils.sha1Hex() does not work on Android */
			String hash = new String(Hex.encodeHex(DigestUtils.sha1(photoScaled))); 
			
			/* create photo asset and database entry */
			File dir = context.getDir(Constants.PHOTO_ASSETS_FOLDER, Context.MODE_PRIVATE);
			File photoAsset = new File(dir, hash + ".png");
			photoUri = Uri.fromFile(photoAsset);
			boolean alreadyPresent = AddressBookProvider.insertOrUpdatePhotoUri(context, photoUri);
			if (!alreadyPresent) {
				FileUtils.writeByteArrayToFile(photoAsset, photoScaled);
				log.info("Saved photo asset with uri {}", photoUri);
			}
			
			/* use opportunity to clean up photo assets */
			List<Uri> stalePhotoUris = AddressBookProvider.deleteStalePhotoAssets(context);
			for (Uri stalePhotoUri : stalePhotoUris) {
				File stalePhotoAsset = new File(stalePhotoUri.getPath());
				FileUtils.deleteQuietly(stalePhotoAsset);
				log.info("Deleting stale photo asset with uri {}", stalePhotoUri);
			}
		} catch (IOException ignored) {}
		
		return photoUri;
	}

	private Helper helper;

	@Override
	public boolean onCreate()
	{
		helper = new Helper(getContext());
		return true;
	}

	@Override
	public String getType(final Uri uri)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public Uri insert(final Uri uri, final ContentValues values)
	{
		if (uri.getPathSegments().size() != 1)
			throw new IllegalArgumentException(uri.toString());

		final String address = uri.getLastPathSegment();
		values.put(KEY_ADDRESS, address);

		long rowId = helper.getWritableDatabase().insertOrThrow(DATABASE_TABLE, null, values);

		final Uri rowUri = contentUri(getContext().getPackageName()).buildUpon().appendPath(address).appendPath(Long.toString(rowId)).build();

		String photo = values.getAsString(KEY_PHOTO);
		if (photo != null)
			helper.setPhotoAssetAsPermanent(photo, true);
		
		getContext().getContentResolver().notifyChange(rowUri, null);

		return rowUri;
	}

	@Override
	public int update(final Uri uri, final ContentValues values, final String selection, final String[] selectionArgs)
	{
		if (uri.getPathSegments().size() != 1)
			throw new IllegalArgumentException(uri.toString());

		final String address = uri.getLastPathSegment();

		final int count = helper.getWritableDatabase().update(DATABASE_TABLE, values, KEY_ADDRESS + "=?", new String[] { address });

		if (count > 0)
			getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	@Override
	public int delete(final Uri uri, final String selection, final String[] selectionArgs)
	{
		final List<String> pathSegments = uri.getPathSegments();
		if (pathSegments.size() != 1)
			throw new IllegalArgumentException(uri.toString());

		final String address = uri.getLastPathSegment();

		final int count = helper.getWritableDatabase().delete(DATABASE_TABLE, KEY_ADDRESS + "=?", new String[] { address });

		if (count > 0)
			getContext().getContentResolver().notifyChange(uri, null);

		return count;
	}

	@Override
	public Cursor query(final Uri uri, final String[] projection, final String originalSelection, final String[] originalSelectionArgs,
			final String sortOrder)
	{
		final SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		qb.setTables(DATABASE_TABLE);

		final List<String> pathSegments = uri.getPathSegments();
		if (pathSegments.size() > 1)
			throw new IllegalArgumentException(uri.toString());

		String selection = null;
		String[] selectionArgs = null;

		if (pathSegments.size() == 1)
		{
			final String address = uri.getLastPathSegment();

			qb.appendWhere(KEY_ADDRESS + "=");
			qb.appendWhereEscapeString(address);
		}
		else if (SELECTION_IN.equals(originalSelection))
		{
			final String[] addresses = originalSelectionArgs[0].trim().split(",");

			qb.appendWhere(KEY_ADDRESS + " IN (");
			appendAddresses(qb, addresses);
			qb.appendWhere(")");
		}
		else if (SELECTION_NOTIN.equals(originalSelection))
		{
			final String[] addresses = originalSelectionArgs[0].trim().split(",");

			qb.appendWhere(KEY_ADDRESS + " NOT IN (");
			appendAddresses(qb, addresses);
			qb.appendWhere(")");
		}
		else if (SELECTION_QUERY.equals(originalSelection))
		{
			final String query = '%' + originalSelectionArgs[0].trim() + '%';
			selection = KEY_ADDRESS + " LIKE ? OR " + KEY_LABEL + " LIKE ?";
			selectionArgs = new String[] { query, query };
		}

		final Cursor cursor = qb.query(helper.getReadableDatabase(), projection, selection, selectionArgs, null, null, sortOrder);

		cursor.setNotificationUri(getContext().getContentResolver(), uri);

		return cursor;
	}
	
	@Override
	public Bundle call(String method, String arg, Bundle extras)
	{
		if (method.equals(METHOD_INSERT_OR_UPDATE_PHOTO_URI)) {
			if (arg == null)
				return null;
			
			boolean alreadyPresent = helper.insertOrUpdatePhotoAsset(arg); 
			Bundle bundle = new Bundle();
			bundle.putBoolean(PHOTO_URI_PRESENT, alreadyPresent);
			
			return bundle;
		} else if (method.equals(METHOD_DELETE_STALE_PHOTO_URIS)) {
			ArrayList<String> stalePhotoUris = helper.deleteStalePhotoAssets();
			Bundle bundle = new Bundle();
			bundle.putStringArrayList(STALE_PHOTO_URIS, stalePhotoUris);
			
			return bundle;
		} else {
			throw new UnsupportedOperationException("Unknown method: " + method);
		}
	}

	private static void appendAddresses(@Nonnull final SQLiteQueryBuilder qb, @Nonnull final String[] addresses)
	{
		for (final String address : addresses)
		{
			qb.appendWhereEscapeString(address.trim());
			if (!address.equals(addresses[addresses.length - 1]))
				qb.appendWhere(",");
		}
	}
	
	public static class AddressBookEntry
	{
		private String address;
		private String label;
		private Uri photoUri;
		
		public AddressBookEntry(String address, String label, @Nullable String photo)
		{
			this.address = address;
			this.label = label;
			this.photoUri = null;
			if (photo != null)
				this.photoUri = Uri.parse(photo);
		}
		
		public String getAddress()
		{
			return address;
		}
		
		public String getLabel()
		{
			return label;
		}
		
		public Uri getPhotoUri()
		{
			return photoUri;
		}
	}

	private static class Helper extends SQLiteOpenHelper
	{
		private static final String DATABASE_NAME = "address_book";
		private static final String PHOTO_ASSETS_TABLE_NAME = "photos";
		private static final int DATABASE_VERSION = 4;

		private static final String KEY_PERMANENT = "permanent";
		private static final String KEY_TIMESTAMP = "timestamp";		
		
		private static final String DATABASE_CREATE = "CREATE TABLE " + DATABASE_TABLE + " (" //
				+ KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, " //
				+ KEY_ADDRESS + " TEXT NOT NULL, " //
				+ KEY_LABEL + " TEXT NULL, " //
				+ KEY_PHOTO + " TEXT NULL);";
		private static final String INDEX_CREATE = "CREATE INDEX " + DATABASE_TABLE + "_idx1 on " //
				+ DATABASE_TABLE + " (" + KEY_ADDRESS + ");";
		
		private static final String DATABASE_CREATE2 =
				"CREATE TABLE " + PHOTO_ASSETS_TABLE_NAME + " ("
				+ KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
				+ KEY_PHOTO + " TEXT NOT NULL,"
				+ KEY_PERMANENT + " INTEGER NOT NULL,"
				+ KEY_TIMESTAMP + " INTEGER NOT NULL);";
		private static final String INDEX_CREATE2 =
				"CREATE INDEX " + PHOTO_ASSETS_TABLE_NAME + "_idx1 on " + PHOTO_ASSETS_TABLE_NAME + " (" + KEY_PHOTO + ");";
		private static final String INDEX_CREATE3 =
				"CREATE INDEX " + PHOTO_ASSETS_TABLE_NAME + "_idx2 on " + PHOTO_ASSETS_TABLE_NAME + " (" + KEY_TIMESTAMP + ");";		
		
		private static final String UPGRADE1 = "ALTER TABLE " + DATABASE_TABLE + " " //
				+ " ADD " + KEY_PHOTO + " TEXT NULL;";
		
		private static final long ABOUT_A_DAY = 24 * 60 * 60 * 1000; /* in milliseconds */
		
		public Helper(final Context context)
		{
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(final SQLiteDatabase db)
		{
			db.execSQL(DATABASE_CREATE);
			db.execSQL(DATABASE_CREATE2);
			db.execSQL(INDEX_CREATE);
			db.execSQL(INDEX_CREATE2);
			db.execSQL(INDEX_CREATE3);
		}

		@Override
		public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion)
		{
			db.beginTransaction();
			try
			{
				for (int v = oldVersion; v < newVersion; v++)
					upgrade(db, v);

				db.setTransactionSuccessful();
			}
			finally
			{
				db.endTransaction();
			}
		}

		private void upgrade(final SQLiteDatabase db, final int oldVersion)
		{
			if (oldVersion == 1)
			{
				db.execSQL(UPGRADE1);
				db.execSQL(INDEX_CREATE);
			}
			else if (oldVersion == 2)
			{
				/* nothing to to */
			}
			else if (oldVersion == 3)
			{
				db.execSQL(DATABASE_CREATE2);
				db.execSQL(INDEX_CREATE2);
				db.execSQL(INDEX_CREATE3);
			}
			else
			{
				throw new UnsupportedOperationException("old=" + oldVersion);
			}
		}
		
		public boolean insertOrUpdatePhotoAsset(String photo)
		{
			SQLiteDatabase db = getWritableDatabase();
			Cursor cursor = db.query(
					PHOTO_ASSETS_TABLE_NAME, new String[] { KEY_PHOTO }, KEY_PHOTO + " = ?", new String [] { photo }, null, null, null);
			boolean hasHash = cursor.moveToFirst();
			cursor.close();

			ContentValues values = new ContentValues();
			values.put(KEY_PHOTO, photo);
			values.put(KEY_TIMESTAMP, System.currentTimeMillis());
			if (!hasHash) {
				values.put(KEY_PERMANENT, 0);
				db.insert(PHOTO_ASSETS_TABLE_NAME, null, values);
				return false;
			} else {
				db.update(PHOTO_ASSETS_TABLE_NAME, values, KEY_PHOTO + " = ?", new String [] { photo });
				return true;
			}
		}
		
		/** If the provided photo uri is part of the photo assets database, the {@code permanent} field will be set. **/
		public void setPhotoAssetAsPermanent(String photo, boolean isPermanent)
		{
			SQLiteDatabase db = getWritableDatabase();
			
			ContentValues values = new ContentValues();
			values.put(KEY_PHOTO, photo);
			values.put(KEY_PERMANENT, isPermanent ? 1 : 0);
			db.update(PHOTO_ASSETS_TABLE_NAME, values, KEY_PHOTO + " = ?", new String [] { photo });
		}
		
		public ArrayList<String> deleteStalePhotoAssets()
		{
			long cutoffTimestamp = System.currentTimeMillis() - ABOUT_A_DAY;
			SQLiteDatabase db = getReadableDatabase();
			Cursor cursor = db.query(
					PHOTO_ASSETS_TABLE_NAME, new String[] { KEY_PHOTO },
					KEY_PERMANENT + " = 0 AND " + KEY_TIMESTAMP + " < ?",
					new String [] { String.valueOf(cutoffTimestamp) }, null, null, null);
			
			ArrayList<String> staleEntries = new ArrayList<String>();
			while (cursor.moveToNext()) {
				staleEntries.add(cursor.getString(cursor.getColumnIndexOrThrow(KEY_PHOTO)));
			}
			cursor.close();
			
			if (staleEntries.isEmpty())
				return staleEntries;
			
			db = getWritableDatabase();
			for (String photo : staleEntries) {
				db.delete(PHOTO_ASSETS_TABLE_NAME, KEY_PHOTO + " = ?", new String[] { photo });
			}
			return staleEntries;
		}		
	}
}
