package com.hivewallet.androidclient.wallet.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;

import com.google.protobuf.ByteString;
import com.hivewallet.androidclient.wallet.AddressBookProvider;
import com.hivewallet.androidclient.wallet.Constants;
import com.hivewallet.androidclient.wallet.Protos.Contact;
import com.hivewallet.androidclient.wallet.Protos.Contact.Builder;

public class FindNearbyContact {
	private static final Logger log = LoggerFactory.getLogger(FindNearbyContact.class);
	
	private String bluetoothAddress = null;
	private String bitcoinAddress = null;
	private String name = null;
	private byte[] photo = null;
	private Uri photoUri = null;
	
	public FindNearbyContact(String bitcoinAddress, String name)
	{
		this(null, bitcoinAddress, name, null);
	}
	
	public FindNearbyContact(String bitcoinAddress, String name, @Nullable byte[] photo)
	{
		this(null, bitcoinAddress, name, photo);
	}
	
	public FindNearbyContact(String bluetoothAddress, String bitcoinAddress, String name, byte[] photo)
	{
		this.bluetoothAddress = bluetoothAddress;
		this.bitcoinAddress = bitcoinAddress;
		this.name = name;
		this.photo = photo;
	}
	
	public void writeDelimitedTo(OutputStream output) throws IOException
	{
		Contact contact = toProtos();
		contact.writeDelimitedTo(output);
	}
	
	public String toString()
	{
		return toProtos().toString();
	}
	
	public String getName()
	{
		return name;
	}
	
	public String getBluetoothAddress()
	{
		return bluetoothAddress;
	}
	
	public byte[] getPhoto()
	{
		return photo;
	}
	
	public String getBitcoinAddress()
	{
		return bitcoinAddress;
	}
	
	/** Note: Needs to be initialized first by calling {@code preparePhotoUri()}. **/
	public Uri getPhotoUri()
	{
		return photoUri;
	}
	
	/** Saves the contact photo - if present - to a file in private storage and
	 * makes sure the object can provide a URI for it.
	 */	
	public void preparePhotoUri(Context context)
	{
		if (photo == null)
			return;
		
		Bitmap bitmapOriginal = BitmapFactory.decodeByteArray(photo, 0, photo.length);
		if (bitmapOriginal == null)
			return;
		
		Bitmap bitmapScaled = AddressBookProvider.ensureReasonableSize(bitmapOriginal);
		if (bitmapScaled == null)
			return;
		
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		bitmapScaled.compress(CompressFormat.PNG, 100, outStream);
		byte[] photoScaled = outStream.toByteArray();
		
		try {
			/* for some reason calling DigestUtils.sha1Hex() does not work on Android */
			String hash = new String(Hex.encodeHex(DigestUtils.sha1(photoScaled))); 
			
			/* create photo asset and database entry */
			File dir = context.getDir(Constants.PHOTO_ASSETS_FOLDER, Context.MODE_PRIVATE);
			File photoAsset = new File(dir, hash + ".png");
			photoUri = Uri.fromFile(photoAsset);
			boolean alreadyPresent = AddressBookProvider.insertOrUpdatePhotoUri(context, photoUri);
			if (!alreadyPresent) {
				FileUtils.writeByteArrayToFile(photoAsset, photoScaled);
				log.info("Saved photo asset with uri {}", photoUri);
			}
			
			/* use opportunity to clean up photo assets */
			List<Uri> stalePhotoUris = AddressBookProvider.deleteStalePhotoAssets(context);
			for (Uri stalePhotoUri : stalePhotoUris) {
				File stalePhotoAsset = new File(stalePhotoUri.getPath());
				FileUtils.deleteQuietly(stalePhotoAsset);
				log.info("Deleting stale photo asset with uri {}", stalePhotoUri);
			}
		} catch (IOException ignored) {}
	}
	
	private Contact toProtos() {
		Builder builder = Contact.newBuilder()
				.setVersionMajor(1)
				.setBitcoinAddress(bitcoinAddress)
				.setName(name);
		
		if (photo != null)
			builder.setPhoto(ByteString.copyFrom(photo));
		
		return builder.build();
	}
	
	public static FindNearbyContact parseDelimitedFrom(String bluetoothAddress, InputStream input) throws IOException
	{
		Contact contact = Contact.parseDelimitedFrom(input);
		
		String bitcoinAddress = contact.getBitcoinAddress();
		String name = "";
		byte[] photo = null;
		
		if (contact.hasName())
			name = contact.getName();
		
		if (contact.hasPhoto())
			photo = contact.getPhoto().toByteArray();
		
		return new FindNearbyContact(bluetoothAddress, bitcoinAddress, name, photo);
	}
}