package data;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import data.Logger.Level;
import main.JsonMsg;

public class Config 
{
	public static String groupID = null; //signal group ID in base64
	public static String host = null; //host running signal-cli JsonRpc daemon
	public static int port = 9999; //JsonRpc service port
	public static boolean debug = true; //log extra debug information
	public static Path cwd = null; //current working directory for all spawned processes
	public static Path attachment_dir = null; //signal-cli attachment directory
	public static int BLOCKSIZE = 8000; //maximum message size permitted
	
	public static Path hashcat_base = null; //base path to hashcat folder
	public static Path wordlist_base = null; //base path to wordlist folder
	public static Path log_path = null; //path to log file
	
	public static boolean configured = false; //flag indicating that group member information has been processed
	public HashMap<String,Member> members = null; //hashmap of group members
	
	private static Config cfg = null; //instance of the Config object
	private static int prog_counter = 0; //iterator for naming newly spawned jobs
	
	public static String getcwd()
	{
		return cwd.toAbsolutePath().toString();
	}
	
	public static int next_prog_id()
	{
		prog_counter = (prog_counter+1) % 10;
		return prog_counter;
	}
	
	public static String[] getAdmins()
	{
		return null;
	}
	
	public static Config init()
	{
		if (cfg == null)
		{
			cfg = new Config();
		}
		return cfg;
	}
	
	/**
	 * Parses initial JSON config file/string and fills required parameters
	 * @param json
	 * @return success/failure
	 */
	public boolean parseConfigJson(String json)
	{
		try {
			JSONObject arg 	= new JSONObject(json);
			groupID 		= arg.getString("groupID");
			host 			= arg.getString("host");
			port 			= arg.getInt("port");
			BLOCKSIZE 		= arg.getInt("maxLength");
			cwd 			= Paths.get(arg.getString("cwd"));
			attachment_dir 	= Paths.get(arg.getString("attachment_dir"));
			hashcat_base 	= Paths.get(arg.getString("hashcat_base"));
			log_path 		= Paths.get(arg.getString("log_path"));
			wordlist_base 	= Paths.get(arg.getString("wordlist_base"));
		}
		catch (JSONException e)
		{
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/**
	 * Process response of the listGroups message, retrieves group member information
	 * and determines access roles
	 * @param json
	 */
	public void processGroups(JsonMsg json)
	{
		JSONArray groups = new JSONArray(json.getProp("result"));
		for (int i=0; i<groups.length(); i++)
		{
			JSONObject group = groups.getJSONObject(i);
			if (group.getString("id").equals(Config.groupID))
			{
				//process members first
				JSONArray members = group.getJSONArray("members");
				for (int j=0; j<members.length(); j++)
				{
					JSONObject member = members.getJSONObject(j);
					String uuid = member.getString("uuid");
					String number = member.getString("number");
					Member m = new Member(uuid, number);
					
					Config.init().members.put(uuid, m);
				}
				
				//admins are always members too
				JSONArray admins = group.getJSONArray("admins");
				for (int j=0; j<admins.length(); j++)
				{
					JSONObject admin = admins.getJSONObject(j);
					String uuid = admin.getString("uuid");
					if (Config.init().members.containsKey(uuid))
					{
						Config.init().members.get(uuid).setIsAdmin(true);
					}
				}
				
				//process complete
				Logger.log(Level.INFO, "Processed group config!");
				Config.configured = true;
				break;
			}
		}
	}
	
	private Config()
	{
		members = new HashMap<String, Member>();
	}
}
