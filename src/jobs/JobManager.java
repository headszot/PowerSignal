package jobs;

import java.util.HashMap;

import data.Config;
import main.MsgHandler;

public class JobManager 
{
	private static JobManager jman = null;
	
	private HashMap<String,Job> job_tracker = null;
	
	public String activeMonitor = null;
	
	private JobManager()
	{
		job_tracker = new HashMap<String, Job>();
	}
	
	public static JobManager init()
	{
		if (jman == null)
		{
			jman = new JobManager();
		}
		return jman;
	}
	
	public Job[] getJobs()
	{
		Job[] jobs = new Job[job_tracker.size()];
		
		int i=0;
		for (String s : job_tracker.keySet())
		{
			jobs[i] = job_tracker.get(s);
			i++;
		}
		return jobs;
	}
	
	public boolean queueJob(String name, String cmdline, String workingDirectory)
	{
		JobThread jt = new JobThread(name,cmdline,workingDirectory);
		Thread t = new Thread(jt);

		Job job = new Job(name, cmdline);
		job.job_thr = jt;
		job.thr = t;
		
		//keep track of threads
		job_tracker.put(name, job);
	
		//launch thread instance
		t.start();
		
		return true;
	}
	
	public boolean killJob(String name)
	{
		if (!job_tracker.containsKey(name))
		{
			return false;
		}
		Job j = job_tracker.get(name);
		
		System.out.println("JMAN: interrupting "+name);
		Thread jt = j.thr;
		jt.interrupt();
		
		//if current thread is being monitored, interrupt it when watched thread is killed
		stopThreadMonitor(name);

		return true;
	}
	
	public boolean sendInput(String name, String input)
	{
		if (!job_tracker.containsKey(name))
		{
			return false;
		}
		
		JobThread jt = job_tracker.get(name).job_thr;
		jt.sendInput(input+"\n");
		return true;
	}
	
	public String getThreadBuf(String name)
	{
		JobThread jt = job_tracker.get(name).job_thr;
		if (jt == null)
		{
			//job already terminated
			return null;
		}
		
		
		String s[] = jt.getOut();
		StringBuilder cmdout = new StringBuilder("");
		
		for (int i=0; i<s.length; i++)
		{
			cmdout.append(s[i]+"\\n");
		}
		
		if (cmdout.length() > Config.BLOCKSIZE)
		{
			String out = "<--Output Truncated-->\\n"+cmdout.toString();
			return out.substring(0, Config.BLOCKSIZE);
		}
		
		return cmdout.toString();
	}
	
	public void completeJob(String name)
	{
		System.err.println("JMAN Completed: "+name);
		if (job_tracker.containsKey(name))
		{
			String out = getThreadBuf(name);
			
			//if current thread is being monitored, interrupt it when watched thread is killed
			stopThreadMonitor(name);
			
			MsgHandler.inst().sendMessage(out);
			job_tracker.remove(name);
		}
	}
	
	public void startThreadMonitor(String name)
	{
		if (!job_tracker.containsKey(name))
		{
			System.err.println("Job thread does not exist!");
			return;
		}
		
		Thread monthread = new Thread(new InteractiveThread(name));
		job_tracker.get(name).int_thr = monthread;
		monthread.start();
		
		this.activeMonitor = name;
	}
	
	public void stopThreadMonitor(String name)
	{
		Job j = job_tracker.get(name);
		if (j.int_thr != null)
		{
			j.int_thr.interrupt();
		}
		this.activeMonitor = null;
	}
	
}
