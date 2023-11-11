package discordSync;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.*;

import java.io.*;

public class DiscordSync extends JavaPlugin implements Listener
{
	final DiscordBot bot = new DiscordBot(this);
	
	@Override
	public void onEnable()
	{
		//configuration not set
		if (!getConfig().isSet(DiscordBot.TOKEN))
		{
			saveDefaultConfig();
		}
		
		bot.enable();
		bot.start();
		
		Role.loadRoleList(this);
		
		//ensure userData directory exists
		if (!userDataDirectory().exists() || !userDataDirectory().isDirectory())
			userDataDirectory().mkdir();
		
		Bukkit.getPluginManager().registerEvents(this, this);
		
		//cull expired link processes each second
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, LinkProcess::cull, 0, 20);
		
		this.getCommand("link-account").setExecutor(new LinkProcess.LinkAccountCommand());
		this.getCommand("view-profile").setExecutor(new User.ViewProfileCommand(this));
		
		//perform sync for all players currently on the server
		for (Player player : Bukkit.getOnlinePlayers())
		{
			User user = new User(this, player.getUniqueId());
			user.sync();
		}
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