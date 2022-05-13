
package main;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.Random;
import java.util.Stack;

import org.json.JSONArray;
import org.json.JSONObject;

import data.Config;
import data.Logger;
import data.Logger.Level;
import data.Member;
import jobs.Job;
import jobs.JobManager;

/**
 * Processes messages and sends responses
 * @author Peter
 *
 */
public class MsgHandler 
{
	private static MsgHandler inst = null;
	private JobManager jman = null;
	private Random rand_src = new Random();
	private int cmd_count = 0;
	
	//history
	private Stack<String> cmd_history = null;
	
	private PrintWriter writer;
	
	private MsgHandler(OutputStream os)
	{
		writer = new PrintWriter(os, true);
		
		//perform config init
		sendListGroups();
	}
	
	public static MsgHandler inst(OutputStream os)
	{
		if (inst == null)
		{
			inst = new MsgHandler(os);
			inst.jman = JobManager.init();
			
			inst.cmd_history = new Stack<String>();
		}
		return inst;
	}
	public static MsgHandler inst()
	{
		return inst;
	}
	
	public int handleMsg(JsonMsg json)
	{
		//block all message processing until group data is processed
		if (!Config.configured)
		{
			if (json.getProp("id").equals("signalListGroups"))
				Config.init().processGroups(json);
			else
				return 0;
		}
		
		String message = json.getProp("params.envelope.syncMessage.sentMessage.message");
		String groupID = json.getProp("params.envelope.syncMessage.sentMessage.groupInfo.groupId");
		String sender_uuid = json.getProp("params.envelope.sourceUuid");
		
		Member caller = Config.init().members.get(sender_uuid);
		
		String msg = "";
		
		if (message == null || groupID == null)
		{
			return 0;
		}
		
		//ensure processing of messages from correct group
		if (!groupID.equals(Config.groupID))
		{
			//not our message
			return 0;
		}
		
		if (Config.debug)
		{
			Logger.log(Level.DEBUG, json.getJson());
		}
		
		//if there is an active monitor thread, redirect all input to it
		if (jman.activeMonitor != null)
		{
			//invoking !back or !b will drop the monitor thread
			if (message.equals("!background") || message.equals("!b"))
			{
				jman.stopThreadMonitor(jman.activeMonitor);
			}
			else
			{
				jman.sendInput(jman.activeMonitor, message);
			}
			return 0;
		}
		
		String [] cmds = message.split(" ");
		
		//shortcut commands for jobs e.g. jr1 -> jobs read 1
		if (cmds[0].toLowerCase().matches("j[risk][0-9]") && cmds.length == 1)
		{
			String shorthand = cmds[0].toLowerCase();
			//process shorthand commands
			cmds = new String[3];
			for (int i=0; i<cmds.length; i++)
				cmds[i] = Character.toString(shorthand.charAt(i));
		}
		
		//catch history recall
		if (cmds[0].toLowerCase().matches("![0-9]") && cmds.length == 1)
		{
			int off = Integer.parseInt(cmds[0].substring(1, 2));
			
			message = cmd_history.get(off);
			cmds = message.split(" ");
		}
		
		//record last X supplied commands into history
		cmd_history.push(message);
		cmd_count += 1;
		
		//limit history to configured size by removing oldest entry
		if (cmd_history.size() > Config.HIST_LIMIT) cmd_history.remove(0);
		
		switch (cmds[0].toLowerCase())
		{
		case "quit":
			if (!caller.isAdmin) {
				sendMessage("Admin access required!");
				return 0;
			}
			sendMessage("Terminating Server!");
			return -1;
			
		//force reload of group member configuration
		case "reload":
			if (!caller.isAdmin) {
				sendMessage("Admin access required!");
				return 0;
			}
			Config.configured = false;
			sendListGroups();
			break;
			
		case "role":
			Member m = Config.init().members.get(sender_uuid);
			if (m.isAdmin)
			{
				sendMessage("ADMIN");
			}
			else
			{
				sendMessage("USER");
			}
			return 0;
		
		case "ping":
			msg = String.format("pong : %s",new Date());
			sendMessage(msg);
			break;
			
		case "h":
		case "history":
			String response = "";
			for (int i=0; i<Config.HIST_LIMIT && i<cmd_count && i<cmd_history.size(); i++)
			{
				response += String.format("%d: %s\n", i, cmd_history.get(i));
			}
			sendMessage(response);
			break;
			
		case "help":
		case "?":
			sendMessage(
					"List of available commands:\\n"
				    + "--------------------------\\n"
					+ "ping - check server response\\n"
					+ "role - check user role\\n"
					+ "reload - reload user roles\\n"
					+ "quit - terminate bot\\n"
					+ "ps <powershell command> - run powershell cmd\\n"
					+ "hashcat <int> <int> - runs hashcat on uploaded file\\n"
					+ "[download|dl] <path-to-file> - download file\\n"
					+ "[cwd|cd] <dir> - change working directory\\n"
					+ "pwd - print working directory\\n"
					+ "jobs - list/interact with current jobs\\n"
					+ "[upload|up] <dir> - uploads attached file to dir\\n"
					+ "[history|h] - get powersignal command history\\n"
					+ "\\n"
					);
			break;
			
		case "hashcat":
			if (cmds.length != 3)
			{
				msg = String.format("Usage: hashcat <mode:int> <hashtype:int>\\n"
						+ " - attach your target hashes as a file\\n"
						+ " - default wordlist is rockyou2021.txt\\n");
				sendMessage(msg);
				break;
			}
			else
			{
				int mode = 0, hashtype = 0;
				try 
				{
					mode = Integer.parseInt(cmds[1]);
					hashtype = Integer.parseInt(cmds[2]);
				}
				catch (NumberFormatException e)
				{
					msg = String.format("Input must be a valid integer!");
					sendMessage(msg);
					return 0;
				}
				
				int nextID = Config.next_prog_id();
				String hash_filename = caller.uuid+"-"+nextID+".hash";
				Path hash_folder = Config.hashcat_base.resolve(Paths.get("hashes/"));
				
				//process hash upload and move it to the dedicated hash folder
				if (!processUpload(json, hash_folder, hash_filename, false))
				{
					msg = String.format("Must supply a file containing hashes!");
					sendMessage(msg);
					return 0;
				}
				
				Path hash_absolute = hash_folder.resolve(hash_filename);
				Path wordlist_absolute = Config.wordlist_base.resolve("rockyou2021.txt");
				
				String cmd = String.format("%s --status -O --status-timer 10 -a %d -m %d %s %s", 
						Config.hashcat_base.resolve("hashcat.exe"), //must be launched from within correct working directory anyway
						mode,
						hashtype,
						hash_absolute.toString(),
						wordlist_absolute.toString());
				
				System.out.println(cmd);
				
				String name = Integer.toString(nextID);
				jman.queueJob(name,cmd,Config.hashcat_base.toString());
			}
			break;
			
		//execute powershell command
		case "ps":
			if (!caller.isAdmin) {
				sendMessage("Admin access required!");
				return 0;
			}
			
			if (cmds.length == 1)
			{
				msg = String.format("Usage: ps <powershell command>");
				sendMessage(msg);
				break;
			}
			String fullcmd =""; 
			for (int i=1;i<cmds.length;i++)
			{
				fullcmd += cmds[i];
				if (i!=cmds.length-1)
					fullcmd+=" ";
			}
			
			String cmd = String.format("powershell.exe -c %s", fullcmd);
			String name = Integer.toString(Config.next_prog_id());
			jman.queueJob(name,cmd,Config.cwd.toString());
			break;
		
		//file download functionality
		case "download":
		case "dl":
			if (!caller.isAdmin) {
				sendMessage("Admin access required!");
				return 0;
			}
			if (cmds.length == 1)
			{
				msg = String.format("Usage: [download|dl] <path-to-file>");
				sendMessage(msg);
			}
			else if (cmds.length == 2)
			{
				sendMessage("", cmds[1]);
			}
			break;
			
		//control working directory for process builder
		case "cwd":
		case "cd":
			if (cmds.length == 1)
			{
				msg = String.format("Usage: [cwd|cd] <dir>\\n"
						+ "- cwd : %s",Config.getcwd());
				sendMessage(msg);
			}
			if (cmds.length == 2)
			{
				Path newpath = Paths.get(cmds[1]);
				Path p = Config.cwd.resolve(newpath);
				if (!p.toFile().isDirectory())
				{
					msg = String.format("Usage: [cwd|cd] <dir>");
					sendMessage(msg);
				}
				else
				{
					Config.cwd = p.normalize();
				}
			}
			break;
			
		case "pwd":
			if (cmds.length == 1)
			{
				msg = String.format("%s",Config.getcwd());
				sendMessage(msg);
			}
			break;
			
		case "upload":
		case "up":
			if (!caller.isAdmin) {
				sendMessage("Admin access required!");
				return 0;
			}
			
			if (cmds.length == 2)
			{
				//process potentially multiple uploaded files and notify for each success 
				if (!processUpload(json, Paths.get(cmds[1]), null, true))
				{
					msg = String.format("Must supply a file as an attachment!");
					sendMessage(msg);
					return 0;
				}
			}
			else
			{
				msg = String.format("Usage: [upload|up] <upload-dir>");
				sendMessage(msg);
				break;
			}
			break;
			
		//jobs management
		case "jobs":
		case "j":
			if (cmds.length > 1)
			{
				if (cmds[1].equals("kill") || cmds[1].equals("k"))
				{
					if (cmds.length == 3)
					{
						if (jman.killJob(cmds[2]))
						{
							msg = String.format("Jobs: %s killed successfully!",cmds[2]);
							sendMessage(msg);
						}
						else
						{
							msg = String.format("Jobs: no such job %s!",cmds[2]);
							sendMessage(msg);
						}
					}
					else
					{
						msg = String.format("Usage: [jobs|j] [kill|k] <name>");
						sendMessage(msg);
					}
				}
				else if (cmds[1].equals("send") || cmds[1].equals("s"))
				{
					if (cmds.length > 3)
					{
						String jobname = cmds[2];
						
						String sendcmd = "";
						for (int i=3; i<cmds.length; i++)
						{
							sendcmd += cmds[i];
							if (i != cmds.length-1)
								sendcmd += " ";
						}
						jman.sendInput(jobname, sendcmd);
					}
					else
					{
						msg = String.format("Usage: [jobs|j] [send|s] <jobname> <input...>");
						sendMessage(msg);
					}
				}
				else if (cmds[1].equals("read") || cmds[1].equals("r"))
				{
					if (cmds.length > 2)
					{
						String jobname = cmds[2];
						//retrieve all intermediate/new output from running program
						String buf = jman.getThreadBuf(jobname);
						if (buf == null)
						{
							sendMessage("The specified job does not exist");
						}
						else
						{
							sendMessage(buf);
						}
					}
					else
					{
						msg = String.format("Usage: [jobs|j] [read|r] <jobname>");
						sendMessage(msg);
					}
				}
				else if (cmds[1].equals("interact") || cmds[1].equals("i"))
				{
					if (cmds.length > 2)
					{
						String jobname = cmds[2];
						if (!jman.startThreadMonitor(jobname))
						{
							sendMessage("The specified job does not exist");
						}
					}
					else
					{
						msg = String.format("Usage: [jobs|j] [interact|i] <jobname>");
						sendMessage(msg);
					}
				}
				else
				{
					msg = String.format("Unrecognised command: %s\\n"
							+ "[jobs|j] [send|s] <jobname> <input...>\\n"
							+ "[jobs|j] [read|r] <jobname>\\n"
							+ "[jobs|j] [kill|k] <jobname>\\n"
							+ "[jobs|j] [interact|i] <jobname>\\\\n", 
							cmds[1]);
					sendMessage(msg);
				}
			}
			else //list jobs
			{
				Job jobs[] = jman.getJobs(); String jobl = "";
				if (jobs.length == 0)
				{
					sendMessage("No currently running jobs!");
					break;
				}
				
				for (int i=0; i<jobs.length; i++)
				{
					jobl += jobs[i].name+" - "+jobs[i].cmdline+"\\n";
				}
				msg = String.format(
						"JobID - Cmdline:\\n"
					  + "----------------\\n"
					  + "%s", jobl);
				sendMessage(msg);
			}
			break;
		}
		
		return 0;
	}
	
