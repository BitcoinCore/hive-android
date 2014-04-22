package com.hivewallet.androidclient.wallet.util;

import java.io.UnsupportedEncodingException;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.HttpHeaderParser;

public class StringPlusRequest extends Request<StringPlus>
{
	private final Listener<StringPlus> listener;
	private byte[] body;
	
	public StringPlusRequest(int method, String url, Listener<StringPlus> listener, ErrorListener errorListener, byte[] body)
	{
		super(method, url, errorListener);
		this.listener = listener;
		this.body = body;
	}
	
	@Override
	public byte[] getBody() throws AuthFailureError
	{
		return body;
	}
	
	@Override
	protected void deliverResponse(StringPlus responsePlus)
	{
		listener.onResponse(responsePlus);
	}
	
	@Override
	protected Response<StringPlus> parseNetworkResponse(NetworkResponse response)
	{
		String parsed;
		try {
			parsed = new String(response.data, HttpHeaderParser.parseCharset(response.headers));
		} catch (UnsupportedEncodingException e) {
			parsed = new String(response.data);
		}
		
		String contentType = "application/octet-stream";
		for (String header : response.headers.keySet()) {
			if (header.equalsIgnoreCase("content-type"))
				contentType = response.headers.get(header);
		}
		
		StringPlus responsePlus = new StringPlus(parsed, contentType);
		return Response.success(responsePlus, HttpHeaderParser.parseCacheHeaders(response));
	}
}
