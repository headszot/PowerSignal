package main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import data.Logger;
import data.Logger.Level;

public class MsgThread implements Runnable
{
	private String host;
	private int port;
	
	public MsgThread(String host, int port)
	{
		this.port = port;
		this.host = host;
	}
	
	public void run()
	{
		Socket sock = null;
		BufferedReader reader = null;
		MsgHandler msg_h = null;
		InputStream is = null;
		
		//connection loop
		do
		{
			try {
				//connect to signal JSON-RPC
				sock = new Socket(host, port);
				
				is = sock.getInputStream();
				reader = new BufferedReader(new InputStreamReader(is));
				
				OutputStream os = sock.getOutputStream();
				msg_h = MsgHandler.inst(os);
				
			} 
			catch (UnknownHostException e) {
				Logger.log(Level.CRITICAL,"Unknown host: "+host);
				return;
			} 
			catch (IOException e) {
				Logger.log(Level.CRITICAL, String.format("Failed to connect to %s:%d",host,port));
				System.err.println(String.format("Failed to connect to %s:%d",host,port));
				
				//wait before retrying
				try {
					sock = null;
					Thread.sleep(5000);
					System.err.println(String.format("Retrying..."));
				} catch (InterruptedException i) {
					Logger.log(Level.CRITICAL, String.format("Received interrupt while starting retrying connection."));
					return;
				}
			}
		}
		while (sock == null);
		
		Logger.log(Level.INFO, "Entering message queue");
		
		try
		{
			//message loop
			while(true)
			{
				//get JSON from signal daemon
				String json = reader.readLine();
				JsonMsg jmsg = new JsonMsg(json);
				
				if (msg_h.handleMsg(jmsg) == -1)
				{
					Logger.log(Level.INFO, "Received quit!");
					break;
				}
			}
			sock.close();
		}
		catch (IOException e)
		{
			Logger.log(Level.CRITICAL, String.format("Unexpected error! Terminating!"));
			Logger.log(Level.CRITICAL, e.getMessage());
		}
	}
}
