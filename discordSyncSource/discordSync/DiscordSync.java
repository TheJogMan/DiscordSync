package discordSync;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.*;
import org.jetbrains.annotations.*;

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
		
		getCommand("link-account").setExecutor(new LinkProcess.LinkAccountCommand());
		getCommand("view-profile").setExecutor(new User.ViewProfileCommand(this));
		getCommand("list-profiles").setExecutor(new ListProfilesCommand(this));
		
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
	
	/**
	 * Provides the instance of the discord bot
	 * @return
	 */
	public DiscordBot bot()
	{
		return bot;
	}
	
	/**
	 * Gets the folder that user data files are stored in
	 * @return
	 */
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
				player.sendMessage("§6[DiscordSync]§r " + message);
		}
	}
	
	public static class ListProfilesCommand implements CommandExecutor
	{
		final DiscordSync plugin;
		
		public ListProfilesCommand(DiscordSync plugin)
		{
			this.plugin = plugin;
		}
		
		@Override
		public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args)
		{
			User[] users = User.getUsers(plugin);
			StringBuilder builder = new StringBuilder();
			builder.append("§6[DiscordSync] Users: (MC Name: Linked)§r");
			for (User user : users)
				builder.append("\n").append(user.getLastSeenMinecraftName()).append(": ").append(user.isLinked() ? "Yes" : "No");
			builder.append("\n§6Run the §b/view-profile <MC Name>§6 command to view more information about a profile.");
			sender.sendMessage(builder.toString());
			return true;
		}
	}
}