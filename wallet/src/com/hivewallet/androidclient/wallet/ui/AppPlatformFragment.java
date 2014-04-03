package com.hivewallet.androidclient.wallet.ui;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.CharStreams;
import com.google.common.io.InputSupplier;
import com.hivewallet.androidclient.wallet.Configuration;
import com.hivewallet.androidclient.wallet.Constants;
import com.hivewallet.androidclient.wallet.ExchangeRatesProvider;
import com.hivewallet.androidclient.wallet.PaymentIntent;
import com.hivewallet.androidclient.wallet.WalletApplication;
import com.hivewallet.androidclient.wallet.integration.android.BitcoinIntegration;
import com.hivewallet.androidclient.wallet.util.GenericUtils;
import com.hivewallet.androidclient.wallet_test.R;

import android.annotation.SuppressLint;
import android.app.Activity;
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

@SuppressLint("SetJavaScriptEnabled")
public class AppPlatformFragment extends Fragment
{
	private static final Logger log = LoggerFactory.getLogger(AppPlatformFragment.class);
	private static final int REQUEST_CODE_SEND_MONEY = 0;
	private static final String HIVE_ANDROID_APP_PLATFORM_JS = "hive_android_app_platform.js";
	
	private WebView webView;
	private AppPlatformApi appPlatformApi;
	
	public AppPlatformFragment() { /* required default constructor */ }
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		String appBase = "file:///android_asset/com.hivewallet.supporthive/";
		
		View view = inflater.inflate(R.layout.app_platform_fragment, container, false);
		
		webView = (WebView)view.findViewById(R.id.wv_app_platform);

		webView.setWebViewClient(new AppPlatformWebViewClient(getActivity(), appBase));
		webView.addJavascriptInterface(new AppPlatformApiLoader(getActivity().getAssets()), "hive");
		appPlatformApi = new AppPlatformApi(this, webView);
		webView.addJavascriptInterface(appPlatformApi, "__bitcoin");
		
		webView.getSettings().setJavaScriptEnabled(true);
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
		private AssetManager assetManager;
		
		public AppPlatformApiLoader(AssetManager assetManager)
		{
			this.assetManager = assetManager;
		}
		
		@SuppressWarnings("unused")
		public String init() {
			try {
				final InputStream is = assetManager.open(HIVE_ANDROID_APP_PLATFORM_JS);
				InputSupplier<InputStream> inputSupplier = new InputSupplier<InputStream>()
				{
					@Override
					public InputStream getInput() throws IOException
					{
						return is;
					}
				};
				final String androidSubstrateLayer = CharStreams.toString(
						CharStreams.newReaderSupplier(inputSupplier, Charsets.UTF_8)).replace('\n', ' ');
				
				return androidSubstrateLayer;
			} catch (IOException e) {
				throw new RuntimeException("Error while trying to activate Hive API", e);
			}
		}
	}
	
	private static class AppPlatformApi {
		private static final Logger log = LoggerFactory.getLogger(AppPlatformApi.class);
		
		private WalletApplication application;
		private Configuration config;
		private Fragment fragment;
		private WebView webView;
		
		private long lastSendMoneyCallbackId = -1;
		
		public AppPlatformApi(Fragment fragment, WebView webView)
		{
			this.application = (WalletApplication)fragment.getActivity().getApplication();
			this.config = application.getConfiguration();
			this.fragment = fragment;
			this.webView = webView;
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
			log.info("API: in sendMoney1 - " + addressStr + "; " + amountLong);
			
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
			log.info("API: in sendMoney2");
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
		
		private static String toJSDataStructure(Map<String, String> entries) {
			Map<String, String> ppEntries = new HashMap<String, String>();
			for (Entry<String, String> entry : entries.entrySet()) {
				ppEntries.put("'" + entry.getKey() + "'", entry.getValue());
			}
			
			String sEntries = Joiner.on(',').withKeyValueSeparator(":").join(ppEntries);
			return "{" + sEntries + "}";
		}
	}
}