package com.hivewallet.androidclient.wallet.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivewallet.androidclient.wallet.Configuration;
import com.hivewallet.androidclient.wallet.Constants;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public class FindNearbyGPSWorker extends Thread implements LocationListener
{
	private static final Logger log = LoggerFactory.getLogger(FindNearbyGPSWorker.class);
	
	public static final int MESSAGE_IS_SEARCHING = 1000;
	public static final int MESSAGE_INFO_RECEIVED = 1001;
	
	private static final String HIVE_SERVER = "https://web.hivewallet.com/location";
	private static final String JSON_FIELD_NETWORK = "network";
	private static final String JSON_FIELD_LATITUDE = "lat";
	private static final String JSON_FIELD_LONGITUDE = "lon";
	private static final String BITCOIN_NETWORK = "bitcoin";
	private static final int SEARCH_INTERVAL = 8 * 1000;
	private static final int LOCATION_INTERVAL = 300;
	private static final int TWO_MINUTES = 2 * 60 * 1000;
	private static final int BUFFER_SIZE = 4096;
	
	private final Context context;
	private final Configuration configuration;
	private final Handler parentHandler;
	private final CookieManager cookieManager;
	private final URL hiveServer;
	
	private Handler handler;
	
	private FindNearbyContact userRecord;
	private String bitcoinAddress;
	
	private LocationManager locationManager;
	private Location currentBestLocation = null;
	private long lastActivityTimestamp = 0;
	
	public FindNearbyGPSWorker(Context context, Configuration configuration, Handler parentHandler, String bitcoinAddress)
	{
		this.context = context;
		this.configuration = configuration;
		this.parentHandler = parentHandler;
		this.bitcoinAddress = bitcoinAddress;
		
		this.cookieManager = new CookieManager();
		CookieHandler.setDefault(cookieManager);
		
		try {
			hiveServer = new URL(HIVE_SERVER);
		} catch (MalformedURLException e) { throw new RuntimeException(e); }
	}
	
	@Override
	public void run()
	{
		Looper.prepare();
		handler = new Handler();
		
		userRecord = FindNearbyContact.lookupUserRecord(context.getContentResolver(), configuration, bitcoinAddress);
		if (userRecord == null) {
			log.warn("Unable to lookup user details in preparation of Hive geo server search - GPS worker is shutting down.");
			return;
		}
		
		locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
		if (locationManager == null) {
			log.warn("Location manager not available - GPS worker is shutting down.");
			return;
		}
		
		boolean hasAtLeastOneProvider = false;
		try {
			locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, 0, this);
			hasAtLeastOneProvider = true;
		} catch (IllegalArgumentException discarded) {}
		try {
			locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, 0, this);
			hasAtLeastOneProvider = true;
		} catch (IllegalArgumentException discarded) {}
		
		if (!hasAtLeastOneProvider) {
			log.warn("Location manager has no suitable providers - GPS worker is shutting down.");
			return;
		}
		
		Looper.loop();
	}

	private Runnable shutdownRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			if (locationManager != null)
				locationManager.removeUpdates(FindNearbyGPSWorker.this);
			unregister();
			handler.getLooper().quit(); 
		}
	};
	
	public void shutdown() {
		handler.removeCallbacksAndMessages(null);
		handler.post(shutdownRunnable);
	}
	
	private Runnable registerAndSearchRunnable = new Runnable()
	{
		@Override
		public void run() { registerAndSearch(); }
	};
	
	private void registerAndSearch() {
		if (currentBestLocation == null)
			return;
		
		if (System.currentTimeMillis() - lastActivityTimestamp < SEARCH_INTERVAL) {
			/* do not hit the server too often - try again in a little while */
			handler.removeCallbacks(registerAndSearchRunnable);
			handler.postDelayed(registerAndSearchRunnable, SEARCH_INTERVAL);
			return;
		}
		
		register();
		search();
	}
	
	private JSONObject prepareJSONRequest() {
		if (userRecord == null)
			return null;
		
		JSONObject userJSONObject = null;
		try {
			userJSONObject = userRecord.toJSONObject();
			userJSONObject.put(JSON_FIELD_NETWORK, BITCOIN_NETWORK);
		} catch (JSONException e) {
			log.warn("Unable to serialize user details to JSON");
			return null;
		}
		
		try	{
			userJSONObject.put(JSON_FIELD_LATITUDE, currentBestLocation.getLatitude());
			userJSONObject.put(JSON_FIELD_LONGITUDE, currentBestLocation.getLongitude());
		} catch (JSONException e) {
			log.warn("Unable to serialize user position to JSON");
			return null;
		}
		
		return userJSONObject;
	}
	
	private void search() {
		JSONObject userJSONObject = prepareJSONRequest();
		if (userJSONObject == null)
			return;
		
		log.info("Searching via Hive server...");
		lastActivityTimestamp = System.currentTimeMillis();
		String jsonRequest = userJSONObject.toString();
		
		reportSearching();
		HttpURLConnection conn = null;
		OutputStream outStream = null;
		InputStream inStream = null;
		try
		{
			conn = (HttpURLConnection)hiveServer.openConnection();
			conn.setRequestMethod("PUT");
			conn.setDoInput(true);
			conn.setDoOutput(true);
			conn.setFixedLengthStreamingMode(jsonRequest.length());
			conn.setRequestProperty("Content-Type", "application/json");
			
			outStream = conn.getOutputStream();
			outStream.write(jsonRequest.getBytes(Constants.US_ASCII));
			outStream.flush();
			outStream.close();
			
			if (conn.getResponseCode() != HttpURLConnection.HTTP_OK)
				throw new IOException("Response code: " + conn.getResponseCode()
						+ " - " + conn.getResponseMessage());			
			
			inStream = conn.getInputStream();
			byte[] buffer = new byte[BUFFER_SIZE];
			int count;
			StringBuilder stringBuilder = new StringBuilder();
			while ((count = inStream.read(buffer)) > 0) {
				stringBuilder.append(new String(buffer, 0, count, Constants.US_ASCII));
			}
			
			String jsonReply = stringBuilder.toString();
			JSONArray jsonArray = new JSONArray(jsonReply);
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONArray jsonSubArray = jsonArray.getJSONArray(i);
				JSONObject jsonObject = jsonSubArray.getJSONObject(0);
				FindNearbyContact contact = FindNearbyContact.fromJSONObject(jsonObject);
				reportReceivedContact(contact);
			}
			
			log.info("Received {} result(s) from Hive geo server", jsonArray.length());
		} catch (IOException e)	{
			log.warn("Unable to contact Hive geo server", e);
		} catch (JSONException e) {
			log.warn("Unable to parse JSON from Hive geo server", e);
		} finally {
			if (conn != null)
				conn.disconnect();
			if (outStream != null)
				try { outStream.close(); } catch (IOException ignored) { }
			if (inStream != null)
				try { inStream.close(); } catch (IOException ignored) { }
		}
	}
	
	private void register() {
		JSONObject userJSONObject = prepareJSONRequest();
		if (userJSONObject == null)
			return;
		
		log.info("Registering with Hive server...");
		lastActivityTimestamp = System.currentTimeMillis();
		String jsonRequest = userJSONObject.toString();
		
		HttpURLConnection conn = null;
		OutputStream outStream = null;
		try
		{
			conn = (HttpURLConnection)hiveServer.openConnection();
			conn.setDoOutput(true);
			conn.setFixedLengthStreamingMode(jsonRequest.length());
			conn.setRequestProperty("Content-Type", "application/json");
			
			outStream = conn.getOutputStream();
			outStream.write(jsonRequest.getBytes(Constants.US_ASCII));
			outStream.flush();
			outStream.close();
			
			if (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED)
				throw new IOException("Response code: " + conn.getResponseCode()
						+ " - " + conn.getResponseMessage());
			
			log.info("Successfully registered with Hive geo server");
		} catch (IOException e)	{
			log.warn("Unable to contact Hive geo server", e);
		} finally {
			if (conn != null)
				conn.disconnect();
			if (outStream != null)
				try { outStream.close(); } catch (IOException ignored) { }
		}
	}
	
	private void unregister() {
		HttpURLConnection conn = null;
		try
		{
			conn = (HttpURLConnection)hiveServer.openConnection();
			conn.setRequestMethod("DELETE");
			
			if (conn.getResponseCode() != HttpURLConnection.HTTP_OK)
				throw new IOException("Response code: " + conn.getResponseCode()
						+ " - " + conn.getResponseMessage());			
		} catch (IOException e)	{
			log.warn("Unable to unregister from Hive geo server", e);
		} finally {
			if (conn != null)
				conn.disconnect();
		} 
	}
	
	private void reportSearching()
	{
		Message msg = parentHandler.obtainMessage(MESSAGE_IS_SEARCHING);
		parentHandler.sendMessage(msg);
	}
	
	private void reportReceivedContact(FindNearbyContact contact)
	{
		/* prepare contact photo uri, if present, before passing it on to the UI */
		contact.preparePhotoUri(context);
		
		Message msg = parentHandler.obtainMessage(MESSAGE_INFO_RECEIVED);
		msg.obj = contact;
		parentHandler.sendMessage(msg);
	}	

	@Override
	public void onLocationChanged(Location location)
	{
		if (isBetterLocation(location, currentBestLocation))
			currentBestLocation = location;
		
		registerAndSearch();
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
}
