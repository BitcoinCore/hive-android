package com.hivewallet.androidclient.wallet.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import butterknife.ButterKnife;
import butterknife.InjectView;

import com.google.bitcoin.core.Address;
import com.hivewallet.androidclient.wallet.AddressBookProvider;
import com.hivewallet.androidclient.wallet.Configuration;
import com.hivewallet.androidclient.wallet.WalletApplication;
import com.hivewallet.androidclient.wallet.util.FindNearbyContact;
import com.hivewallet.androidclient.wallet.util.FindNearbyBluetoothWorker;
import com.hivewallet.androidclient.wallet.util.FindNearbyGPSWorker;
import com.hivewallet.androidclient.wallet_test.R;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.widget.ListView;
import android.widget.TextView;

public class FindNearbyActivity extends FragmentActivity implements Callback
{
	private static final int REQUEST_DISCOVERABLE = 0;
	
	private static final int RECHECK_INTERVAL = 10;	/* seconds */
	private static final int DISCOVERY_COOLOFF_INTERVAL = 3; /* seconds */
	private static final int DISCOVERY_COOLOFF_RANDOM_ADDITIONAL_INTERVAL = 3; /* seconds */
	
	private static final String USE_BLUETOOTH = "use_bluetooth";
	private static final String USE_SERVER = "use_server";
	
	public static void start(Context context, boolean viaBluetooth, boolean viaServer) {
		final Intent intent = new Intent(context, FindNearbyActivity.class);
		intent.putExtra(USE_BLUETOOTH, viaBluetooth);
		intent.putExtra(USE_SERVER, viaServer);
		context.startActivity(intent);
	}
	
	private boolean useServer = false;
	
	private BluetoothAdapter bluetoothAdapter;
	private boolean useBluetooth = false;
	private boolean bluetoothReady = false;
	private boolean workerIsBusy = false;
	private boolean activityIsActive = false;
	private long discoveryCooloffTimestamp = 0;

	private Map<String, Long> seenCandidates = new HashMap<String, Long>();
	private Set<String> successfulCandidates = new HashSet<String>();

	private ContentResolver contentResolver;
	private Configuration configuration;
	private Handler handler;
	private FindNearbyBluetoothWorker findNearbyBluetoothWorker = null;
	private FindNearbyGPSWorker findNearbyGPSWorker = null;
	
	private FindNearbyAdapter arrayAdapter;
	
	@InjectView(R.id.tv_status) TextView statusTextView;
	@InjectView(R.id.lv_nearby) ListView nearbyListView;
	@InjectView(R.id.tv_empty_nearby) TextView emptyNearbyTextView;
	
	private Random rnd = new Random();
	
	private WalletApplication application;
	private Address bitcoinAddress;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		Bundle extras = null;
		if (getIntent() != null)
			extras = getIntent().getExtras();
		
		if (extras != null) {
			useBluetooth = extras.getBoolean(USE_BLUETOOTH);
			useServer = extras.getBoolean(USE_SERVER);
		}
		
		setContentView(R.layout.find_nearby_activity);
		ButterKnife.inject(this);

		arrayAdapter = new FindNearbyAdapter(
				this, getSupportFragmentManager(), R.layout.find_nearby_contacts_list_item); 
		nearbyListView.setEmptyView(emptyNearbyTextView);
		nearbyListView.setAdapter(arrayAdapter);
		
		handler = new Handler(this);
		
		contentResolver = getContentResolver();
		
