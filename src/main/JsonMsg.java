
package main;

import org.json.JSONException;
import org.json.JSONObject;

public class JsonMsg 
{
	private String json = null;
	private JSONObject jobj = null;
	
	public JsonMsg(String json)
	{
		this.json = json;
		this.jobj = new JSONObject(json);
	}
	
	public String getJson()
	{
		return this.json;
	}
	
	public boolean isResponse()
	{
		return this.jobj.has("id");
	}
	
	public String toString()
	{
		return jobj.toString();
	}
	
	/**
	 * Take '.' separated list of keys and return the associated property
	 * @param props
	 * @return 
	 */
	public String getProp(String props)
	{
		JSONObject value = jobj;
		
		try 
		{
			if (props.indexOf(".") == -1)
			{
				//get single property
				return value.get(props).toString();
			}
			else
			{
				//parse property values of ABC.XYZ as [abc][xyz] etc
				String [] propnames = props.split("\\.");
				
				for (int i=0; i<propnames.length; i++)
				{
					if (value == null) return null;
					
					//for the last item, return its literal value
					if (i==propnames.length-1)
					{
						return value.has(propnames[i]) ? value.get(propnames[i]).toString() : null;
					}
					else
					{
						value = value.has(propnames[i]) ? value.getJSONObject(propnames[i]) : null;						
					}
				}
				return value.toString();
			}
		}
		catch (JSONException e)
		{
			e.printStackTrace();
			return null;
		}
	}
}