	public void sendListGroups()
	{
		JSONObject jobj = new JSONObject();
		jobj.put("id", "signalListGroups");
		jobj.put("jsonrpc", "2.0");
		jobj.put("method", "listGroups");
		
		//get configured group details
		this.writer.println(jobj.toString());
	}
	
	/**
	 * Send plain message
	 * @param msg - message to send
	 * @return
	 */
	public int sendMessage(String msg)
	{
		JSONObject jobj_params = new JSONObject();
		jobj_params.put("groupId", Config.groupID);
		jobj_params.put("message", msg);
		
		JSONObject jobj_env = new JSONObject();
		jobj_env.put("id", rand_src.nextInt());
		jobj_env.put("jsonrpc", "2.0");
		jobj_env.put("method", "send");
		jobj_env.put("params", jobj_params);
		
		//unescape \n's
		String finalstr = jobj_env.toString().replaceAll("\\\\\\\\n", "\\\\n");
		
		System.out.println(finalstr);
		
		this.writer.println(finalstr);
		
		return 0;
	}
	
	/**
	 * Download the file specified by the path and return it as an attachment
	 * @param msg - message to send
	 * @param filePath - path to file to upload
	 * @return
	 */
	public int sendMessage(String msg, String filePath)
	{
		if (filePath == null)
			return -1;
		
		Path p1 = Paths.get(filePath);
		Path p_f = Config.cwd.resolve(p1);
		
		File f = p_f.toFile();
		
		if (!f.exists())
		{
			sendMessage("File does not exist!");
			return -1;
		}
		else if (!f.canRead())
		{
			sendMessage("Access denied!");
			return -1;
		}
		else if (!f.isFile())
		{
			sendMessage("Not a file!");
			return -1;
		}
		
		//get absolute path for signal-cli to avoid any issues
		filePath = f.getAbsolutePath();
		
		JSONObject jobj_params = new JSONObject("{\"groupId\":\"\",\"message\":\"\"}");
		jobj_params.put("groupId", Config.groupID);
		jobj_params.put("message", msg);
		jobj_params.put("attachment", filePath);
		
		JSONObject jobj_env = new JSONObject("{\"jsonrpc\":\"2.0\",\"method\":\"send\",\"params\":{},\"id\":0}");
		jobj_env.put("id", rand_src.nextInt());
		jobj_env.put("jsonrpc", "2.0");
		jobj_env.put("method", "send");
		jobj_env.put("params", jobj_params);
		
		//unescape \n's
		String finalstr = jobj_env.toString().replaceAll("\\\\\\\\n", "\\\\n");
		
		this.writer.println(finalstr);
		
		return 0;
	}
	
