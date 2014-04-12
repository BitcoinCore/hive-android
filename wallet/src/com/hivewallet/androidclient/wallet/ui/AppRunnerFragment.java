package com.hivewallet.androidclient.wallet.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.CharStreams;
import com.google.common.io.InputSupplier;
import com.hivewallet.androidclient.wallet.Configuration;
import com.hivewallet.androidclient.wallet.Constants;
import com.hivewallet.androidclient.wallet.ExchangeRatesProvider;
import com.hivewallet.androidclient.wallet.WalletApplication;
import com.hivewallet.androidclient.wallet.integration.android.BitcoinIntegration;
import com.hivewallet.androidclient.wallet.data.PaymentIntent;
import com.hivewallet.androidclient.wallet.ui.send.SendCoinsActivity;
import com.hivewallet.androidclient.wallet.util.AppPlatformDBHelper;
import com.hivewallet.androidclient.wallet.util.GenericUtils;
import com.hivewallet.androidclient.wallet_test.R;

@SuppressLint("SetJavaScriptEnabled")
public class AppRunnerFragment extends Fragment
{
	private static final Logger log = LoggerFactory.getLogger(AppRunnerFragment.class);
	private static final int REQUEST_CODE_SEND_MONEY = 0;
	private static final String HIVE_ANDROID_APP_PLATFORM_JS = "hive_android_app_platform.min.js";
	private static final String ARGUMENT_APP_ID = "app_id";
	private static final String APP_STORE_BASE = "file:///android_asset/";
	private static final String APP_PLATFORM_DOWNLOAD_FILE = "app.hiveapp";
	private static final String APP_PLATFORM_UNPACK_FOLDER = "unpacked_app";
	private static final String APP_PLATFORM_MANIFEST_FILE = "manifest.json";
	
	private WebView webView;
	
	private AppPlatformApi appPlatformApi;
	private String platformJS;
	
	public static AppRunnerFragment newInstance(String appId) {
		AppRunnerFragment f = new AppRunnerFragment();
		
		Bundle args = new Bundle();
		args.putString(ARGUMENT_APP_ID, appId);
		f.setArguments(args);
		
		return f;
	}
	
