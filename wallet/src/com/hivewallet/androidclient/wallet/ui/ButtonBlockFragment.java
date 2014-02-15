package com.hivewallet.androidclient.wallet.ui;

import com.actionbarsherlock.app.SherlockFragment;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.uri.BitcoinURI;
import com.hivewallet.androidclient.wallet.Configuration;
import com.hivewallet.androidclient.wallet.WalletApplication;
import com.hivewallet.androidclient.wallet.util.BitmapFragment;
import com.hivewallet.androidclient.wallet.util.Nfc;
import com.hivewallet.androidclient.wallet.util.Qr;
import com.hivewallet.androidclient.wallet_test.R;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.nfc.NfcManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;

public class ButtonBlockFragment extends SherlockFragment
{
	private WalletActivity activity;
	private WalletApplication application;
	private Configuration config;
	private NfcManager nfcManager;

	private Address lastSelectedAddress;

	private Bitmap qrCodeBitmap;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (WalletActivity) activity;
		this.application = (WalletApplication) activity.getApplication();
		this.config = application.getConfiguration();
		this.nfcManager = (NfcManager) activity.getSystemService(Context.NFC_SERVICE);
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		final View view = inflater.inflate(R.layout.button_block_fragment, container, false);

		final View requestButton = view.findViewById(R.id.ib_receive_money);
		requestButton.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				activity.handleRequestCoins();
			}
		});

		final View sendButton = view.findViewById(R.id.ib_send_money);
		sendButton.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				activity.handleSendCoins();
			}
		});

		final View sendQrButton = view.findViewById(R.id.ib_scan_qrcode);
		sendQrButton.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				activity.handleScan();
			}
		});		
		
		
		final View showQrButton = view.findViewById(R.id.ib_show_qrcode);
		showQrButton.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				handleShowQRCode();
			}
		});

		return view;
	}

	@Override
	public void onResume()
	{
		super.onResume();

		config.registerOnSharedPreferenceChangeListener(prefsListener);

		updateView();
	}

	@Override
	public void onPause()
	{
		config.unregisterOnSharedPreferenceChangeListener(prefsListener);

		Nfc.unpublish(nfcManager, getActivity());

		super.onPause();
	}

	private void updateView()
	{
		final Address selectedAddress = application.determineSelectedAddress();

		if (!selectedAddress.equals(lastSelectedAddress))
		{
			lastSelectedAddress = selectedAddress;

			final String addressStr = BitcoinURI.convertToBitcoinURI(selectedAddress, null, null, null);

			final int size = (int) (256 * getResources().getDisplayMetrics().density);
			qrCodeBitmap = Qr.bitmap(addressStr, size);

			Nfc.publishUri(nfcManager, getActivity(), addressStr);
		}
	}

	private void handleShowQRCode()
	{
		BitmapFragment.show(getFragmentManager(), qrCodeBitmap);
	}

	private final OnSharedPreferenceChangeListener prefsListener = new OnSharedPreferenceChangeListener()
	{
		@Override
		public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
		{
			if (Configuration.PREFS_KEY_SELECTED_ADDRESS.equals(key))
				updateView();
		}
	};
}