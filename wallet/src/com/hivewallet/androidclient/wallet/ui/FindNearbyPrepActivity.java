package com.hivewallet.androidclient.wallet.ui;

import java.io.FileNotFoundException;
import java.io.IOException;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import butterknife.OnTextChanged;

import com.hivewallet.androidclient.permissionpack.LocationService;
import com.hivewallet.androidclient.wallet.AddressBookProvider;
import com.hivewallet.androidclient.wallet.Configuration;
import com.hivewallet.androidclient.wallet.WalletApplication;
import com.hivewallet.androidclient.wallet.R;
import com.squareup.picasso.Picasso;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.text.Html;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

public class FindNearbyPrepActivity extends FragmentActivity
{
	private static final int REQUEST_CODE_PICK_PHOTO = 0;
	
	private Configuration configuration;
	private ContentResolver contentResolver;
	
	@InjectView(R.id.cb_via_bluetooth) CheckBox viaBluetoothCheckbox;
	@InjectView(R.id.cb_via_server) CheckBox viaServerCheckbox;
	@InjectView(R.id.iv_user_photo) ImageView userPhotoImageView;
	@InjectView(R.id.et_user_name) EditText userNameEditText;
	@InjectView(R.id.tv_permission_pack) TextView permissionPackTextView;
	
	private String userName = null;
	private Uri userPhotoUri = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		contentResolver = getContentResolver();
		
		setContentView(R.layout.find_nearby_prep_activity);
		ButterKnife.inject(this);
		
		WalletApplication application = (WalletApplication)getApplication();
		configuration = application.getConfiguration();
		loadUserProfile();
		saveUserName();	// write back default name if we are initializing
		
		updateView();
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		
		if (BluetoothAdapter.getDefaultAdapter() != null) {
			viaBluetoothCheckbox.setEnabled(true);
		} else {
			viaBluetoothCheckbox.setChecked(false);
			viaBluetoothCheckbox.setEnabled(false);
		}
		
		if (isLocationServiceAvailable()) {
			viaServerCheckbox.setEnabled(true);
			permissionPackTextView.setVisibility(View.GONE);
		} else {
			viaServerCheckbox.setChecked(false);
			viaServerCheckbox.setEnabled(false);
			permissionPackTextView.setText(Html.fromHtml(getString(R.string.find_nearby_permission_pack)));
			permissionPackTextView.setVisibility(View.VISIBLE);
		}
	}
	
	private boolean isLocationServiceAvailable() {
		PackageManager pm = getPackageManager();
		try {
			pm.getPackageInfo(LocationService.PACKAGE_NAME, PackageManager.GET_SERVICES);
			return true;
		} catch (PackageManager.NameNotFoundException e) {
			return false;
		}
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
	
	@OnClick(R.id.tv_permission_pack) void handlePermissionPackHint() {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse("market://details?id=" + LocationService.PACKAGE_NAME));
		startActivity(intent);
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
			Uri uri = data.getData();
			Bitmap bitmap = null;
			try {
				bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri);
			} catch (FileNotFoundException ignored) {
			} catch (IOException ignored) { 
			}
			
			if (bitmap != null) {
				userPhotoUri = AddressBookProvider.storeBitmap(this, bitmap);
			} else {
				userPhotoUri = null;
			}
			
			if (userPhotoUri != null)
				AddressBookProvider.setPhotoAssetAsPermanent(this, userPhotoUri);

			saveUserPhoto();
			updateView();
		}
	}	
}
