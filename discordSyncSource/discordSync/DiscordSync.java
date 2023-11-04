package discordSync;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.*;

import java.io.*;

public class DiscordSync extends JavaPlugin implements Listener
{
	public static final String DISCORD_BOT_TOKEN = "discord-bot.token";
	final DiscordBot bot = new DiscordBot(this);
	
	@Override
	public void onEnable()
	{
		//configuration not set
		if (!getConfig().isSet(DISCORD_BOT_TOKEN))
		{
			saveDefaultConfig();
		}
		bot.enable();
		bot.start();
		
		//ensure userData directory exists
		if (!userDataDirectory().exists() || !userDataDirectory().isDirectory())
			userDataDirectory().mkdir();
		
		//perform sync for all players currently on the server
		for (Player player : Bukkit.getOnlinePlayers())
		{
			User user = new User(this, player.getUniqueId());
			user.sync();
		}
		
		Bukkit.getPluginManager().registerEvents(this, this);
		
		//cull expired link processes each second
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, LinkProcess::cull, 0, 20);
		
		this.getCommand("link-account").setExecutor(new LinkProcess.LinkAccountCommand());
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event)
	{
		//perform sync for players joining the server
		User user = new User(this, event.getPlayer().getUniqueId());
		user.sync();
	}
	
	public DiscordBot bot()
	{
		return bot;
	}
	
	public File userDataDirectory()
	{
		return new File(getDataFolder() + "/userData");
	}
	
	@Override
	public void onDisable()
	{
		bot.stop();
	}
	
	/**
	 * Sends a chat message to all players currently online who have operator permissions
	 * @param message
	 */
	public static void announceToAdmins(String message)
	{
		for (Player player : Bukkit.getOnlinePlayers())
		{
			if (player.isOp())
				player.sendMessage("[DiscordSync] " + message);
		}
	}
}