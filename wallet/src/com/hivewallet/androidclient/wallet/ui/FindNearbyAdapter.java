package com.hivewallet.androidclient.wallet.ui;

import com.hivewallet.androidclient.wallet.util.FindNearbyWorker.FindNearbyContact;
import com.hivewallet.androidclient.wallet_test.R;
import com.squareup.picasso.Picasso;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
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
		FindNearbyContact contact = super.getItem(position);
		LayoutInflater inflater =
				(LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(resource, parent, false);
		ImageView photoImageView = (ImageView)rowView.findViewById(R.id.iv_contact_photo);
		TextView nameTextView = (TextView)rowView.findViewById(R.id.tv_contact_name);
		
		Picasso.with(context)
			.load((Uri)null)
			.placeholder(R.drawable.ic_contact_picture)
			.into(photoImageView);
		nameTextView.setText(contact.getName());
		
		return rowView;
	}
}
