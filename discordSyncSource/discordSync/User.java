package discordSync;

import net.dv8tion.jda.api.entities.*;
import org.bukkit.*;
import org.bukkit.configuration.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;

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
		data = YamlConfiguration.loadConfiguration(dataFile());
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
	
	public static User getByDiscordID(DiscordSync plugin, long discordUUID)
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
		Player player = getPlayer();
		data.set(LAST_SEEN_MINECRAFT_NAME, player.getName());
		saveData();
		
		Member member = getGuildMember();
		if (member == null)
		{
			player.sendMessage("[DiscordSync] You need to link your minecraft and discord accounts, run the §b/link-account§r command in discord to begin the process.");
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
	
	public Member getGuildMember()
	{
		return plugin.bot().guild.getMemberById(getDiscordID());
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
}