package com.hivewallet.androidclient.wallet.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.hivewallet.androidclient.wallet.util.FindNearbyWorker;
import com.hivewallet.androidclient.wallet.util.FindNearbyWorker.FindNearbyContact;
import com.hivewallet.androidclient.wallet_test.R;
import com.squareup.picasso.Picasso;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class FindNearbyActivity extends FragmentActivity implements LoaderCallbacks<Cursor>, Callback
{
	private static final String TAG = "com.example.hive_mockup1";
	private static final int REQUEST_ENABLE_BLUETOOTH = 0;
	
	private static final int RECHECK_INTERVAL = 10;	/* seconds */
	private static final int DISCOVERY_COOLOFF_INTERVAL = 3; /* seconds */
	private static final int DISCOVERY_COOLOFF_RANDOM_ADDITIONAL_INTERVAL = 3; /* seconds */ 
	
	private BluetoothAdapter bluetoothAdapter;
	private boolean readyForDiscovery = false;
	private boolean shouldBeVisible = false;
	private boolean workerIsBusy = false;
	private boolean activityIsActive = false;
	private long discoveryCooloffTimestamp = 0;

	private Map<String, Long> seenCandidates = new HashMap<String, Long>();
	private Set<String> successfulCandidates = new HashSet<String>();

	private ContentResolver contentResolver;
	private Handler handler;
	private FindNearbyWorker findNearbyWorker = null;
	
	private SimpleCursorAdapter userSimpleCursorAdapter;
	private ArrayAdapter<FindNearbyContact> arrayAdapter;
	
	private ListView userListView;
	private ListView nearbyListView;
	private Button visibilityButton = null;
	
	private Random rnd = new Random();
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.find_nearby_activity);

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
				
				Picasso.with(FindNearbyActivity.this)
					.load(uri)
					.placeholder(R.drawable.ic_contact_picture)
					.into(imageView);
				
				return true;
			}
		});
		
		userListView = (ListView)findViewById(R.id.lv_user);
		userListView.setAdapter(userSimpleCursorAdapter);
		
		arrayAdapter = new FindNearbyAdapter(this, R.layout.find_nearby_contacts_list_item); 
		
		nearbyListView = (ListView)findViewById(R.id.lv_nearby);
		TextView emptyNearbyTextView = (TextView)findViewById(R.id.tv_empty_nearby);
		nearbyListView.setEmptyView(emptyNearbyTextView);
		nearbyListView.setAdapter(arrayAdapter);
		
		visibilityButton = (Button)findViewById(R.id.b_visibility);
		visibilityButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				prepareVisibility();
			}
		});
		
		handler = new Handler(this);
		
		getSupportLoaderManager().initLoader(0, null, this);
		
		contentResolver = getContentResolver();
		
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null) {
			Toast.makeText(this, R.string.bluetooth_unavailable, Toast.LENGTH_LONG).show();
			finish();
		}
	}
	
	@Override
	protected void onStart()
	{
		super.onStart();
		
		if (bluetoothAdapter.isEnabled()) {
			readyForDiscovery = true;
		} else {
			// ask user to enable Bluetooth - we will set 'readyForDiscovery' when we
			// come back from that
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode) {
			case REQUEST_ENABLE_BLUETOOTH:
				if (resultCode == Activity.RESULT_OK) {
					readyForDiscovery = true;
				} else {
					finish();
				}
				break;
		}
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		activityIsActive = true;
		
        // register for Bluetooth broadcasts
		IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        this.registerReceiver(receiver, filter);
        
        // setup worker
        if (findNearbyWorker == null) {
        	findNearbyWorker = new FindNearbyWorker(bluetoothAdapter, contentResolver, handler, "1AxQN2njHDnYbHBj9PPiW4b1eJhMBuq7Gm");
			findNearbyWorker.start();
        }
        
        manageDiscovery();
        maybeStartVisibility();
	}
	
	@Override
	protected void onPause()
	{
		stopVisibility();
		stopDiscovery();
		
		// unregister from broadcasts
		this.unregisterReceiver(receiver);
		
		if (findNearbyWorker != null) {
			findNearbyWorker.shutdown();
			findNearbyWorker = null;
		}
	
		activityIsActive = false;
		super.onPause();
	}
	
	private void manageDiscovery()
	{
		boolean cooloff = discoveryCooloffTimestamp != 0
				&& System.currentTimeMillis() < discoveryCooloffTimestamp;
		boolean shouldBeDiscovering = activityIsActive && readyForDiscovery
				&& !workerIsBusy && !cooloff;
		
		if (!bluetoothAdapter.isDiscovering() && shouldBeDiscovering) {
			Log.d(TAG, "Start of Bluetooth discovery");
			bluetoothAdapter.startDiscovery();
		} else if (bluetoothAdapter.isDiscovering() && !shouldBeDiscovering) {
			Log.d(TAG, "Stop of Bluetooth discovery");
			bluetoothAdapter.cancelDiscovery();
		}
	}
	
	private void stopDiscovery()
	{
		if (!bluetoothAdapter.isDiscovering())
			return;
		
		Log.d(TAG, "Stop of Bluetooth discovery");
		bluetoothAdapter.cancelDiscovery();
	}
	
	private void prepareVisibility()
	{
		if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
		}
		
		shouldBeVisible = true;
		maybeStartVisibility();
	}
	
	private void maybeStartVisibility()
	{
		if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
			return;
		
		adjustVisibilityButton();
		if (shouldBeVisible)
			findNearbyWorker.becomeVisible();
	}
	
	private void stopVisibility()
	{
		if (findNearbyWorker == null)
			return;
		
		findNearbyWorker.becomeInvisible();
	}
	
	private void adjustVisibilityButton()
	{
		if (visibilityButton == null || bluetoothAdapter == null)
			return;
		
		boolean buttonState = visibilityButton.isEnabled();
		boolean appState = bluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
				&& shouldBeVisible; 
		
		/* Button and adapter need to be in opposite states */
		if (buttonState == appState)
			visibilityButton.setEnabled(!appState);
	}
	
	private void enterDiscoveryCooloff()
	{
		long timestamp = System.currentTimeMillis() + (DISCOVERY_COOLOFF_INTERVAL * 1000);
		timestamp += rnd.nextInt(DISCOVERY_COOLOFF_RANDOM_ADDITIONAL_INTERVAL * 1000);
		
		discoveryCooloffTimestamp = timestamp;
	}
	
	private void addCandidate(String address)
	{
		/* don't check candidates that we already connected to */
		if (successfulCandidates.contains(address))
			return;
		
		/* don't attempt a connection again right away */
		Long lastCheck = seenCandidates.get(address);
		if (lastCheck != null && System.currentTimeMillis() - lastCheck < RECHECK_INTERVAL * 1000)
			return;
		
		/* valid candidate - pass on to worker */
		workerIsBusy = true;	// worker will send us a non-busy message again eventually
		manageDiscovery();
		seenCandidates.put(address, System.currentTimeMillis());
		findNearbyWorker.addCandidate(address);
	}
	
	private final BroadcastReceiver receiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			Log.d(TAG, "BroadcastReceiver: " + action);
			
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				Log.d(TAG, "Candidate discovered: " + device.getName() + " (" + device.getAddress() + ")");
				addCandidate(device.getAddress());
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				// take a pause from discovery
				enterDiscoveryCooloff();
			} else if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
				adjustVisibilityButton();
			}
		}
	};

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

	@Override
	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
			case FindNearbyWorker.MESSAGE_IS_BUSY:
				workerIsBusy = true;
				manageDiscovery();
				return true;
			case FindNearbyWorker.MESSAGE_IS_NOT_BUSY:
				workerIsBusy = false;
				manageDiscovery();
				return true;
			case FindNearbyWorker.MESSAGE_INFO_RECEIVED:
				FindNearbyContact contact = (FindNearbyContact)msg.obj;
				successfulCandidates.add(contact.getBluetoothAddress());
				arrayAdapter.add(contact);
			case FindNearbyWorker.MESSAGE_HEARTBEAT:
				manageDiscovery(); /* use heartbeat to check/update discovery operation */
			default:
				return false;
		}
	}
}