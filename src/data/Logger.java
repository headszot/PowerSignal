package data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger 
{
	private static Logger logger = null;
	private File logFile = null;
	private FileOutputStream fio = null;
	
	public enum Level {
		DEBUG,
		INFO,
		ERROR,
		CRITICAL
	}
	
	private Logger()
	{
		
	}
	
	public void setLogFilePath(Path path)
	{
		logFile = path.toFile();
		
		if (!logFile.exists())
		{
			try {
				logFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static Logger inst()
	{
		if (logger == null)
		{
			logger = new Logger();
		}
		return logger;
	}
	
	public static boolean log(Level level, String msg)
	{
		if (logger.logFile == null)
		{
			System.err.println("[-] Logger not initialized!");
			return false;
		}
		try {
			//append to log file
			logger.fio = new FileOutputStream(logger.logFile, true);
			
			SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//dd/MM/yyyy
		    String strDate = sdfDate.format(new Date());
			
			String logstr = String.format("%s : [%s] : %s\n", strDate, level, msg);
			logger.fio.write(logstr.getBytes());
			
			logger.fio.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return true;
	}
}
