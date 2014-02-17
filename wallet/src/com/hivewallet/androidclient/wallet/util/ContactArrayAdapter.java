package com.hivewallet.androidclient.wallet.util;

import com.hivewallet.androidclient.wallet.data.ExampleContact;
import com.hivewallet.androidclient.wallet_test.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class ContactArrayAdapter extends ArrayAdapter<ExampleContact>
{
	private final Context context;
	
	public ContactArrayAdapter(Context context, ExampleContact[] contacts)
	{
		super(context, R.layout.contacts_list_item, contacts);
		this.context = context;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		ExampleContact contact = super.getItem(position);
		LayoutInflater inflater =
				(LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.contacts_list_item, parent, false);
		TextView contactNameTextView = (TextView)rowView.findViewById(R.id.tv_contact_name);
		ImageView contactPhotoImageView = (ImageView)rowView.findViewById(R.id.iv_contact_photo);
		ImageView indicatorImageView = (ImageView)rowView.findViewById(R.id.iv_contact_photo_hive_indicator);
		
		contactNameTextView.setText(contact.getName());
		contactPhotoImageView.setImageResource(contact.getPhotoId());
		if (contact.isHiveContact())
			indicatorImageView.setVisibility(View.VISIBLE);
		
		return rowView;
	}
}
