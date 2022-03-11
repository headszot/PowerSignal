package jobs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

import data.Logger;
import data.Logger.Level;

public class JobThread implements Runnable
{
	private String[] cmdline;
	private String friendlyName;
	private String workingDirectory;
	
	private InputStream is = null;
	private OutputStream os = null;
	
	private OutputThread ot = null;
	private BufferedWriter stdin = null;
	
	public JobThread(String name, String cmdline, String workingDirectory)
	{
		this.cmdline = cmdline.split(" ");
		this.friendlyName = name;
		this.workingDirectory = workingDirectory;
	}
	
	public String getName()
	{
		return friendlyName;
	}
	
	public void sendInput(String instr)
	{
		try {
			stdin.write(instr);
			stdin.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public String[] getOut()
	{
		return ot.getbuf();
	}
	
	public void run()
	{
		//setup process builder with provided cmdline and working directory controlled by global static param
		ProcessBuilder pb = new ProcessBuilder(cmdline)
				.redirectErrorStream(true)
				.directory(new File(workingDirectory));
		
		Process process = null;
		try {
			process = pb.start();
		} catch (IOException e1) {
			e1.printStackTrace();
			return;
		}
		Thread outthr = null;
		
		try {
			Logger.log(Level.INFO, "Starting job thread: "+friendlyName);
			
			System.out.println("Starting JT");
			is = process.getInputStream();
			os = process.getOutputStream();
			
			//pass output stream to reader thread
			ot = new OutputThread(is);
			
			//setup input stream writer
			stdin = new BufferedWriter(new OutputStreamWriter(os));
			
			outthr = new Thread(ot);
			outthr.start();
			outthr.join();
			
			is.close();
			os.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			System.out.println("JT: "+friendlyName+" thread caught interrupt!");
			outthr.interrupt();
		}
		process.destroy();
		
		//notify the job manager that this job is complete
		JobManager.init().completeJob(friendlyName);
	}
	
	private class OutputThread implements Runnable
	{
		private InputStream is;
		private ArrayList<String> outbuf;
		
		public OutputThread(InputStream is)
		{
			this.is = is;
			this.outbuf = new ArrayList<String>();
		}
		
		//pops off strings from the output buffer and returns them
		public String[] getbuf()
		{
			String[] s = new String[outbuf.size()];
			for (int i=0; i<outbuf.size(); i++)
			{
				s[i] = outbuf.get(i);
			}
			outbuf.clear();
			return s;
		}
		
		@Override
		public void run() 
		{
			BufferedReader in = new BufferedReader(new InputStreamReader(this.is));
			while (true)
            {
                String line;
				try {
					line = in.readLine();
					if (line == null)
						break;
					outbuf.add(line);
					//System.out.println(line);
				} catch (IOException e) {
					e.printStackTrace();
				}
            }
		}
		
	}
}
