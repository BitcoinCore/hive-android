package com.hivewallet.androidclient.wallet.ui;

import com.google.bitcoin.core.Transaction;
import com.hivewallet.androidclient.wallet.data.PaymentIntent;
import com.hivewallet.androidclient.wallet.ui.InputParser.StringInputParser;
import com.hivewallet.androidclient.wallet_test.R;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.text.ClipboardManager;

@SuppressWarnings("deprecation")
public class AddContactChoiceFragment extends DialogFragment
{
	private static final String FRAGMENT_TAG = AddContactChoiceFragment.class.getName();
	
	private AbstractWalletActivity activity;
	private ClipboardManager clipboardManager;
	
	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);
		
		this.activity = (AbstractWalletActivity)activity;
		this.clipboardManager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		DialogBuilder builder = new DialogBuilder(activity);
		String[] choices = new String[] { activity.getString(R.string.add_contact_choice_qr)
									    , activity.getString(R.string.add_contact_choice_clipboard)
										};
		
		builder.setTitle(R.string.add_contact) //_choice)
			.setItems(choices, new DialogInterface.OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					switch (which) {
						case 0:
							handleScan();
							break;
						case 1:
							handlePasteClipboard();
							break;
						default:
							throw new UnsupportedOperationException();
					}
				}
			});
		return builder.create();
	}
	
	private void handleScan()
	{
		activity.startActivityForResult(new Intent(activity, ScanActivity.class), WalletActivity.REQUEST_CODE_SCAN_ADD_CONTACT);
	}	
	
	private void handlePasteClipboard()
	{
		if (clipboardManager.hasText())
		{
			final String input = clipboardManager.getText().toString().trim();

			new StringInputParser(input)
			{
				@Override
				protected void handlePaymentIntent(final PaymentIntent paymentIntent)
				{
					if (paymentIntent.hasAddress())
						EditAddressBookEntryFragment.edit(getFragmentManager(), paymentIntent.getAddress().toString());
					else
						dialog(activity, null, R.string.address_book_options_paste_from_clipboard_title,
								R.string.address_book_options_paste_from_clipboard_invalid);
				}

				@Override
				protected void handleDirectTransaction(final Transaction transaction)
				{
					cannotClassify(input);
				}

				@Override
				protected void error(final int messageResId, final Object... messageArgs)
				{
					dialog(activity, null, R.string.address_book_options_paste_from_clipboard_title, messageResId, messageArgs);
				}
			}.parse();
		}
		else
		{
			activity.toast(R.string.address_book_options_paste_from_clipboard_empty);
		}
	}
	
	public static void show(FragmentManager manager) {
		AddContactChoiceFragment fragment = instance();
		fragment.show(manager, FRAGMENT_TAG);
	}
	
	private static AddContactChoiceFragment instance() {
		return new AddContactChoiceFragment();
	}
}
