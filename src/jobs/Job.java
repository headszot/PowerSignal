package jobs;

public class Job 
{
	public String name;
	public String cmdline;
	
	//jobthread instance
	public Thread thr = null;
	//interactive thread instance
	public Thread int_thr = null;
	//jobthread reference
	public JobThread job_thr = null;
	
	public Job(String name, String cmdline)
	{
		this.name = name;
		this.cmdline = cmdline;
	}
}
