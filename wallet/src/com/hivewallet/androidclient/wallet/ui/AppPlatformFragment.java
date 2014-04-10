package com.hivewallet.androidclient.wallet.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;

import com.hivewallet.androidclient.wallet.util.AppPlatformDBHelper;
import com.hivewallet.androidclient.wallet_test.R;
import com.squareup.picasso.Picasso;

@SuppressLint("SetJavaScriptEnabled")
public class AppPlatformFragment extends Fragment
								 implements LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener
{
	private static final String APP_BASE_PREFIX = "file:///android_asset/";
	private static final int ID_APPS_LOADER = 0;
	
	private SimpleCursorAdapter appsAdapter;
	private ListView appsListView;
	
	private AppPlatformDBHelper appPlatformDBHelper;
	
	public AppPlatformFragment() { /* required default constructor */ }
	
	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);
		
		this.appPlatformDBHelper = new AppPlatformDBHelper(activity);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.app_platform_fragment, container, false);
		
		appsListView = (ListView)view.findViewById(R.id.lv_apps);
		appsAdapter = new AppsAdapter(getActivity()); 
		appsListView.setAdapter(appsAdapter);
		appsListView.setOnItemClickListener(this);
		
		return view;
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		
		getLoaderManager().initLoader(ID_APPS_LOADER, null, this);
	}
	
	@Override
	public void onPause()
	{
		getLoaderManager().destroyLoader(ID_APPS_LOADER);
		
		super.onPause();
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		Cursor cursor = (Cursor)parent.getItemAtPosition(position);
		String appId = cursor.getString(cursor.getColumnIndexOrThrow(AppPlatformDBHelper.KEY_ID));
		String appBase = APP_BASE_PREFIX + appId + "/";
		
		AppRunnerActivity.start(getActivity(), appBase);
	}
	
	@Override
	public Loader<Cursor> onCreateLoader(final int id, final Bundle args)
	{
		return appPlatformDBHelper.getAllAppsCursorLoader(getActivity());
	}

	@Override
	public void onLoadFinished(final Loader<Cursor> loader, final Cursor cursor)
	{
		appsAdapter.swapCursor(cursor);
	}

	@Override
	public void onLoaderReset(final Loader<Cursor> loader)
	{
		appsAdapter.swapCursor(null);
	}	
	
	private static class AppsAdapter extends SimpleCursorAdapter {
		private static final String[] APPS_FROM_COLUMNS =
			{ AppPlatformDBHelper.KEY_ICON, AppPlatformDBHelper.KEY_NAME, AppPlatformDBHelper.KEY_DESCRIPTION };
		private static final int[] APPS_TO_IDS =
			{ R.id.iv_app_icon, R.id.tv_app_name, R.id.tv_app_description };
		
		private Context context;
		
		public AppsAdapter(Context context)
		{
			super(context, R.layout.app_platform_list_item, null, APPS_FROM_COLUMNS, APPS_TO_IDS, 0);
			
			this.context = context;
		}
		
		@Override
		public void setViewImage(ImageView imageView, String iconURL)
		{
			Picasso.with(context).load(iconURL).into(imageView);
		}
	}
}