	public AppRunnerFragment() { /* required default constructor */ }
	
	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);
		
		try
		{
			final InputStream is = activity.getAssets().open(HIVE_ANDROID_APP_PLATFORM_JS);
			platformJS = IOUtils.toString(is, Charset.defaultCharset());
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error while loading platform javascript layer", e);
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		Bundle args = getArguments();
		if (args == null)
			throw new IllegalArgumentException("This fragment requires arguments.");
		
		String appId = args.getString(ARGUMENT_APP_ID);
		if (appId == null)
			throw new IllegalArgumentException("App id needs to be provided");
		
		String appBase = AppPlatformDBHelper.getAppBase(getActivity()) + appId + "/";
		if (Constants.APP_STORE_ID.equals(appId))
			appBase = APP_STORE_BASE + appId + "/";		
		
		View view = inflater.inflate(R.layout.app_runner_fragment, container, false);
		webView = (WebView)view.findViewById(R.id.wv_app_runner);

		webView.setWebViewClient(new AppPlatformWebViewClient(getActivity(), appBase));
		webView.addJavascriptInterface(new AppPlatformApiLoader(platformJS), "hive");
		appPlatformApi = new AppPlatformApi(this, webView);
		webView.addJavascriptInterface(appPlatformApi, "__bitcoin");
		
		webView.getSettings().setJavaScriptEnabled(true);
		webView.loadUrl("javascript:" + platformJS);
		webView.loadUrl(appBase + "index.html");
		
		return view;
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (requestCode == REQUEST_CODE_SEND_MONEY) {
			if (resultCode == Activity.RESULT_OK) {
				final String txHash = BitcoinIntegration.transactionHashFromResult(data);
				appPlatformApi.sendMoneyResult(true, txHash);
			} else {
				appPlatformApi.sendMoneyResult(false, null);
			}
		}
	}
	
	@Override
	public void onPause()
	{
		appPlatformApi.onPause();
		
		super.onPause();
	}
	
	private static class AppPlatformWebViewClient extends WebViewClient {
		private Activity activity;
		private String baseURL;
		
		public AppPlatformWebViewClient(Activity activity, String baseURL)
		{
			super();
			
			this.activity = activity;
			this.baseURL = baseURL.toLowerCase(Locale.US);
		}
		
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url)
		{
			String lcUrl = url.toLowerCase(Locale.US);
			boolean accessAllowed = lcUrl.startsWith(baseURL);
			
			if (!accessAllowed && (lcUrl.startsWith("http://") || lcUrl.startsWith("https://"))) {
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				activity.startActivity(intent);
			} else if (!accessAllowed) {
				log.warn("Prevented access to this URL: {}", url);
			}
			
			return !accessAllowed;
		}
	}
	
	private static class AppPlatformApiLoader {
		private String platformJS;
		
		public AppPlatformApiLoader(String platformJS)
		{
			this.platformJS = platformJS;
		}
		
		@SuppressWarnings("unused")
		public String init() {
			return platformJS;
		}
	}
	
	private static class AppPlatformApi implements AppInstallCallback {
		private static final Logger log = LoggerFactory.getLogger(AppPlatformApi.class);
		
		private WalletApplication application;
		private Configuration config;
		private Fragment fragment;
		private WebView webView;
		
		volatile private long lastSendMoneyCallbackId = -1;
		volatile private long lastInstallAppCallbackId = -1;
		
		private AppPlatformDBHelper appPlatformDBHelper;
		private AppInstaller appInstaller;
		
		public AppPlatformApi(Fragment fragment, WebView webView)
		{
			this.application = (WalletApplication)fragment.getActivity().getApplication();
			this.config = application.getConfiguration();
			this.fragment = fragment;
			this.webView = webView;
			this.appPlatformDBHelper = application.getAppPlatformDBHelper();
		}
		
		@SuppressWarnings("unused")
		public void getUserInfo(long callbackId) {
			Address address = application.determineSelectedAddress();
			Map<String, String> info = new HashMap<String, String>();
			
			info.put("firstName", "'Hive user'");
			info.put("lastName", "''");
			info.put("address", "'" + address.toString() + "'");
			performCallback(callbackId, toJSDataStructure(info));
		}
		
		@SuppressWarnings("unused")
		public void getSystemInfo(long callbackId) {
			Map<String, String> info = new HashMap<String, String>();
			
			String version = application.packageInfo().versionName;
			
			info.put("version", "'" + version + "'");
			info.put("buildNumber", "'" + version + "'");
			info.put("platform", "'android'");
			
			info.put("decimalSeparator", "'.'");	// always use Locale.US at the moment
			info.put("locale", "'" + Locale.getDefault().toString() + "'");
			
			info.put("preferredBitcoinFormat", "'" + config.getBtcPrefix() + "'");
			
			String exchangeCurrencyCode = config.getExchangeCurrencyCode();
			if (exchangeCurrencyCode != null) {
				info.put("preferredCurrency", "'" + exchangeCurrencyCode.toString()  + "'");
			} else {
				String defaultCurrencyCode = ExchangeRatesProvider.defaultCurrencyCode();
				if (defaultCurrencyCode == null) defaultCurrencyCode = "USD";
				
				info.put("preferredCurrency", "'" + defaultCurrencyCode + "'");
			}
			
			List<String> currencies = new ArrayList<String>();
			for (String currency : config.getCachedExchangeCurrencies()) {
				currencies.add("'" + currency + "'");
			}
			info.put("availableCurrencies", "[" + Joiner.on(',').join(currencies) + "]");
			
			info.put("onTestnet", Constants.TEST ? "true" : "false");
			performCallback(callbackId, toJSDataStructure(info));
		}
		
		@SuppressWarnings("unused")
		public String userStringForSatoshi(long longAmount) {
			BigInteger amount = BigInteger.valueOf(longAmount);
			return GenericUtils.formatValue(amount, config.getBtcPrecision(), config.getBtcShift());
		}
		
		@SuppressWarnings("unused")
		public long satoshiFromUserString(String amountStr) {
			int shift = config.getBtcShift();
			BigInteger amount = GenericUtils.parseValue(amountStr, shift);
			return amount.longValue();
		}
		
		public void sendMoney1(long callbackId, String addressStr, long amountLong) {
			Address address = null;
			
			try {
				address = new Address(Constants.NETWORK_PARAMETERS, addressStr);
			} catch (AddressFormatException e) { /* ignore address */ }
			
			BigInteger amount = null;
			if (amountLong >= 0)
				amount = BigInteger.valueOf(amountLong);

			PaymentIntent paymentIntent = null;
			if (address != null)
				paymentIntent = PaymentIntent.fromAddressAndAmount(address, amount);
			
			final Intent intent = new Intent(fragment.getActivity(), SendCoinsActivity.class);
			if (paymentIntent != null)
				intent.putExtra(SendCoinsActivity.INTENT_EXTRA_PAYMENT_INTENT, paymentIntent);
			
			lastSendMoneyCallbackId = callbackId;
			fragment.startActivityForResult(intent, REQUEST_CODE_SEND_MONEY);
		}
		
		@SuppressWarnings("unused")
		public void sendMoney2(long callbackId, String addressStr) {
			sendMoney1(callbackId, addressStr, -1);
		}
		
		public void sendMoneyResult(boolean success, @Nullable String txHash) {
			if (lastSendMoneyCallbackId == -1)
				return;
			
			if (txHash != null) {
				performCallback(lastSendMoneyCallbackId, success ? "true" : "false", "'" + txHash + "'");
			} else {
				performCallback(lastSendMoneyCallbackId, success ? "true" : "false", "null");
			}
			
			lastSendMoneyCallbackId = -1;
		}
		
		@SuppressWarnings("unused")
		public void getApplication(long callbackId, String appId) {
			Map<String, String> manifest = appPlatformDBHelper.getAppManifest(appId);
			
			if (manifest == null) {
				performCallback(callbackId, "null");
			} else {
				performCallback(callbackId, toJSDataStructure(manifest));
			}
		}
		
		@SuppressWarnings("unused")
		public void installApp(long callbackId, String url) {
			if (appInstaller != null) {
				/* Concurrent install are not supported at the moment */
				performCallback(callbackId, "'Another install is in progress'", "false");
				return;
			}
			
			lastInstallAppCallbackId = callbackId;
			File dir = application.getDir(Constants.APP_PLATFORM_FOLDER, Context.MODE_PRIVATE);
			appInstaller = new AppInstaller(url, dir, this);
			appInstaller.start();
		}
		
		@Override
		public void installSuccessful(String appId, File unpackDir, File appDir, JSONObject manifest)
		{
			try
			{
				FileUtils.deleteQuietly(appDir);
				FileUtils.moveDirectory(unpackDir, appDir);
				appPlatformDBHelper.addManifest(appId, manifest);
				
				appInstaller = null;
				if (lastInstallAppCallbackId != -1)
					performCallback(lastInstallAppCallbackId, "null", "true");
				lastInstallAppCallbackId = -1;
			}
			catch (IOException e)
			{
				log.info("Exception while finalizing installation: {}", e);
				installFailed("Install failed");
			}
		}

		@Override
		public void installFailed(String errMsg)
		{
			appInstaller = null;
			if (lastInstallAppCallbackId != -1)
				performCallback(lastInstallAppCallbackId, "'" + errMsg + "'", "false");
			lastInstallAppCallbackId = -1;
		}		
		
		private void performCallback(long callbackId, String... arguments) {
			if (arguments == null || arguments.length < 1)
				throw new IllegalArgumentException("Need at least one argument");
			
			final String furtherArguments = Joiner.on(',').join(arguments);
			final String js = "javascript:bitcoin.__callbackFromAndroid(" + callbackId + "," + furtherArguments + ");";
			fragment.getActivity().runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					webView.loadUrl(js);
				}
			});			
		}
		
		public void onPause() {
			if (appInstaller != null)
				appInstaller.cancel();
		}
		
		private static String toJSDataStructure(Map<String, String> entries) {
			Map<String, String> ppEntries = new HashMap<String, String>();
			for (Entry<String, String> entry : entries.entrySet()) {
				ppEntries.put("'" + entry.getKey() + "'", entry.getValue());
			}
			
			String sEntries = Joiner.on(',').withKeyValueSeparator(":").join(ppEntries);
			return "{" + sEntries + "}";
		}
		
		private static class AppInstaller extends Thread {
			private static final int BUFFER_SIZE = 4096;
			
			private String urlStr;
			private AppInstallCallback callback;
			private File dir;
			volatile private boolean isRunning = true;
			
			public AppInstaller(String url, File dir, AppInstallCallback callback)
			{
				this.urlStr = url;
				this.dir = dir;
				this.callback = callback;
			}
			
			public void cancel() {
				this.isRunning = false;
			}
			
			@Override
			public void run()
			{
				log.info("Starting install for {}", urlStr);
				
				/* 1. Preliminary checks */
				if (urlStr == null) {
					String errMsg = "No app URL provided";
					log.info("Aborting install: {}", errMsg);
					callback.installFailed(errMsg);
					return;
				}
				
				if (!urlStr.toLowerCase(Locale.US).startsWith("https")) {
					String errMsg = "Secure download location required";
					log.info("Aborting install: {}", errMsg);
					callback.installFailed(errMsg);
					return;
				}
				
				URL url = null;
				try { url = new URL(urlStr); } catch (MalformedURLException e) { /* handle below */ }
				if (url == null) {
					String errMsg = "Invalid app URL";
					log.info("Aborting install: {}", errMsg);
					callback.installFailed(errMsg);
					return;
				}
				
				/* 2. Download archive */
				File downloadFile = new File(dir, APP_PLATFORM_DOWNLOAD_FILE);
				HttpURLConnection conn = null;
				FileOutputStream fileOutputStream = null;
				String errMsg = null;
				try
				{
					conn = (HttpURLConnection)url.openConnection();
					
					if (conn.getResponseCode() != HttpURLConnection.HTTP_OK)
						throw new IOException();
					
					InputStream inputStream = conn.getInputStream();
					fileOutputStream = new FileOutputStream(downloadFile);
					
					byte[] buffer = new byte[BUFFER_SIZE];
					int count;
					while ((count = inputStream.read(buffer)) > 0) {
						fileOutputStream.write(buffer, 0, count);
						
						if (!isRunning) {
							errMsg = "Install canceled";
							break;
						}
					}
				}
				catch (IOException e)
				{
					errMsg = "Unable to download app";
				}
				finally
				{
					if (conn != null)
						conn.disconnect();
					if (fileOutputStream != null)
						try { fileOutputStream.close(); } catch (IOException ignored) { }
				}
				
				if (errMsg != null) {
					log.info("Aborting install: {}", errMsg);
					callback.installFailed(errMsg);
					return;
				}
				
				/* 3. Unpack archive */
				errMsg = null;
				File unpackDir = new File(dir, APP_PLATFORM_UNPACK_FOLDER);
				FileInputStream fileInputStream = null;
				FileOutputStream fileOutputStream2 = null;
				ZipInputStream zipInputStream = null;
				try
				{
					FileUtils.deleteQuietly(unpackDir);
					unpackDir.mkdirs();
					
					fileInputStream = new FileInputStream(downloadFile);
					zipInputStream = new ZipInputStream(fileInputStream);
					ZipEntry zipEntry = null;
					
					while ((zipEntry = zipInputStream.getNextEntry()) != null) {
						File zipEntryFile = new File(unpackDir, zipEntry.getName());
						if (!isSubdirectory(unpackDir, zipEntryFile))
							throw new IOException("Security violation?!");

						if (zipEntry.isDirectory()) {
							zipEntryFile.mkdirs();
						} else {
							zipEntryFile.getParentFile().mkdirs();
							
							fileOutputStream2 = new FileOutputStream(zipEntryFile);
							byte[] buffer = new byte[BUFFER_SIZE];
							int count;
							while ((count = zipInputStream.read(buffer)) != -1) {
								fileOutputStream2.write(buffer, 0, count);
							}
							fileOutputStream2.close();
						}
					}
				}
				catch (FileNotFoundException e)
				{
					log.info("Exception while extracting: {}", e.toString());
					errMsg = "Error while extracting archive";
				}
				catch (IOException e)
				{
					log.info("Exception while extracting: {}", e.toString());
					errMsg = "Error while extracting archive";
				}
				finally
				{
					if (fileInputStream != null)
						try { fileInputStream.close(); } catch (IOException ignored) { }
					if (fileOutputStream2 != null)
						try { fileOutputStream2.close(); } catch (IOException ignored) { }
					if (zipInputStream != null)
						try { zipInputStream.close(); } catch (IOException ignored) { }
				}
				
				if (errMsg != null) {
					log.info("Aborting install: {}", errMsg);
					callback.installFailed(errMsg);
					return;
				}
				
				/* 4. Check manifest */
				errMsg = null;
				File manifest = new File(unpackDir, APP_PLATFORM_MANIFEST_FILE);
				JSONObject manifestJSON = null;
				String appId = null;
				File appsDir = new File(dir, Constants.APP_PLATFORM_APP_FOLDER);
				File appDir = null;
				try
				{
					String manifestData = FileUtils.readFileToString(manifest, Charset.defaultCharset());
					manifestJSON = new JSONObject(manifestData);
					
					for (String key : AppPlatformDBHelper.getMinimalManifestKeys()) {
						if (!manifestJSON.has(key))
							throw new JSONException("Missing required key: " + key);
					}
					
					appId = manifestJSON.getString(AppPlatformDBHelper.KEY_ID);
					appDir = new File(appsDir, appId);
					if (!isSubdirectory(appsDir, appDir))
						throw new IOException("App is trying to walk the file system via its id");
					appDir.mkdirs();
				}
				catch (IOException e)
				{
					log.info("Exception while reading manifest: {}", e.toString());
					errMsg = "Malformed manifest";
				}
				catch (JSONException e)
				{
					log.info("Exception while parsing manifest: {}", e.toString());
					errMsg = "Malformed manifest";
				}
				
				if (errMsg != null) {
					log.info("Aborting install: {}", errMsg);
					callback.installFailed(errMsg);
					return;
				}
				
				log.info("Install was successful");
				callback.installSuccessful(appId, unpackDir, appDir, manifestJSON);
			}

		}
	}

	private static boolean isSubdirectory(File parent, File child) throws IOException {
		String parentPath = parent.getCanonicalPath();
		String childPath = child.getCanonicalPath();
		return childPath.startsWith(parentPath);
	}	
	
	private static interface AppInstallCallback {
		void installSuccessful(String appId, File unpackDir, File appDir, JSONObject manifest);
		void installFailed(String errMsg);
	}
}
