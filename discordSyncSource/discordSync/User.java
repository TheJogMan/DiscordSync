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
				player.getPlayer().sendMessage("[DiscordSync] You need to link your minecraft and discord accounts, run the §b/link-account§r command in discord to begin the process.");
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
	
	public void giveRole(Role role)
	{
		giveRole(role, Side.BOTH);
	}
	
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
	
	public void removeRole(Role role)
	{
		removeRole(role, Side.BOTH);
	}
	
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
	
	public String getLastSeenMinecraftName()
	{
		return data.getString(LAST_SEEN_MINECRAFT_NAME, "no name found");
	}
	
	public Player getPlayer()
	{
		return Bukkit.getPlayer(minecraftUUID);
	}
	
	public OfflinePlayer getOfflinePlayer()
	{
		return Bukkit.getOfflinePlayer(minecraftUUID);
	}
	
	public boolean isOnline()
	{
		return getOfflinePlayer().isOnline();
	}
	
	public net.luckperms.api.model.user.User getLuckPermsUser()
	{
		UserManager manager = LuckPermsProvider.get().getUserManager();
		if (!manager.isLoaded(minecraftUUID))
			return manager.loadUser(minecraftUUID).getNow(null);
		else
			return manager.getUser(minecraftUUID);
	}
	
	public boolean isLinked()
	{
		return getGuildMember() != null;
	}
	
	public Member getGuildMember()
	{
		return plugin.bot().guild.retrieveMemberById(getDiscordID()).complete();
	}
	
	public long getDiscordID()
	{
		return data.getLong(DISCORD_UUID, 0);
	}
	
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
						sender.sendMessage("[DiscordSync] " + user.getLastSeenMinecraftName() + " is @" + user.getGuildMember().getUser().getName() + " on Discord, currently with the display name "
										   + user.getGuildMember().getEffectiveName() + ".");
					}
					else
						sender.sendMessage("[DiscordSync] " + user.getLastSeenMinecraftName() + " has not linked their accounts.");
				}
				else
					sender.sendMessage("[DiscordSync] There is no profile with that name, the player has either never joined the server before or the name was not typed correctly.");
				return true;
			}
			sender.sendMessage("[DiscordSync] You must provide a player name.");
			return false;
		}
	}
}