	public boolean processUpload(JsonMsg json, Path upload_dir, String override_name, boolean notify)
	{
		String attach_json = json.getProp("params.envelope.syncMessage.sentMessage.attachments");
		System.out.println(attach_json);
		if (attach_json == null)
		{
			return false;
		}
		
		if (!Config.attachment_dir.toFile().isDirectory())
		{
			Logger.log(Level.ERROR, "Log directory is not configured correctly!");
			return false;
		}
		
		JSONArray json_arr = new JSONArray(attach_json);
		for (int i=0; i<json_arr.length(); i++)
		{
			JSONObject json_obj = json_arr.getJSONObject(i);
			
			Path attachment_path = Config.attachment_dir.resolve(json_obj.getString("id"));	
			System.out.println("attachment path: "+attachment_path);
			
			Path relative = Config.cwd.resolve(upload_dir);
			String fn = (override_name == null ? json_obj.getString("filename") : override_name);
			Path new_dir = attachment_path.resolve(relative).normalize();
			
			if (!new_dir.toFile().exists())
			{
				return false;
			}
			Path new_filename = new_dir.resolve(fn);
			
			System.out.println("new filename: "+new_filename);
			
			if (!attachment_path.toFile().exists())
			{
				Logger.log(Level.ERROR, "Could not locate attachment: "+attachment_path);
				continue;
			}
			
			try {
				Files.move(attachment_path, new_filename, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			if (notify)
			{
				String msg = String.format("Uploaded file to: %s",new_filename);
				sendMessage(msg);
			}
		}
		return true;
	}
}
