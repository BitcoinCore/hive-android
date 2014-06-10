package com.hivewallet.androidclient.wallet.ui;

import com.google.bitcoin.core.AddressFormatException;
import com.hivewallet.androidclient.wallet.data.PaymentIntent;
import com.hivewallet.androidclient.wallet.ui.send.SendCoinsActivity;
import com.hivewallet.androidclient.wallet.util.FindNearbyContact;
import com.hivewallet.androidclient.wallet_test.R;
import com.squareup.picasso.Picasso;

import android.content.Context;
import android.content.Intent;
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
	private int resource;
	
	public FindNearbyAdapter(Context context, int resource)
	{
		super(context, resource);
		this.context = context;
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
		ImageButton sendMoneyImageButton = (ImageButton)rowView.findViewById(R.id.ib_contact_send_money);
		
		Picasso.with(context)
			.load(contact.getPhotoUri())
			.placeholder(R.drawable.ic_contact_picture)
			.into(photoImageView);
		
		nameTextView.setText(contact.getName());
		
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
		
		return rowView;
	}
}
