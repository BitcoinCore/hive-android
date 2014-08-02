package com.hivewallet.androidclient.wallet.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivewallet.androidclient.wallet.Configuration;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.os.Message;

public class FindNearbyBluetoothWorker extends Thread
{
	private static final Logger log = LoggerFactory.getLogger(FindNearbyBluetoothWorker.class);
	
	public static final int MESSAGE_IS_BUSY = 0;
	public static final int MESSAGE_IS_NOT_BUSY = 1;
	public static final int MESSAGE_INFO_RECEIVED = 2;
	public static final int MESSAGE_HEARTBEAT = 3;
	
	private static final String FIND_NEARBY_NAME = "Find Nearby Service";
	private static final UUID FIND_NEARBY_UUID = UUID.fromString("D85FC650-EA77-11E3-AC10-0800200C9A66");
	
	private static final int INITIAL_BACKOFF = 1; /* second */
	private static final int MAXIMUM_BACKOFF = 16; /* seconds */
	
	private static final int BLUETOOTH_TIMEOUT = 10; /* seconds */ 
	
	private static final int COMMAND_BECOME_VISIBLE = 0;
	private static final int COMMAND_BECOME_INVISIBLE = 1;
	private static final int COMMAND_ADD_CANDIDATE = 2;
	private static final int COMMAND_BLUETOOTH_ACTIVITY_INC = 3;
	private static final int COMMAND_BLUETOOTH_ACTIVITY_DEC = 4;

	private boolean isRunning = true;
	private boolean isBusy = false;
	private int bluetoothActivityLevel = 0;
	private final BlockingQueue<FindNearbyCommand> commands = new LinkedBlockingQueue<FindNearbyBluetoothWorker.FindNearbyCommand>();
	
	private final BlockingQueue<String> candidates = new LinkedBlockingQueue<String>();
	
	private final Context context;
	private final BluetoothAdapter bluetoothAdapter;
	private final ContentResolver contentResolver;
	private final Configuration configuration;
	private final Handler handler;
	
	private ServerThread serverThread = null;
	private ReceiveInfoThread receiveInfoThread = null;
	private HeartbeatThread heartbeatThread = null;
	
	private FindNearbyContact userRecord = null;
	private String bitcoinAddress;
	
	public FindNearbyBluetoothWorker(Context context, BluetoothAdapter bluetoothAdapter, ContentResolver contentResolver, Configuration configuration, Handler handler, String bitcoinAddress)
	{
		this.context = context;
		this.bluetoothAdapter = bluetoothAdapter;
		this.contentResolver = contentResolver;
		this.configuration = configuration;
		this.handler = handler;
		this.bitcoinAddress = bitcoinAddress;
	}
	
	@Override
	public void run()
	{
		receiveInfoThread = new ReceiveInfoThread();
		receiveInfoThread.start();
		
		heartbeatThread = new HeartbeatThread();
		heartbeatThread.start();	/* send heartbeat every second */
		
		while (isRunning)
			handleCommand();
	}
	
	private void handleCommand() {
		FindNearbyCommand command = null;
		try {
			command = commands.poll(5, TimeUnit.SECONDS);
		} catch (InterruptedException ignored) {}
		
		if (command == null)
			return;
		
		switch (command.getType()) {
			case COMMAND_BECOME_VISIBLE:
				handleBecomeVisible();
				break;
			case COMMAND_BECOME_INVISIBLE:
				handleBecomeInvisible();
				break;
			case COMMAND_ADD_CANDIDATE:
				AddCandidateCommand addCandidateCommand = (AddCandidateCommand)command;
				handleAddCandidate(addCandidateCommand.getAddress());
				break;
			case COMMAND_BLUETOOTH_ACTIVITY_INC:
				bluetoothActivityLevel += 1;
				checkBluetoothActivityLevel();
				break;
			case COMMAND_BLUETOOTH_ACTIVITY_DEC:
				bluetoothActivityLevel -= 1;
				checkBluetoothActivityLevel();
				break;
		}
	}	
	
	public void becomeVisible() {
		try {
			commands.put(new SimpleCommand(COMMAND_BECOME_VISIBLE));
		}
		catch (InterruptedException e) { throw new RuntimeException(e); }
	}
	
	private void handleBecomeVisible()
	{
		if (serverThread != null && serverThread.isRunning())
			return;
		
		if (serverThread != null)
			serverThread.cancel();
		serverThread = new ServerThread();
		serverThread.start();
	}
	
	public void becomeInvisible() {
		commands.add(new SimpleCommand(COMMAND_BECOME_INVISIBLE));
	}			
	
	private void handleBecomeInvisible()
	{
		if (serverThread == null)
			return;
		
		serverThread.cancel();
	}
	
	public void addCandidate(String address)
	{
		commands.add(new AddCandidateCommand(address));
	}
	
	private void handleAddCandidate(String address)
	{
		candidates.add(address);
	}
	
	private void incBluetoothActivity()
	{
		commands.add(new SimpleCommand(COMMAND_BLUETOOTH_ACTIVITY_INC));
	}
	
	private void decBluetoothActivity()
	{
		commands.add(new SimpleCommand(COMMAND_BLUETOOTH_ACTIVITY_DEC));
	}
	
	private void checkBluetoothActivityLevel()
	{
		if (!isBusy && bluetoothActivityLevel > 0) {
			/* going from not busy to busy */
			isBusy = true;
			Message msg = handler.obtainMessage(MESSAGE_IS_BUSY);
			handler.sendMessage(msg);
		} else if (isBusy && bluetoothActivityLevel == 0) {
			/* going from busy to not busy */
			isBusy = false;
			Message msg = handler.obtainMessage(MESSAGE_IS_NOT_BUSY);
			handler.sendMessage(msg);
		}
	}
	
