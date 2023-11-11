package discordSync;

import net.dv8tion.jda.api.entities.*;
import net.luckperms.api.*;
import net.luckperms.api.model.group.*;
import net.luckperms.api.model.user.*;
import net.luckperms.api.node.*;
import net.luckperms.api.query.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

public class User
{
	public static final String DISCORD_UUID = "discord-uuid";
	public static final String LAST_SEEN_MINECRAFT_NAME = "last-seen-minecraft-name";
	
	final DiscordSync plugin;
	
	final UUID minecraftUUID;
	FileConfiguration data;
	
	public User(DiscordSync plugin, UUID minecraftUUID)
	{
		this.plugin = plugin;
		this.minecraftUUID = minecraftUUID;
		if (!dataFile().exists())
		{
			try
			{
				data.loadFromString(getDefaultUserData());
				data.save(dataFile());
			}
			catch (InvalidConfigurationException | IOException e)
			{
				throw new RuntimeException(e);
			}
		}
		data = YamlConfiguration.loadConfiguration(dataFile());
	}
	
	/**
	 * Gets the user with the given minecraft name
	 * @param plugin
	 * @param name
	 * @return
	 */
	public static User getByPlayerName(DiscordSync plugin, String name)
	{
		File[] users = plugin.userDataDirectory().listFiles();
		for (File file : users)
		{
			User user = new User(plugin, UUID.fromString(file.getName().substring(0, file.getName().length() - 4)));
			if (user.getLastSeenMinecraftName().equals(name))
				return user;
		}
		return null;
	}
	
	/**
	 * Gets the user with the given discord ID
	 * @param plugin
	 * @param discordUUID
	 * @return
	 */
	public static User getByPlayerDiscordID(DiscordSync plugin, long discordUUID)
	{
		File[] users = plugin.userDataDirectory().listFiles();
		for (File file : users)
		{
			User user = new User(plugin, UUID.fromString(file.getName().substring(0, file.getName().length() - 4)));
			if (user.getDiscordID() == discordUUID)
				return user;
		}
		return null;
	}
	
	/**
	 * Sets the discord ID for this user
	 * @param discordUuid
	 */
	public void setDiscordUuid(long discordUuid)
	{
		data.set(DISCORD_UUID, discordUuid);
		saveData();
		sync();
	}
	
	public void sync()
	{
		net.luckperms.api.model.user.User user = getLuckPermsUser();
		Member member = getGuildMember();
		OfflinePlayer player = getOfflinePlayer();
		
		//if the player is online, then update the last seen name
		if (player.isOnline())
		{
			data.set(LAST_SEEN_MINECRAFT_NAME, player.getPlayer().getName());
			saveData();
		}
		
		//if the accounts aren't linked then inform the player and cancel the sync
		if (member == null)
		{
			if (player.isOnline())
				player.getPlayer().sendMessage("§b[DiscordSync]§r You need to link your minecraft and discord accounts, run the §b/link-account§r command in discord to begin the process.");
			return;
		}
		
		//make sure the player has all the roles that they have from discord
		List<net.dv8tion.jda.api.entities.Role> roles = member.getRoles();
		for (net.dv8tion.jda.api.entities.Role discordRole : roles)
		{
			Role role = Role.getRoleByID(discordRole.getIdLong());
			if (role != null)
				giveRole(role, Side.MINECRAFT);
		}
		
		//remove any roles that the player does not have in discord
		Collection<Group> groups = user.getInheritedGroups(QueryOptions.nonContextual());
		for (Group group : groups)
		{
			Role role = Role.getRoleByGroup(group.getName());
			if (role != null && !roles.contains(role.getDiscordRole()))
			{
				removeRole(role, Side.MINECRAFT);
			}
		}
	}
	
	/**
	 * Gets the currently synced roles for this user
	 * @return
	 */
	public Role[] getRoles()
	{
		List<net.dv8tion.jda.api.entities.Role> discordRoles = getGuildMember().getRoles();
		ArrayList<Role> roles = new ArrayList<>();
		for (net.dv8tion.jda.api.entities.Role discordRole : discordRoles)
		{
			Role role = Role.getRoleByID(discordRole.getIdLong());
			if (role != null)
				roles.add(role);
		}
		return roles.toArray(new Role[0]);
	}
	
	/**
	 * Gives a role to this user in both Discord and Minecraft
	 * @param role
	 */
	public void giveRole(Role role)
	{
		giveRole(role, Side.BOTH);
	}
	
	/**
	 * Gives a role to this user on the specified side
	 * @param role
	 * @param side
	 */
	public void giveRole(Role role, Side side)
	{
		if (side.appliesToDiscord)
		{
			Member member = getGuildMember();
			member.getGuild().addRoleToMember(member, role.getDiscordRole());
		}
		
		if (side.appliesToMinecraft)
		{
			net.luckperms.api.model.user.User user = getLuckPermsUser();
			user.data().add(Node.builder("group." + role.getLuckPermsGroup().getName()).build());
			LuckPermsProvider.get().getUserManager().saveUser(user);
		}
	}
	
	/**
	 * Takes a role away from this user in both Discord and Minecraft
	 * @param role
	 */
	public void removeRole(Role role)
	{
		removeRole(role, Side.BOTH);
	}
	
