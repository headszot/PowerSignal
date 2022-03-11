package data;

public class Member 
{
	public boolean isAdmin;
	public String uuid;
	public String number;
	
	public Member(String uuid, String number)
	{
		this.uuid = uuid;
		this.number = number;
		this.isAdmin = false;
	}
	
	public void setIsAdmin(boolean flag)
	{
		this.isAdmin = flag;
	}
}
