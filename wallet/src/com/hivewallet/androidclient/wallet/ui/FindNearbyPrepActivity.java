package com.hivewallet.androidclient.wallet.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

import com.hivewallet.androidclient.wallet_test.R;
import com.squareup.picasso.Picasso;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.database.Cursor;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;

public class FindNearbyPrepActivity extends FragmentActivity implements LoaderCallbacks<Cursor>
{
	private SimpleCursorAdapter userSimpleCursorAdapter;
	
	@InjectView(R.id.cb_via_bluetooth) CheckBox viaBluetoothCheckbox;
	@InjectView(R.id.cb_via_server) CheckBox viaServerCheckbox;
	@InjectView(R.id.lv_user) ListView userListView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.find_nearby_prep_activity);
		ButterKnife.inject(this);
		
		if (BluetoothAdapter.getDefaultAdapter() == null) {
			/* no Bluetooth available */
			viaBluetoothCheckbox.setChecked(false);
			viaBluetoothCheckbox.setEnabled(false);
		}
		
		LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		if (locationManager == null) {
			/* no GPS available */
			viaServerCheckbox.setChecked(false);
			viaServerCheckbox.setEnabled(false);
		}
		
		setupUserListView();
	}
	
	private void setupUserListView()
	{
		final String[] from_columns = { Contacts.PHOTO_URI
				  				      , Contacts.DISPLAY_NAME
				  					  };
		final int[] to_ids = { R.id.iv_user_photo, R.id.tv_user_name };
		userSimpleCursorAdapter= new SimpleCursorAdapter(
				this
				, R.layout.find_nearby_user_list_item
				, null
				, from_columns
				, to_ids
				, 0
				);

		userSimpleCursorAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder()
		{
			public boolean setViewValue(View view, Cursor cursor, int columnIndex)
			{
				switch (view.getId()) {
					case R.id.iv_user_photo:
						return setUserPhoto((ImageView)view, cursor, columnIndex);
					default:
						return false;
				}
			}

			private boolean setUserPhoto(ImageView imageView, Cursor cursor, int columnIndex)
			{
				String photo = cursor.getString(columnIndex);
				Uri uri = null;
				if (photo != null)
					uri = Uri.parse(photo);

				Picasso.with(FindNearbyPrepActivity.this)
				.load(uri)
				.placeholder(R.drawable.ic_contact_picture)
				.into(imageView);

				return true;
			}
		});
		
		userListView.setAdapter(userSimpleCursorAdapter);
		
		getSupportLoaderManager().initLoader(0, null, this);
	}
	
	@OnClick(R.id.b_start) void start() {
		FindNearbyActivity.start(this, viaBluetoothCheckbox.isChecked(), viaServerCheckbox.isChecked());
	}
	
	@Override
	public Loader<Cursor> onCreateLoader(int loaderId, Bundle args)
	{
		CursorLoader loader = new CursorLoader
				( this
				, ContactsContract.Profile.CONTENT_URI
				, null
				, null
				, null
				, null
				);
		return loader; 
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor)
	{
		userSimpleCursorAdapter.swapCursor(cursor);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> arg0)
	{
		userSimpleCursorAdapter.swapCursor(null);
	}	
}
