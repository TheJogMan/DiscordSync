package discordSync;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.*;

public class DiscordSync extends JavaPlugin
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