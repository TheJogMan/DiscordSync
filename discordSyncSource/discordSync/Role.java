package discordSync;

import net.luckperms.api.*;
import net.luckperms.api.model.group.*;
import org.bukkit.configuration.*;

import java.util.*;

public class Role
{
	public static final String ROLE_LIST = "roles";
	public static final String DISCORD_ROLE_ID = "discord-role-id";
	public static final String LUCK_PERMS_GROUP_NAME = "luck-perms-group-name";
	
	static final ArrayList<Role> roles = new ArrayList<>();
	
	public static void loadRoleList(DiscordSync plugin)
	{
		roles.clear();
		ConfigurationSection roleList = plugin.getConfig().getConfigurationSection(ROLE_LIST);
		Set<String> keys = roleList.getKeys(false);
		for (String key : keys)
		{
			ConfigurationSection role = roleList.getConfigurationSection(key);
			roles.add(new Role(plugin, key, role.getLong(DISCORD_ROLE_ID), role.getString(LUCK_PERMS_GROUP_NAME)));
		}
	}
	
	public static int roleCount()
	{
		return roles.size();
	}
	
	public static Role getRole(int index)
	{
		return roles.get(index);
	}
	
	public static Role getRoleByName(String name)
	{
		for (Role role : roles)
			if (role.name.equals(name))
				return role;
		return null;
	}
	
	public static Role getRoleByID(long discordRoleID)
	{
		for (Role role : roles)
			if (role.discordRoleID == discordRoleID)
				return role;
		return null;
	}
	
	public static Role getRoleByGroup(String luckPermsGroupName)
	{
		for (Role role : roles)
			if (role.luckPermsGroupName.equals(luckPermsGroupName))
				return role;
		return null;
	}
	
	final String name;
	final long discordRoleID;
	final String luckPermsGroupName;
	final DiscordSync plugin;
	
	net.dv8tion.jda.api.entities.Role discordRole;
	
	private Role(DiscordSync plugin, String name, long discordRoleID, String luckPermsGroupName)
	{
		this.plugin = plugin;
		this.name = name;
		this.discordRoleID = discordRoleID;
		this.luckPermsGroupName = luckPermsGroupName;
		
		discordRole = plugin.bot().jda.getRoleById(discordRoleID);
	}
	
	public net.dv8tion.jda.api.entities.Role getDiscordRole()
	{
		return discordRole;
	}
	
	public Group getLuckPermsGroup()
	{
		return LuckPermsProvider.get().getGroupManager().getGroup(luckPermsGroupName);
	}
}