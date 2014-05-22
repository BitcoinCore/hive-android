package com.hivewallet.androidclient.wallet.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivewallet.androidclient.wallet.Constants;

public class AppInstaller extends Thread {
	public static interface AppInstallCallback {
		void installSuccessful(String appId, File unpackDir, File appDir, JSONObject manifest);
		void installFailed(String errMsg);
	}

	private static final Logger log = LoggerFactory.getLogger(AppInstaller.class);
	private static final String APP_PLATFORM_DOWNLOAD_FILE = "app.hiveapp";
	private static final String APP_PLATFORM_UNPACK_FOLDER = "unpacked_app";
	private static final String APP_PLATFORM_MANIFEST_FILE = "manifest.json";
	private static final int BUFFER_SIZE = 4096;
	
	private String urlStr;
	private AppInstaller.AppInstallCallback callback;
	private File dir;
	volatile private boolean isRunning = true;
	
	public AppInstaller(String url, File dir, AppInstaller.AppInstallCallback callback)
	{
		this.urlStr = url;
		this.dir = dir;
		this.callback = callback;
	}
	
	public void cancel() {
		this.isRunning = false;
	}
	
	@Override
	public void run()
	{
		log.info("Starting install for {}", urlStr);
		
		/* 1. Preliminary checks */
		if (urlStr == null) {
			String errMsg = "No app URL provided";
			log.info("Aborting install: {}", errMsg);
			callback.installFailed(errMsg);
			return;
		}
		
		if (!urlStr.toLowerCase(Locale.US).startsWith("http")) {
			String errMsg = "Only http(s) links supported";
			log.info("Aborting install: {}", errMsg);
			callback.installFailed(errMsg);
			return;
		}
		
		URL url = null;
		try { url = new URL(urlStr); } catch (MalformedURLException e) { /* handle below */ }
		if (url == null) {
			String errMsg = "Invalid app URL";
			log.info("Aborting install: {}", errMsg);
			callback.installFailed(errMsg);
			return;
		}
		
		/* 2. Download archive */
		File downloadFile = new File(dir, APP_PLATFORM_DOWNLOAD_FILE);
		HttpURLConnection conn = null;
		FileOutputStream fileOutputStream = null;
		String errMsg = null;
		try
		{
			conn = (HttpURLConnection)url.openConnection();
			
			if (conn.getResponseCode() != HttpURLConnection.HTTP_OK)
				throw new IOException();
			
			InputStream inputStream = conn.getInputStream();
			fileOutputStream = new FileOutputStream(downloadFile);
			
			byte[] buffer = new byte[BUFFER_SIZE];
			int count;
			while ((count = inputStream.read(buffer)) > 0) {
				fileOutputStream.write(buffer, 0, count);
				
				if (!isRunning) {
					errMsg = "Install canceled";
					break;
				}
			}
		}
		catch (IOException e)
		{
			errMsg = "Unable to download app";
		}
		finally
		{
			if (conn != null)
				conn.disconnect();
			if (fileOutputStream != null)
				try { fileOutputStream.close(); } catch (IOException ignored) { }
		}
		
		if (errMsg != null) {
			log.info("Aborting install: {}", errMsg);
			callback.installFailed(errMsg);
			return;
		}
		
		/* 3. Unpack archive */
		errMsg = null;
		File unpackDir = new File(dir, APP_PLATFORM_UNPACK_FOLDER);
		FileInputStream fileInputStream = null;
		FileOutputStream fileOutputStream2 = null;
		ZipInputStream zipInputStream = null;
		try
		{
			FileUtils.deleteQuietly(unpackDir);
			unpackDir.mkdirs();
			
			fileInputStream = new FileInputStream(downloadFile);
			zipInputStream = new ZipInputStream(fileInputStream);
			ZipEntry zipEntry = null;
			
			while ((zipEntry = zipInputStream.getNextEntry()) != null) {
				File zipEntryFile = new File(unpackDir, zipEntry.getName());
				if (!isSubdirectory(unpackDir, zipEntryFile))
					throw new IOException("Security violation?!");

				if (zipEntry.isDirectory()) {
					zipEntryFile.mkdirs();
				} else {
					zipEntryFile.getParentFile().mkdirs();
					
					fileOutputStream2 = new FileOutputStream(zipEntryFile);
					byte[] buffer = new byte[BUFFER_SIZE];
					int count;
					while ((count = zipInputStream.read(buffer)) != -1) {
						fileOutputStream2.write(buffer, 0, count);
					}
					fileOutputStream2.close();
				}
			}
		}
		catch (FileNotFoundException e)
		{
			log.info("Exception while extracting: {}", e.toString());
			errMsg = "Error while extracting archive";
		}
		catch (IOException e)
		{
			log.info("Exception while extracting: {}", e.toString());
			errMsg = "Error while extracting archive";
		}
		finally
		{
			if (fileInputStream != null)
				try { fileInputStream.close(); } catch (IOException ignored) { }
			if (fileOutputStream2 != null)
				try { fileOutputStream2.close(); } catch (IOException ignored) { }
			if (zipInputStream != null)
				try { zipInputStream.close(); } catch (IOException ignored) { }
		}
		
		if (errMsg != null) {
			log.info("Aborting install: {}", errMsg);
			callback.installFailed(errMsg);
			return;
		}
		
		/* 4. Check manifest */
		errMsg = null;
		File manifest = new File(unpackDir, APP_PLATFORM_MANIFEST_FILE);
		JSONObject manifestJSON = null;
		String appId = null;
		File appsDir = new File(dir, Constants.APP_PLATFORM_APP_FOLDER);
		File appDir = null;
		try
		{
			String manifestData = FileUtils.readFileToString(manifest, Charset.defaultCharset());
			manifestJSON = new JSONObject(manifestData);
			
			for (String key : AppPlatformDBHelper.getMinimalManifestKeys()) {
				if (!manifestJSON.has(key))
					throw new JSONException("Missing required key: " + key);
			}
			
			appId = manifestJSON.getString(AppPlatformDBHelper.KEY_ID);
			appDir = new File(appsDir, appId);
			if (!isSubdirectory(appsDir, appDir))
				throw new IOException("App is trying to walk the file system via its id");
			appDir.mkdirs();
		}
		catch (IOException e)
		{
			log.info("Exception while reading manifest: {}", e.toString());
			errMsg = "Malformed manifest";
		}
		catch (JSONException e)
		{
			log.info("Exception while parsing manifest: {}", e.toString());
			errMsg = "Malformed manifest";
		}
		
		if (errMsg != null) {
			log.info("Aborting install: {}", errMsg);
			callback.installFailed(errMsg);
			return;
		}
		
		log.info("Install was successful");
		callback.installSuccessful(appId, unpackDir, appDir, manifestJSON);
	}
	
	private static boolean isSubdirectory(File parent, File child) throws IOException {
		String parentPath = parent.getCanonicalPath();
		String childPath = child.getCanonicalPath();
		return childPath.startsWith(parentPath);
	}
}