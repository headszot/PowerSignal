package jobs;

import main.MsgHandler;

/**
 * Thread invokes get output buffer periodically to mimic console interaction
 * @author Peter
 *
 */
public class InteractiveThread implements Runnable
{
	private String mon_job;
	
	public InteractiveThread(String mon_job)
	{
		this.mon_job = mon_job;
	}
	
	@Override
	public void run() 
	{
		JobManager jman = JobManager.init();
		try 
		{
			while (true)
			{
				String out = jman.getThreadBuf(mon_job);
				if (out != null)
				{
					if (out.length() > 0)
						MsgHandler.inst().sendMessage(out);
				}
				Thread.sleep(1000);
			}
		} 
		catch (InterruptedException e) 
		{
			System.out.println("Monitor thread was interrupted. Terminating.");
		}
	}
	
}