package com.hivewallet.androidclient.wallet.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import com.commonsware.cwac.loaderex.acl.SQLiteCursorLoader;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class AppPlatformDBHelper extends SQLiteOpenHelper
{
	public static final String APP_STORE_ID = "wei-lu.app-store";
	public static final String APP_STORE_MANIFEST = "wei-lu.app-store/manifest.json";
	public static final String APP_STORE_ICON = "file:///android_asset/wei-lu.app-store/images/logo.png";
	
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
	
	private static final String[] MANIFEST_KEYS =
		{ KEY_ID, KEY_VERSION, KEY_NAME, KEY_AUTHOR, KEY_CONTACT, KEY_DESCRIPTION
		, KEY_ICON, KEY_ACCESSEDHOSTS, KEY_APIVERSIONMAJOR, KEY_APIVERSIONMINOR
		};
	private static final String[] MINIMAL_MANIFEST_KEYS =
		{ KEY_ID, KEY_VERSION, KEY_NAME, KEY_DESCRIPTION, KEY_ICON };
	private static final Set<String> NON_TEXT_KEYS = new HashSet<String>(Arrays.asList(new String[] 
		{ KEY_ROWID, KEY_APIVERSIONMAJOR, KEY_APIVERSIONMINOR, KEY_SORT_PRIORITY }));
	
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
	
	private AssetManager assetManager;
	
	public AppPlatformDBHelper(final Context context)
	{
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		
		this.assetManager = context.getAssets();
	}

	@Override
	public void onCreate(SQLiteDatabase db)
	{
		db.execSQL(TABLE_CREATE);
		db.execSQL(TABLE_CREATE_IDX);
		
		try {
			insertAppStore(db);
		}
		catch (IOException e) {
			throw new RuntimeException("Error while initializing app store", e);
		}
		catch (JSONException e)
		{
			throw new RuntimeException("Error while initializing app store", e);
		}
	}
	
	private void insertAppStore(SQLiteDatabase db) throws IOException, JSONException
	{
		InputStream is = assetManager.open(APP_STORE_MANIFEST);
		String manifestData = IOUtils.toString(is, Charset.defaultCharset());
		JSONObject manifestJSON = new JSONObject(manifestData);
		manifestJSON.put(KEY_ICON, APP_STORE_ICON);
		addManifest(APP_STORE_ID, manifestJSON, db);
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
	
	public Map<String, String> getAppManifest(String appId) {
		Cursor cursor = getReadableDatabase().query(
				TABLE_NAME, MANIFEST_KEYS, KEY_ID + " = ?", new String [] { appId }, null, null, null);
		if (cursor.moveToFirst()) {
			Map<String, String> manifest = new HashMap<String, String>();
			for (String key : MANIFEST_KEYS) {
				int columnIdx = cursor.getColumnIndexOrThrow(key);
				if (cursor.isNull(columnIdx))
					continue;
				
				if (NON_TEXT_KEYS.contains(key)) {
					int value = cursor.getInt(columnIdx);
					manifest.put(key, Integer.toString(value));
				} else {
					String value = cursor.getString(columnIdx);
					manifest.put(key,  "'" + value + "'");
				}
			}
			return manifest;
		} else {
			return null;
		}
	}
	
	public void addManifest(String appId, JSONObject manifest) {
		SQLiteDatabase db = getWritableDatabase();
		db.delete(TABLE_NAME, KEY_ID + " = ?", new String[] { appId });
		
		addManifest(appId, manifest, db);
	}
	
	private void addManifest(String appId, JSONObject manifest, SQLiteDatabase db) {
		ContentValues values = new ContentValues();
		HashSet<String> validKeys = new HashSet<String>(Arrays.asList(MANIFEST_KEYS));
		
		@SuppressWarnings("unchecked")
		Iterator<String> iter = manifest.keys();
		while (iter.hasNext()) {
			String key = iter.next();
			if (!validKeys.contains(key))
				continue;
			
			try {
				if (NON_TEXT_KEYS.contains(key)) {
					int value = manifest.getInt(key);
					values.put(key, value);
				} else {
					String value = manifest.getString(key);
					values.put(key, value);
				}
			} catch (JSONException e) { /* skip key */ };
		}
		values.put(KEY_SORT_PRIORITY, 100);
		db.insert(TABLE_NAME, null, values);
	}
	
	public static List<String> getMinimalManifestKeys() {
		return Collections.unmodifiableList(Arrays.asList(MINIMAL_MANIFEST_KEYS));
	}
}