	/**
	 * Takes a role away from this user on the specified side
	 * @param role
	 * @param side
	 */
	public void removeRole(Role role, Side side)
	{
		if (side.appliesToDiscord)
		{
			Member member = getGuildMember();
			member.getGuild().removeRoleFromMember(member, role.getDiscordRole());
		}
		
		if (side.appliesToMinecraft)
		{
			net.luckperms.api.model.user.User user = getLuckPermsUser();
			user.data().remove(Node.builder("group." + role.getLuckPermsGroup().getName()).build());
			LuckPermsProvider.get().getUserManager().saveUser(user);
		}
	}
	
	public enum Side
	{
		MINECRAFT(true, false),
		DISCORD(false, true),
		BOTH(true, true);
		
		final boolean appliesToMinecraft;
		final boolean appliesToDiscord;
		
		Side(boolean appliesToMinecraft, boolean appliesToDiscord)
		{
			this.appliesToMinecraft = appliesToMinecraft;
			this.appliesToDiscord = appliesToDiscord;
		}
	}
	
	/**
	 * Gets the minecraft name this user had when they were last on the server
	 * @return
	 */
	public String getLastSeenMinecraftName()
	{
		return data.getString(LAST_SEEN_MINECRAFT_NAME, "no name found");
	}
	
	/**
	 * Get the player object for this user if they are currently online
	 * @return
	 */
	public Player getPlayer()
	{
		return Bukkit.getPlayer(minecraftUUID);
	}
	
	/**
	 * Get the offline player object for this user
	 * @return
	 */
	public OfflinePlayer getOfflinePlayer()
	{
		return Bukkit.getOfflinePlayer(minecraftUUID);
	}
	
	/**
	 * Checks if this user is online
	 * @return
	 */
	public boolean isOnline()
	{
		return getOfflinePlayer().isOnline();
	}
	
	/**
	 * Get the luck perms user for this user
	 * @return
	 */
	public net.luckperms.api.model.user.User getLuckPermsUser()
	{
		UserManager manager = LuckPermsProvider.get().getUserManager();
		if (!manager.isLoaded(minecraftUUID))
			return manager.loadUser(minecraftUUID).getNow(null);
		else
			return manager.getUser(minecraftUUID);
	}
	
	/**
	 * Check if this user has linked their discord and minecraft accounts
	 * @return
	 */
	public boolean isLinked()
	{
		return getGuildMember() != null;
	}
	
	/**
	 * Get the discord guild member for this user
	 * @return
	 */
	public Member getGuildMember()
	{
		return plugin.bot().guild.retrieveMemberById(getDiscordID()).complete();
	}
	
	/**
	 * Get the ID for the discord guild member for this user
	 * @return
	 */
	public long getDiscordID()
	{
		return data.getLong(DISCORD_UUID, 0);
	}
	
	/**
	 * Get the file this user's data is stored in
	 * @return
	 */
	public File dataFile()
	{
		return new File(plugin.userDataDirectory() + "/" + minecraftUUID + ".txt");
	}
	
	private void saveData()
	{
		try
		{
			data.save(dataFile());
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Gets all the users on this server
	 * @param plugin
	 * @return
	 */
	public static User[] getUsers(DiscordSync plugin)
	{
		File[] userFiles = plugin.userDataDirectory().listFiles();
		User[] users = new User[userFiles.length];
		for (int index = 0; index < users.length; index++)
		{
			users[index] = new User(plugin, UUID.fromString(userFiles[index].getName().substring(0, userFiles[index].getName().length() - 4)));
		}
		return users;
	}
	
	private static String getDefaultUserData()
	{
		YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("/UserDataTemplate.yml"));
		return config.saveToString();
	}
	
	public static class ViewProfileCommand implements CommandExecutor
	{
		final DiscordSync plugin;
		
		public ViewProfileCommand(DiscordSync plugin)
		{
			this.plugin = plugin;
		}
		
		@Override
		public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args)
		{
			if (args.length == 1)
			{
				User user = User.getByPlayerName(plugin, args[0]);
				if (user != null)
				{
					if (user.isLinked())
					{
						StringBuilder builder = new StringBuilder();
						builder.append("§6[DiscordSync]§r ").append(user.getLastSeenMinecraftName()).append("§b is§r @").append(user.getGuildMember().getUser().getName())
							   .append(" §bon Discord, currently with the display name§r ").append(user.getGuildMember().getEffectiveName()).append("§b.\n§6Currently synced roles: (Discord Role: LuckPerms Group)§r");
						Role[] roles = user.getRoles();
						for (Role role : roles)
						{
							builder.append('\n').append(role.getDiscordRole().getName()).append(": ").append(role.getLuckPermsGroup().getName());
						}
						if (roles.length == 0)
							builder.append("\nNone.");
						
						sender.sendMessage(builder.toString());
					}
					else
						sender.sendMessage("§6[DiscordSync]§r " + user.getLastSeenMinecraftName() + "§b has not linked their accounts.");
				}
				else
					sender.sendMessage("§6[DiscordSync]§b There is no profile with that name, the player has either never joined the server before or the name was not typed correctly.");
				return true;
			}
			sender.sendMessage("§6[DiscordSync]§b You must provide a player name.");
			return false;
		}
	}
}