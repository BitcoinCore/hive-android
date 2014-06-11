package com.hivewallet.androidclient.wallet.ui;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import com.google.bitcoin.core.AddressFormatException;
import com.google.common.base.Optional;
import com.hivewallet.androidclient.wallet.AddressBookProvider;
import com.hivewallet.androidclient.wallet.AddressBookProvider.AddressBookEntry;
import com.hivewallet.androidclient.wallet.data.PaymentIntent;
import com.hivewallet.androidclient.wallet.ui.send.SendCoinsActivity;
import com.hivewallet.androidclient.wallet.util.FindNearbyContact;
import com.hivewallet.androidclient.wallet_test.R;
import com.squareup.picasso.Picasso;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

public class FindNearbyAdapter extends ArrayAdapter<FindNearbyContact>
{
	private Context context;
	private final FragmentManager fragmentManager;
	private int resource;
	
	private final Map<String, Optional<AddressBookEntry>> addressBookCache = new HashMap<String, Optional<AddressBookEntry>>();
	
	public FindNearbyAdapter(Context context, FragmentManager fragmentManager, int resource)
	{
		super(context, resource);
		this.context = context;
		this.fragmentManager = fragmentManager;
		this.resource = resource;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		final FindNearbyContact contact = super.getItem(position);
		LayoutInflater inflater =
				(LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(resource, parent, false);
		
		ImageView photoImageView = (ImageView)rowView.findViewById(R.id.iv_contact_photo);
		TextView nameTextView = (TextView)rowView.findViewById(R.id.tv_contact_name);
		ImageButton addContactImageButton = (ImageButton)rowView.findViewById(R.id.ib_contact_add);
		ImageButton sendMoneyImageButton = (ImageButton)rowView.findViewById(R.id.ib_contact_send_money);
		
		Picasso.with(context)
			.load(contact.getPhotoUri())
			.placeholder(R.drawable.ic_contact_picture)
			.into(photoImageView);
		
		nameTextView.setText(contact.getName());
		
		addContactImageButton.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				EditAddressBookEntryFragment.edit(
						fragmentManager, contact.getBitcoinAddress(), contact.getName(), contact.getPhotoUri());
			}
		});
		
		sendMoneyImageButton.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				try { 
					SendCoinsActivity.start(context,
							PaymentIntent.fromAddress(contact.getBitcoinAddress(), null)); 
				} catch (AddressFormatException e) {
					// if something is wrong with the address, ignore it
					// and go to the send coin screen anyway
					Intent intent = new Intent(context, SendCoinsActivity.class);
					context.startActivity(intent);
				}
			}
		});
		
		if (lookupEntry(contact.getBitcoinAddress()) != null) {
			// we already have this contact - hide the add contact button
			addContactImageButton.setVisibility(View.GONE);
		}
		
		return rowView;
	}
	
	private AddressBookEntry lookupEntry(@Nonnull final String address)
	{
		final Optional<AddressBookEntry> cachedEntry = addressBookCache.get(address);
		if (cachedEntry == null)
		{
			final AddressBookEntry entry = AddressBookProvider.lookupEntry(context, address);
			final Optional<AddressBookEntry> optionalEntry = Optional.fromNullable(entry);
			addressBookCache.put(address, optionalEntry);	// cache entry or the fact that it wasn't found
			return entry;
		}
		else
		{
			return cachedEntry.orNull();
		}
	}

	public void clearAddressBookCache()
	{
		addressBookCache.clear();

		notifyDataSetChanged();
	}	
}