		application = (WalletApplication)getApplication();
		configuration = application.getConfiguration();
		bitcoinAddress = application.determineSelectedAddress();
		
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null) {
			useBluetooth = false;
		}
	}
	
	@Override
	protected void onStart()
	{
		super.onStart();
		
		if (useBluetooth) {
			if (bluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
				bluetoothReady = true;
			} else {
				// ask user to enable Bluetooth and become visible - we will set 'bluetoothReady'
				// when we come back from that
				Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
	            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
	            startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE);
			}
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode) {
			case REQUEST_DISCOVERABLE:
				if (resultCode > 0)	/* duration of visibility will be provided */
					bluetoothReady = true;
				break;
		}
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		activityIsActive = true;
		
		// watch address book
        contentResolver.registerContentObserver(AddressBookProvider.contentUri(getPackageName()), true, addressBookObserver);
		
		if (useBluetooth) {
	        // register for Bluetooth broadcasts
			IntentFilter filter = new IntentFilter();
			filter.addAction(BluetoothDevice.ACTION_FOUND);
			filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
			filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
	        this.registerReceiver(receiver, filter);
        
	        // setup worker
	        if (findNearbyBluetoothWorker == null) {
	        	findNearbyBluetoothWorker = new FindNearbyBluetoothWorker(application, bluetoothAdapter, contentResolver, configuration, handler, bitcoinAddress.toString());
				findNearbyBluetoothWorker.start();
	        }
				
	        manageDiscovery();
	        startVisibility();
		}
		
		if (useServer) {
			if (findNearbyGPSWorker == null) {
				findNearbyGPSWorker = new FindNearbyGPSWorker(application, configuration, handler, bitcoinAddress.toString());
				findNearbyGPSWorker.start();
			}
		}
	}
	
	@Override
	protected void onPause()
	{
		if (useServer) {
			if (findNearbyGPSWorker != null) {
				findNearbyGPSWorker.shutdown();
				findNearbyGPSWorker = null;
			}
		}
		
		if (useBluetooth) {
			stopVisibility();
			stopDiscovery();
			
			// unregister from broadcasts
			this.unregisterReceiver(receiver);
			
			if (findNearbyBluetoothWorker != null) {
				findNearbyBluetoothWorker.shutdown();
				findNearbyBluetoothWorker = null;
			}
		}
		
		contentResolver.unregisterContentObserver(addressBookObserver);
	
		activityIsActive = false;
		super.onPause();
	}
	
	private void manageDiscovery()
	{
		if (!useBluetooth)
			return;
		
		boolean cooloff = discoveryCooloffTimestamp != 0
				&& System.currentTimeMillis() < discoveryCooloffTimestamp;
		boolean shouldBeDiscovering = activityIsActive && bluetoothReady
				&& !workerIsBusy && !cooloff;
		
		if (!bluetoothAdapter.isDiscovering() && shouldBeDiscovering) {
			statusTextView.setText(getString(R.string.searching_via_bluetooth));
			bluetoothAdapter.startDiscovery();
		} else if (bluetoothAdapter.isDiscovering() && !shouldBeDiscovering) {
			bluetoothAdapter.cancelDiscovery();
		}
	}
	
	private void stopDiscovery()
	{
		if (!useBluetooth)
			return;
		
		if (!bluetoothAdapter.isDiscovering())
			return;
		
		bluetoothAdapter.cancelDiscovery();
	}
	
	private void startVisibility()
	{
		if (!useBluetooth)
			return;
		
		if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
			return;
		
		findNearbyBluetoothWorker.becomeVisible();
	}
	
	private void stopVisibility()
	{
		if (findNearbyBluetoothWorker == null)
			return;
		
		findNearbyBluetoothWorker.becomeInvisible();
	}
	
	private void enterDiscoveryCooloff()
	{
		long timestamp = System.currentTimeMillis() + (DISCOVERY_COOLOFF_INTERVAL * 1000);
		timestamp += rnd.nextInt(DISCOVERY_COOLOFF_RANDOM_ADDITIONAL_INTERVAL * 1000);
		
		discoveryCooloffTimestamp = timestamp;
	}
	
	private void addCandidate(String name, String address)
	{
		/* don't check candidates that we already connected to */
		if (successfulCandidates.contains(address))
			return;
		
		/* don't attempt a connection again right away */
		Long lastCheck = seenCandidates.get(address);
		if (lastCheck != null && System.currentTimeMillis() - lastCheck < RECHECK_INTERVAL * 1000)
			return;
		
		/* valid candidate - pass on to worker */
		statusTextView.setText(getString(R.string.find_nearby_connecting, name));
		workerIsBusy = true;	// worker will send us a non-busy message again eventually
		manageDiscovery();
		seenCandidates.put(address, System.currentTimeMillis());
		findNearbyBluetoothWorker.addCandidate(address);
	}
	
	private final BroadcastReceiver receiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				addCandidate(device.getName(), device.getAddress());
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				// take a pause from discovery
				enterDiscoveryCooloff();
			}
		}
	};

	@Override
	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
			case FindNearbyBluetoothWorker.MESSAGE_IS_BUSY:
				workerIsBusy = true;
				manageDiscovery();
				return true;
			case FindNearbyBluetoothWorker.MESSAGE_IS_NOT_BUSY:
				workerIsBusy = false;
				manageDiscovery();
				return true;
			case FindNearbyBluetoothWorker.MESSAGE_INFO_RECEIVED:
				FindNearbyContact contact = (FindNearbyContact)msg.obj;
				successfulCandidates.add(contact.getBluetoothAddress());
				arrayAdapter.maybeAdd(contact);
				return true;
			case FindNearbyBluetoothWorker.MESSAGE_HEARTBEAT:
				manageDiscovery(); /* use heartbeat to check/update discovery operation */
				return true;
			case FindNearbyGPSWorker.MESSAGE_IS_SEARCHING:
				statusTextView.setText(getString(R.string.searching_via_server));
				return true;
			case FindNearbyGPSWorker.MESSAGE_INFO_RECEIVED:
				FindNearbyContact contact2 = (FindNearbyContact)msg.obj;
				arrayAdapter.maybeAdd(contact2);
				return true;
			default:
				return false;
		}
	}
	
	private final ContentObserver addressBookObserver = new ContentObserver(handler)
	{
		@Override
		public void onChange(final boolean selfChange)
		{
			runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					arrayAdapter.clearAddressBookCache();
				}
			});
		}
	};	
}