package com.hivewallet.androidclient.wallet.ui;

import java.io.File;

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

import com.hivewallet.androidclient.wallet.Constants;
import com.hivewallet.androidclient.wallet.WalletApplication;
import com.hivewallet.androidclient.wallet.util.AppPlatformDBHelper;
import com.hivewallet.androidclient.wallet_test.R;
import com.squareup.picasso.Picasso;

@SuppressLint("SetJavaScriptEnabled")
public class AppPlatformFragment extends Fragment
								 implements LoaderManager.LoaderCallbacks<Cursor>, OnItemClickListener
{
	private static final int ID_APPS_LOADER = 0;
	
	private SimpleCursorAdapter appsAdapter;
	private ListView appsListView;
	
	private AppPlatformDBHelper appPlatformDBHelper;
	
	public AppPlatformFragment() { /* required default constructor */ }
	
	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);
		
		WalletApplication application = (WalletApplication)activity.getApplication();
		this.appPlatformDBHelper = application.getAppPlatformDBHelper();
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
		
		AppRunnerActivity.start(getActivity(), appId);
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
			this.setViewBinder(viewBinder);
		}
		
		private ViewBinder viewBinder = new ViewBinder()
		{
			@Override
			public boolean setViewValue(View view, Cursor cursor, int columnIdx)
			{
				if (view.getId() == R.id.iv_app_icon) {
					ImageView imageView = (ImageView)view;
					String appId = cursor.getString(cursor.getColumnIndexOrThrow(AppPlatformDBHelper.KEY_ID));
					String icon = cursor.getString(columnIdx);
					
					String appBase = getAppBase(context);
					String iconPath = appBase + appId + "/" + icon;
					
					if (Constants.APP_STORE_ID.equals(appId))
						iconPath = Constants.APP_STORE_ICON;
					
					Picasso.with(context).load(iconPath).into(imageView);
					return true;
		 		} else {
					return false;
				}
			}
		};
		
		private static String getAppBase(Context context) {
			File appPlatform = context.getDir(Constants.APP_PLATFORM_FOLDER, Context.MODE_PRIVATE);
			File appsDir = new File(appPlatform, Constants.APP_PLATFORM_APP_FOLDER);
			return "file://" + appsDir.getAbsolutePath() + "/";
		}     
	}
}