package discordSync;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.exceptions.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

import java.util.logging.*;

public class DiscordBot implements Listener
{
	public static final String TOKEN = "discord-bot.token";
	
	final DiscordSync plugin;
	JDA jda = null;
	Status status = Status.NOT_RUNNING;
	
	public DiscordBot(DiscordSync plugin)
	{
		this.plugin = plugin;
	}
	
	/**
	 * Called when plugin is enabled to set up minecraft event listeners
	 */
	public void enable()
	{
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}
	
	/**
	 * Gets the current status of the bot
	 * @return
	 */
	public Status status()
	{
		return status;
	}
	
	/**
	 * Stops and then restarts the bot
	 * <p>
	 *     Intended for applying config changes such as a new bot token.
	 * </p>
	 */
	public void reload()
	{
		stop();
		start();
	}
	
	/**
	 * Starts the bot.
	 */
	public void start()
	{
		if (!status.canStart())
			throw new IllegalStateException("Can't start discord bot in its current state.");
		
		try
		{
			plugin.getLogger().log(Level.INFO, "Starting discord bot.");
			jda = JDABuilder.createDefault(plugin.getConfig().getString(TOKEN)).build();
			status = Status.LOADING;
			while (jda.getStatus() != JDA.Status.CONNECTED && status == Status.LOADING)
			{
				try
				{
					jda.awaitReady();
				}
				catch (InterruptedException e)
				{
					//ignore interruptions
				}
				catch (IllegalStateException e)
				{
					e.printStackTrace();
					status = Status.LOAD_ERROR;
				}
			}
			//make sure we didn't encounter an error while loading
			if (status == Status.LOADING)
			{
				status = Status.RUNNING;
				plugin.getLogger().log(Level.INFO, "Discord bot is running.");
				DiscordSync.announceToAdmins("Discord bot is running.");
			}
			else
			{
				plugin.getLogger().log(Level.INFO, "Could not start discord bot.");
				DiscordSync.announceToAdmins("An error occurred while starting the discord bot, check server console for details.");
				jda = null;
			}
		}
		catch (InvalidTokenException | IllegalArgumentException exception)
		{
			status = Status.INVALID_TOKEN;
			plugin.getLogger().log(Level.WARNING, "Discord bot token is invalid, make sure the token is properly set in the config file.");
			DiscordSync.announceToAdmins("Discord bot token is invalid, make sure the token is properly set in the config file.");
			jda = null;
		}
	}
	
	/**
	 * Stops the bot
	 */
	public void stop()
	{
		if (!status.canStop())
			throw new IllegalStateException("Can't stop discord bot in its current state.");
		
		if (jda != null)
		{
			plugin.getLogger().log(Level.INFO, "Shutting down discord bot.");
			jda.shutdown();
			status = Status.SHUTTING_DOWN;
			while (jda.getStatus() != JDA.Status.SHUTDOWN)
			{
				try
				{
					jda.awaitShutdown();
				}
				catch (InterruptedException e)
				{
					//ignore interruptions
				}
			}
			jda = null;
			plugin.getLogger().log(Level.INFO, "Discord bot is shutdown.");
			DiscordSync.announceToAdmins("Discord bot is shutdown.");
		}
		status = Status.NOT_RUNNING;
	}
	
	public enum Status
	{
		NOT_RUNNING(true, false, true, "Discord bot is not currently running."),
		LOAD_ERROR(true, false, true, "An error occurred while loading discord bot, check console for details."),
		LOADING(false, false, false, "Bot is loading."),
		RUNNING(false, true, false, "Bot is running."),
		SHUTTING_DOWN(false, false, false, "Bot is shutting down."),
		INVALID_TOKEN(true, false, true, "Discord bot token is invalid, make sure token is properly set in config file.");
		
		final boolean canStop;
		final boolean canStart;
		final String message;
		final boolean sendMessageToAdmins;
		
		Status(boolean canStart, boolean canStop, boolean sendMessageToAdmins, String message)
		{
			this.canStart = canStart;
			this.canStop = canStop;
			this.message = message;
			this.sendMessageToAdmins = sendMessageToAdmins;
		}
		
		/**
		 * Indicates if the bot can be started in its current state
		 * @return
		 */
		public boolean canStart()
		{
			return canStart;
		}
		
		/**
		 * Indicates if the bot can be stopped in its current state
		 * @return
		 */
		public boolean canStop()
		{
			return canStop;
		}
		
		/**
		 * Indicates if the status message should be sent to admins when they join the server
		 * @return
		 */
		public boolean sendMessageToAdmins()
		{
			return sendMessageToAdmins;
		}
		
		/**
		 * A description of the current status
		 * @return
		 */
		public String message()
		{
			return message;
		}
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event)
	{
		Player player = event.getPlayer();
		if (status.sendMessageToAdmins() && player.isOp())
			player.sendMessage("[DiscordSync] " + status.message());
	}
}