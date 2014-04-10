package com.hivewallet.androidclient.wallet.util;

import com.commonsware.cwac.loaderex.acl.SQLiteCursorLoader;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class AppManifestDBHelper extends SQLiteOpenHelper
{
	public static final String KEY_ROWID = "_id";
	public static final String KEY_ID = "id";
	public static final String KEY_VERSION = "version";
	public static final String KEY_NAME = "name";
	public static final String KEY_AUTHOR = "author";
	public static final String KEY_CONTACT = "contact";
	public static final String KEY_DESCRIPTION = "description";
	public static final String KEY_ICON = "icon";
	public static final String KEY_ACCESSEDHOSTS = "accessed_hosts";
	public static final String KEY_APIVERSIONMAJOR = "api_version_major";
	public static final String KEY_APIVERSIONMINOR = "api_version_minor";
	public static final String KEY_SORT_PRIORITY = "sort_priority";
	
	private static final String DATABASE_NAME = "manifests";
	private static final int DATABASE_VERSION = 1;
	private static final String TABLE_NAME = "manifests";
	private static final String TABLE_CREATE =
			"CREATE TABLE " + TABLE_NAME + " ("
			+ KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
			+ KEY_ID + " TEXT NOT NULL,"
			+ KEY_VERSION + " TEXT NOT NULL,"
			+ KEY_NAME + " TEXT,"
			+ KEY_AUTHOR + " TEXT,"
			+ KEY_CONTACT + " TEXT,"
			+ KEY_DESCRIPTION + " TEXT,"
			+ KEY_ICON + " TEXT,"
			+ KEY_ACCESSEDHOSTS + " TEXT,"
			+ KEY_APIVERSIONMAJOR + " INTEGER,"
			+ KEY_APIVERSIONMINOR + " INTEGER,"
			+ KEY_SORT_PRIORITY + " INTEGER);";
	private static final String TABLE_CREATE_IDX =
			"CREATE INDEX " + TABLE_NAME + "_idx1 on " + TABLE_NAME + " (" + KEY_ID + ")";
	
	public AppManifestDBHelper(final Context context)
	{
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db)
	{
		db.execSQL(TABLE_CREATE);
		db.execSQL(TABLE_CREATE_IDX);
		insertDefaultApps(db);
	}
	
	private void insertDefaultApps(SQLiteDatabase db)
	{
		ContentValues firstEntry = new ContentValues();
		firstEntry.put(KEY_ID, "com.hivewallet.supporthive");
		firstEntry.put(KEY_VERSION, "3.0.1");
		firstEntry.put(KEY_NAME, "Support Hive");
		firstEntry.put(KEY_AUTHOR, "Taylor Gerring");
		firstEntry.put(KEY_DESCRIPTION, "Help us continue building Hive!");
		firstEntry.put(KEY_ICON, "http://hive-app-registry.herokuapp.com/com.hivewallet.supporthive/icon.png");
		firstEntry.put(KEY_SORT_PRIORITY, 2);
		db.insert(TABLE_NAME, null, firstEntry);
		
		ContentValues secondEntry = new ContentValues();
		secondEntry.put(KEY_ID, "wei-lu.app-store");
		secondEntry.put(KEY_VERSION, "1.1.1");
		secondEntry.put(KEY_NAME, "App Store");
		secondEntry.put(KEY_AUTHOR, "Wei Lu");
		secondEntry.put(KEY_DESCRIPTION, "A marketplace for Hive apps");
		secondEntry.put(KEY_ICON, "https://raw.githubusercontent.com/hivewallet/app-store/master/images/logo.png");
		secondEntry.put(KEY_SORT_PRIORITY, 1);
		db.insert(TABLE_NAME, null, secondEntry);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{
		/* no upgrades so far */
	}
	
	public SQLiteCursorLoader getAllAppsCursorLoader(Context context) {
		return new SQLiteCursorLoader(context, this,
				"select * from " + TABLE_NAME + " order by " + KEY_SORT_PRIORITY, null);
	}
}
