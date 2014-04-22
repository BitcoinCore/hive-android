package com.hivewallet.androidclient.wallet.util;

public class StringPlus
{
	private String string;
	private String contentType;

	public StringPlus(String string, String contentType)
	{
		this.string = string;
		this.contentType = contentType;
	}

	public String getString()
	{
		return string;
	}

	public String getContentType()
	{
		return contentType;
	}
}
