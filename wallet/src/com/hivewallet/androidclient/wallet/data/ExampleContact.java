package com.hivewallet.androidclient.wallet.data;

public class ExampleContact
{
	final private String name;
	final private int photoId;
	final private boolean isHiveContact;
	
	public ExampleContact(String name, int photoId, boolean isHiveContact)
	{
		this.name = name;
		this.photoId = photoId;
		this.isHiveContact = isHiveContact;
	}

	public String getName()
	{
		return name;
	}

	public int getPhotoId()
	{
		return photoId;
	}

	public boolean isHiveContact()
	{
		return isHiveContact;
	}
}
