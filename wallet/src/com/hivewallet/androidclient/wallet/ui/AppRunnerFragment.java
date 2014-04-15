package com.hivewallet.androidclient.wallet.ui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.TransactionOutput;
import com.google.bitcoin.core.Wallet;
import com.google.common.base.Joiner;
import com.hivewallet.androidclient.wallet.Configuration;
import com.hivewallet.androidclient.wallet.Constants;
import com.hivewallet.androidclient.wallet.ExchangeRatesProvider;
import com.hivewallet.androidclient.wallet.WalletApplication;
import com.hivewallet.androidclient.wallet.integration.android.BitcoinIntegration;
import com.hivewallet.androidclient.wallet.data.PaymentIntent;
import com.hivewallet.androidclient.wallet.ui.send.SendCoinsActivity;
import com.hivewallet.androidclient.wallet.util.AppInstaller;
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
	private static final String TX_TYPE_OUTGOING = "outgoing";
	private static final String TX_TYPE_INCOMING = "incoming";
	
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
	
	private static class AppPlatformApi implements AppInstaller.AppInstallCallback {
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
		
		@SuppressWarnings({ "unused", "deprecation" })
		public void getTransaction(long callbackId, String txid) {
			Wallet wallet = application.getWallet();
			Transaction tx = null;
			
			try {
				Sha256Hash hash = new Sha256Hash(txid);
				tx = wallet.getTransaction(hash);
			} catch (IllegalArgumentException e) { /* handle below */ };
			
			if (tx != null) {
				Map<String, String> info = new HashMap<String, String>();
				final BigInteger value = tx.getValue(wallet);
				final boolean outgoing = value.signum() < 0;
				
				info.put("id", "'" + tx.getHashAsString() + "'");
				info.put("amount", value.abs().toString());
				info.put("type", outgoing ? "'" + TX_TYPE_OUTGOING + "'" : "'" + TX_TYPE_INCOMING + "'");
				info.put("timestamp", "'" + asISOString(tx.getUpdateTime()) + "'");
				
				List<String> inputAddresses = new ArrayList<String>();
				for (TransactionInput input : tx.getInputs()) {
					try {
						Address address = input.getScriptSig().getFromAddress(Constants.NETWORK_PARAMETERS);
						inputAddresses.add("'" + address.toString() + "'");
					} catch (ScriptException e) { /* skip input */ } 
				}
				
				List<String> outputAddresses = new ArrayList<String>();
				for (TransactionOutput output : tx.getOutputs()) {
					try {
						Address address = output.getScriptPubKey().getToAddress(Constants.NETWORK_PARAMETERS);
						outputAddresses.add("'" + address.toString() + "'");
					} catch (ScriptException e) { /* skip output */ } 
				}
				
				info.put("inputAddresses", "[" + Joiner.on(',').join(inputAddresses) + "]");
				info.put("outputAddresses", "[" + Joiner.on(',').join(outputAddresses) + "]");
				
				performCallback(callbackId, toJSDataStructure(info));
			} else {
				performCallback(callbackId, "null");
			}
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
		
		private static String asISOString(Date date) {
			TimeZone tz = TimeZone.getTimeZone("UTC");
			DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US);
			df.setTimeZone(tz);
			return df.format(date);
		}
	}
}
