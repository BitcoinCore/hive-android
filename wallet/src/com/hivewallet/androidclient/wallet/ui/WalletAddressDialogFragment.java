/*
 * Copyright 2011-2014 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.hivewallet.androidclient.wallet.ui;

import javax.annotation.Nonnull;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.text.ClipboardManager;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.hivewallet.androidclient.wallet_test.R;

/**
 * @author Jan Vornberger
 * @author Andreas Schildbach
 */
@SuppressWarnings("deprecation")
public class WalletAddressDialogFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = WalletAddressDialogFragment.class.getName();

	private static final String KEY_ADDRESS = "address";
	private static final String KEY_QR_CODE = "qr_code";

	public static void show(final FragmentManager fm, @Nonnull final String address, @Nonnull final Bitmap qrCode)
	{
		final DialogFragment newFragment = instance(address, qrCode);
		newFragment.show(fm, FRAGMENT_TAG);
	}

	private static WalletAddressDialogFragment instance(@Nonnull final String address, @Nonnull final Bitmap qrCode)
	{
		final WalletAddressDialogFragment fragment = new WalletAddressDialogFragment();

		final Bundle args = new Bundle();
		args.putString(KEY_ADDRESS, address);
		args.putParcelable(KEY_QR_CODE, qrCode);
		fragment.setArguments(args);

		return fragment;
	}

	private AbstractWalletActivity activity;
	private String address;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (AbstractWalletActivity)activity;
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState)
	{
		address = getArguments().getString(KEY_ADDRESS);
		final Bitmap qrCode = (Bitmap)getArguments().getParcelable(KEY_QR_CODE);

		final Dialog dialog = new Dialog(activity);
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		dialog.setContentView(R.layout.wallet_address_dialog);
		dialog.setCanceledOnTouchOutside(true);

		final ImageView imageView = (ImageView)dialog.findViewById(R.id.iv_qr_code);
		imageView.setImageBitmap(qrCode);
		imageView.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
				dismiss();
			}
		});
		
		final TextView textView = (TextView)dialog.findViewById(R.id.tv_bitcoin_address);
		textView.setText(address);

		final ImageButton copyButton = (ImageButton)dialog.findViewById(R.id.ib_copy_wallet_address);
		copyButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				handleCopy();
			}
		});
		
		final ImageButton shareButton = (ImageButton)dialog.findViewById(R.id.ib_share_wallet_address);
		shareButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				handleShare();
			}
		});		

		return dialog;
	}
	
	private void handleCopy()
	{
		ClipboardManager clipboardManager =
				(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
		clipboardManager.setText(address);
		activity.toast(R.string.wallet_address_dialog_copy_address);
	}

	private void handleShare()
	{
		final Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_TEXT, "bitcoin:" + address);
		startActivity(Intent.createChooser(intent, getString(R.string.wallet_address_dialog_share_address)));
	}	
}
