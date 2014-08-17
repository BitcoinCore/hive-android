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

import com.hivewallet.androidclient.permissionpack.LocationService;
import com.hivewallet.androidclient.wallet.Configuration;
import com.hivewallet.androidclient.wallet.Constants;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public class FindNearbyGPSWorker extends Thread implements Callback
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
	private static final int BUFFER_SIZE = 4096;
	
	private final Context context;
	private final Configuration configuration;
	private final Handler parentHandler;
	private final CookieManager cookieManager;
	private final URL hiveServer;
	
	private Handler handler;
	
	private FindNearbyContact userRecord;
	private String bitcoinAddress;
	
	private Location currentBestLocation = null;
	private long lastActivityTimestamp = 0;
	
	private Messenger myMessenger;
	private Messenger serviceMessenger;
	private boolean isServiceBound = false;
	
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
		handler = new Handler(this);
		myMessenger = new Messenger(handler);
		
		userRecord = FindNearbyContact.lookupUserRecord(context.getContentResolver(), configuration, bitcoinAddress);
		if (userRecord == null) {
			log.warn("Unable to lookup user details in preparation of Hive geo server search - GPS worker is shutting down.");
			return;
		}
		
		Intent intent = new Intent(LocationService.ACTION_START); 
		context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
		
		Looper.loop();
	}

	private ServiceConnection serviceConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			isServiceBound = true;
			serviceMessenger = new Messenger(service);
			
			try {
				Message msg = Message.obtain(null, LocationService.MSG_START);
				msg.replyTo = myMessenger;
				serviceMessenger.send(msg);
			} catch (RemoteException e) {
				log.warn("Communication error with LocationService ({}) - GPS worker is shutting down.", e);
			}
		}
		
		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			isServiceBound = false;
			serviceMessenger = null;
		}
	};
	
	@Override
	public boolean handleMessage(Message msg)
	{
		switch (msg.what) {
			case LocationService.MSG_STATUS:
				Bundle statusBundle = (Bundle)msg.obj;
				
				if (!statusBundle.getBoolean(LocationService.KEY_IS_AVAILABLE, false)) {
					String error = statusBundle.getString(LocationService.KEY_ERROR, "Error message unavailable");
					log.warn("LocationService not available ({}) - GPS worker is shutting down.", error);
				}
				return true;
			case LocationService.MSG_LOCATION:
				currentBestLocation = (Location)msg.obj;
				registerAndSearch();
				return true;
			default:
				return false;
		}
	}

	private Runnable shutdownRunnable = new Runnable()
	{
		@Override
		public void run()
		{
			if (isServiceBound)
				context.unbindService(serviceConnection);
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
}
