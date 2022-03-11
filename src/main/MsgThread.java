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
		try {
			//connect to signal JSON-RPC
			Socket sock = new Socket(host, port);
			
			InputStream is = sock.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			
			OutputStream os = sock.getOutputStream();
			MsgHandler msg_h = MsgHandler.inst(os);
			
			Logger.log(Level.INFO, "Entering message queue");
			
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
		catch (UnknownHostException e) {
			Logger.log(Level.CRITICAL,"Unknown host: "+host);
			return;
		} 
		catch (IOException e) {
			String err = String.format("Failed to connect to %s:%d",host,port);
			Logger.log(Level.CRITICAL, String.format("Failed to connect to %s:%d",host,port));
			System.err.println(err);
		}
	}
}
