package com.hivewallet.androidclient.wallet.ui;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import butterknife.OnTextChanged;

import com.hivewallet.androidclient.wallet.Configuration;
import com.hivewallet.androidclient.wallet.WalletApplication;
import com.hivewallet.androidclient.wallet_test.R;
import com.squareup.picasso.Picasso;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;

public class FindNearbyPrepActivity extends FragmentActivity
{
	private static final int REQUEST_CODE_PICK_PHOTO = 0;
	
	private Configuration configuration;
	
	@InjectView(R.id.cb_via_bluetooth) CheckBox viaBluetoothCheckbox;
	@InjectView(R.id.cb_via_server) CheckBox viaServerCheckbox;
	@InjectView(R.id.iv_user_photo) ImageView userPhotoImageView;
	@InjectView(R.id.et_user_name) EditText userNameEditText;
	
	private String userName = null;
	private Uri userPhotoUri = null;
	
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
		
		int checkResult = this.checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
		boolean hasLocationPermission = checkResult == PackageManager.PERMISSION_GRANTED;
		LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		if (locationManager == null || !hasLocationPermission) {
			/* no GPS available */
			viaServerCheckbox.setChecked(false);
			viaServerCheckbox.setEnabled(false);
		}
		
		WalletApplication application = (WalletApplication)getApplication();
		configuration = application.getConfiguration();
		loadUserProfile();
		saveUserName();	// write back default name if we are initializing
		updateView();
	}
	
	private void loadUserProfile() {
		String defaultUserName = getResources().getString(R.string.find_nearby_default_user_name);
		userName = configuration.getFindNearbyUserName(defaultUserName);
		userPhotoUri = configuration.getFindNearbyUserPhoto();
	}
	
	private void saveUserPhoto() {
		configuration.setFindNearbyUserPhoto(userPhotoUri);
	}
	
	private void saveUserName() {
		configuration.setFindNearbyUserName(userName);
	}
	
	private void updateView() {
		Picasso
			.with(this)
			.load(userPhotoUri)
			.placeholder(R.drawable.ic_contact_picture)
			.into(userPhotoImageView);
		
		String displayedUserName = userNameEditText.getText().toString();
		if (!userName.equals(displayedUserName)) {
			userNameEditText.setText(userName);
			userNameEditText.setSelection(userName.length());
		}
	}
	
	@OnClick(R.id.b_start) void start() {
		FindNearbyActivity.start(this, viaBluetoothCheckbox.isChecked(), viaServerCheckbox.isChecked());
		finish();
	}
	
	@OnClick(R.id.iv_user_photo) void handlePickPhoto() {
		if (userPhotoUri == null) {
			Intent imageIntent = new Intent();
			imageIntent.setType("image/*");
			imageIntent.setAction(Intent.ACTION_GET_CONTENT);
			
			Intent choiceIntent = Intent.createChooser(imageIntent,
					getString(R.string.find_nearby_user_photo_selection));
			
			startActivityForResult(choiceIntent, REQUEST_CODE_PICK_PHOTO);
		} else {
			userPhotoUri = null;
			saveUserPhoto();
			updateView();
		}
	}
	
	@OnTextChanged(value = R.id.et_user_name) void handleUserNameChange() {
		userName = userNameEditText.getText().toString();
		saveUserName();
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (requestCode == REQUEST_CODE_PICK_PHOTO && resultCode == Activity.RESULT_OK) {
			userPhotoUri = data.getData();
			saveUserPhoto();
			updateView();
		}
	}	
}
