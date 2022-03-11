
package main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import data.Config;
import data.Logger;
import data.Logger.Level;

public class SigBot 
{
	public static void main(String[] args) 
	{
		if (args.length == 1)
		{
			String jsonblob = null;
			Path p = null;
			
			try {
				p = Paths.get(args[0]).toAbsolutePath();

				if (p.toFile().exists())
				{
					//parse json from file
					try {
						List<String> lines = Files.readAllLines(p);
						jsonblob = String.join("",lines);
					} catch (IOException e) {
						e.printStackTrace();
					}
					
				}
				else
				{
					System.err.println("Config file not found!");
					return;
				}
			}
			catch (Exception e)
			{
				//not a file, process as cmdline
				jsonblob = args[0];
			}
			if (!Config.init().parseConfigJson(jsonblob))
			{
				return;
			}
		}
		else
		{
			System.err.println("Usage: SigBot.jar <json-config-file>|<json-blob>");
			return;
		}
		
		//init logger
		Logger.inst().setLogFilePath(Config.log_path);
		
		//if custom attachment directory not set, assume default signal-cli folder
		if (Config.attachment_dir.toString().equals(""))
		{
			Config.attachment_dir = Paths.get(System.getenv("USERPROFILE")).resolve("\\.local\\share\\signal-cli\\attachments\\");
		}
		
		Logger.log(Level.INFO, "Using attachments directory: "+Config.attachment_dir);
		Logger.log(Level.INFO, "Connecting to signal-jsonrpc on: "+Config.host+":"+Config.port);
		
		//init message handler thread
		Thread thr = new Thread(new MsgThread(Config.host, Config.port));
		thr.start();
		
		try {
			thr.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		Logger.log(Level.INFO, "Terminating application.");
	}

}