	private void reportReceivedContact(FindNearbyContact contact)
	{
		/* prepare contact photo uri, if present, before passing it on to the UI */
		contact.preparePhotoUri(context);
		
		Message msg = handler.obtainMessage(MESSAGE_INFO_RECEIVED);
		msg.obj = contact;
		handler.sendMessage(msg);
	}
	
	public void shutdown() {
		isRunning = false;
		becomeInvisible();
	}
	
	private interface FindNearbyCommand
	{
		public int getType();
	}
	
	private class SimpleCommand implements FindNearbyCommand
	{
		private int type;
		
		public SimpleCommand(int type)
		{
			this.type = type;
		}
		
		@Override
		public int getType()
		{
			return type;
		}
	}
	
	private class AddCandidateCommand implements FindNearbyCommand
	{
		private String address;
		
		public AddCandidateCommand(String address)
		{
			this.address = address;
		}
		
		@Override
		public int getType()
		{
			return COMMAND_ADD_CANDIDATE;
		}
		
		public String getAddress()
		{
			return address;
		}
	}
	
	private class ServerThread extends Thread {
		private BluetoothServerSocket serverSocket;
		private boolean serverIsRunning = true;
		
		public void run()
		{
			if (userRecord == null)
				userRecord = FindNearbyContact.lookupUserRecord(contentResolver, configuration, bitcoinAddress);
			
			if (userRecord == null) { /* still no user record? abort */
				log.warn("Unable to lookup user details for broadcasting.");
				serverIsRunning = false;
			}
			
			BluetoothSocket clientSocket = null;
			int currentBackoff = INITIAL_BACKOFF;
			while (serverIsRunning) {
				try
				{
					serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(FIND_NEARBY_NAME, FIND_NEARBY_UUID);
					clientSocket = serverSocket.accept();
					serverSocket.close();	/* stop server while we serve the client */
					
					if (clientSocket != null) {
						currentBackoff = INITIAL_BACKOFF;	/* seems like we connected successfully */

						sendInfo(clientSocket);
					}
				}
				catch (IOException e)
				{
					if (serverIsRunning) {
						try { Thread.sleep(currentBackoff * 1000); }
						catch (InterruptedException ignored) {}
						
						currentBackoff *= 2;
						if (currentBackoff >= MAXIMUM_BACKOFF) currentBackoff = MAXIMUM_BACKOFF;
					} // else: ignore exception, we are shutting down
				}
			}			
		}
		
		private void sendInfo(BluetoothSocket clientSocket) {
			incBluetoothActivity();
			try {
				/* make sure that this client does not block us forever */
				Thread timeoutThread = new TimeoutThread(clientSocket);
				timeoutThread.start();
				
				log.info("Sending contact data to: {}", clientSocket.getRemoteDevice().getName());
				OutputStream outStream = clientSocket.getOutputStream();
				userRecord.writeDelimitedTo(outStream);
				
				try {
					InputStream inStream = clientSocket.getInputStream();
					inStream.read();	// block until the socket is closed
				} catch (IOException ignored) {};
			} catch (IOException e) {
				/* something went wrong, give up on this client */
			} finally {
				try {
					clientSocket.close();
				} catch (IOException ignored) {}
			}
			decBluetoothActivity();
		}
		
		public void cancel() {
			serverIsRunning = false;
			try {
				if (serverSocket != null)
					serverSocket.close();
			} catch (IOException ignored) {}
		}
		
		public boolean isRunning() {
			return isRunning;
		}
	}
	
	private class ReceiveInfoThread extends Thread {
		@Override
		public void run()
		{
			while (isRunning)
				handleCandidate();
		}
		
		private void handleCandidate() {
			String address = null;
			try {
				address = candidates.poll(5, TimeUnit.SECONDS);
			} catch (InterruptedException ignored) {}
			
			if (address == null)
				return;
			
			incBluetoothActivity();
			BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
			BluetoothSocket clientSocket = null;
			
			try
			{
				clientSocket = device.createInsecureRfcommSocketToServiceRecord(FIND_NEARBY_UUID);
				clientSocket.connect();
				
				/* make sure that this client does not block us forever */
				Thread timeoutThread = new TimeoutThread(clientSocket);
				timeoutThread.start();				
				
				InputStream inStream = clientSocket.getInputStream();
				FindNearbyContact contact = FindNearbyContact.parseDelimitedFrom(address, inStream);
				
				reportReceivedContact(contact);
			} catch (IOException e) {
				/* something went wrong, give up on this client */
			} finally {
				if (clientSocket != null) {
					try	{
						clientSocket.close();
					} catch (IOException ignored) {}
				}
			}
			decBluetoothActivity();
		}
	}
	
	/** Calls close() on the supplied socket after some time. */ 
	private class TimeoutThread extends Thread {
		private BluetoothSocket socket;
		
		public TimeoutThread(BluetoothSocket socket)
		{
			this.socket = socket;
		}
		
		@Override
		public void run()
		{
			try	{
				Thread.sleep(BLUETOOTH_TIMEOUT * 1000);
			} catch (InterruptedException ignored) {}
			
			try {
				socket.close();
			} catch (IOException ignored) {}
		}
	}
	
	private class HeartbeatThread extends Thread {
		public void run() {
			while (isRunning) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ignored) {}
				
				Message msg = handler.obtainMessage(MESSAGE_HEARTBEAT);
				handler.sendMessage(msg);
			}
		}
	}
}
