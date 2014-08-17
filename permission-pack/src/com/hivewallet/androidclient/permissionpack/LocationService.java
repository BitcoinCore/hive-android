package com.hivewallet.androidclient.permissionpack;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public class LocationService extends Service implements Callback
{
	public static final String PACKAGE_NAME = "com.hivewallet.androidclient.permissionpack";
	public static final String ACTION_START = "com.hivewallet.androidclient.permissionpack.LocationService.action.START";
	public static final String KEY_IS_AVAILABLE = "is_available";
	public static final String KEY_ERROR = "error";
	
	public static final int MSG_START = 0;
	public static final int MSG_STATUS = 1;
	public static final int MSG_LOCATION = 2;
	
	private static final int LOCATION_INTERVAL = 300;
	private static final int TWO_MINUTES = 2 * 60 * 1000;
	
	private LocationManager locationManager;
	private Location currentBestLocation;
	
	private Handler myHandler;
	private Messenger myMessenger;
	private Messenger theirMessenger;
	
	@Override
	public void onCreate()
	{
		super.onCreate();
		
		this.myHandler = new Handler(this);
		this.myMessenger = new Messenger(this.myHandler);
	}
	
	@Override
	public IBinder onBind(Intent intent)
	{
		return myMessenger.getBinder();
	}
	
	@Override
	public boolean onUnbind(Intent intent)
	{
		if (locationManager != null)
			locationManager.removeUpdates(locationListener);
		
		stopSelf();
		return false;
	}

	@Override
	public boolean handleMessage(Message msg)
	{
		try {
			switch (msg.what) {
				case MSG_START:
					theirMessenger = msg.replyTo;
					LocationServiceStatus status = initLocationManager();
					Bundle statusBundle = new Bundle();
					
					statusBundle.putBoolean(KEY_IS_AVAILABLE, status.isAvailable());
					statusBundle.putString(KEY_ERROR, status.isAvailable() ? "" : status.getError());
					
					Message reply = Message.obtain(null, MSG_STATUS);
					reply.obj = statusBundle;
					theirMessenger.send(reply);
					return true;
				default:
					return false;
			}
		} catch (RemoteException ignored) {
			return false;
		}
	}
	
	private LocationServiceStatus initLocationManager() {
		locationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
		if (locationManager == null) {
			return LocationServiceStatus.serviceUnavailable("Location manager not available");
		}
		
		boolean hasAtLeastOneProvider = false;
		try {
			locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, 0, locationListener);
			hasAtLeastOneProvider = true;
		} catch (IllegalArgumentException discarded) {}
		try {
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, 0, locationListener);
			hasAtLeastOneProvider = true;
		} catch (IllegalArgumentException discarded) {}
		
		if (!hasAtLeastOneProvider) {
			return LocationServiceStatus.serviceUnavailable("Location manager has no suitable providers");
		}
		
		return LocationServiceStatus.serviceAvailable();
	}
	
	private LocationListener locationListener = new LocationListener()
	{
		@Override
		public void onLocationChanged(Location location)
		{
			if (isBetterLocation(location, currentBestLocation))
				currentBestLocation = location;
			
			try {
				if (theirMessenger != null) {
					Message update = Message.obtain(null, MSG_LOCATION);
					update.obj = location;
					theirMessenger.send(update);
				}
			} catch (RemoteException ignored) {}
		}
		
		@Override
		public void onStatusChanged(String provider, int status, Bundle extras)
		{
			/* do nothing */			
		}
		
		@Override
		public void onProviderEnabled(String provider)
		{
			/* do nothing */
		}
		
		@Override
		public void onProviderDisabled(String provider)
		{
			/* do nothing */
		}
	};
	
	/** Determines whether one Location reading is better than the current Location fix
	  * @param location  The new Location that you want to evaluate
	  * @param currentBestLocation  The current Location fix, to which you want to compare the new one
	  */
	private static boolean isBetterLocation(Location location, Location currentBestLocation) {
	    if (currentBestLocation == null) {
	        // A new location is always better than no location
	        return true;
	    }

	    // Check whether the new location fix is newer or older
	    long timeDelta = location.getTime() - currentBestLocation.getTime();
	    boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
	    boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
	    boolean isNewer = timeDelta > 0;

	    // If it's been more than two minutes since the current location, use the new location
	    // because the user has likely moved
	    if (isSignificantlyNewer) {
	        return true;
	    // If the new location is more than two minutes older, it must be worse
	    } else if (isSignificantlyOlder) {
	        return false;
	    }

	    // Check whether the new location fix is more or less accurate
	    int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
	    boolean isLessAccurate = accuracyDelta > 0;
	    boolean isMoreAccurate = accuracyDelta < 0;
	    boolean isSignificantlyLessAccurate = accuracyDelta > 200;

	    // Check if the old and new location are from the same provider
	    boolean isFromSameProvider = isSameProvider(location.getProvider(),
	            currentBestLocation.getProvider());

	    // Determine location quality using a combination of timeliness and accuracy
	    if (isMoreAccurate) {
	        return true;
	    } else if (isNewer && !isLessAccurate) {
	        return true;
	    } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
	        return true;
	    }
	    return false;
	}

	/** Checks whether two providers are the same */
	private static boolean isSameProvider(String provider1, String provider2) {
	    if (provider1 == null) {
	      return provider2 == null;
	    }
	    return provider1.equals(provider2);
	}	

	public static class LocationServiceStatus {
		private boolean isAvailable;
		private String error;
		
		private LocationServiceStatus(boolean isAvailable, String error)
		{
			this.isAvailable = isAvailable;
			this.error = error;
		}
		
		public boolean isAvailable()
		{
			return isAvailable;
		}
		
		public String getError()
		{
			return error;
		}
		
		public static LocationServiceStatus serviceAvailable() {
			return new LocationServiceStatus(true, null);
		}
		
		public static LocationServiceStatus serviceUnavailable(String error) {
			return new LocationServiceStatus(false, error);
		}
	}
}
