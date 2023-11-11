package discordSync;

import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.*;
import net.dv8tion.jda.api.exceptions.*;
import net.dv8tion.jda.api.hooks.*;
import net.dv8tion.jda.api.interactions.commands.*;
import net.dv8tion.jda.api.interactions.commands.build.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.jetbrains.annotations.*;

import java.time.*;
import java.util.*;
import java.util.logging.*;

public class DiscordBot extends ListenerAdapter implements Listener
{
	public static final String TOKEN = "discord-bot.token";
	public static final String GUILD_ID = "discord-bot.guild-id";
	
	final DiscordSync plugin;
	JDA jda = null;
	Status status = Status.NOT_RUNNING;
	Guild guild = null;
	
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
	 * Called once the bot is logged in and running
	 */
	private void botStarted()
	{
		addCommand("get-guild-id", "Gets the ID for this discord server.", true, DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR),
		(event) ->
		{
			event.reply("This server's ID is `" + event.getGuild().getIdLong() + "'").setEphemeral(true).queue();
		});
		
		addCommand("get-role-id", "Gets the ID for a role.", true, DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR),
		(event) ->
		{
			net.dv8tion.jda.api.entities.Role role = event.getOption("role").getAsRole();
			event.reply(role.getAsMention() + "'s ID is `" + role.getIdLong() + "`").setEphemeral(true).queue();
		}, new OptionData(OptionType.ROLE, "role", "The role to get the ID of.", true));
		
		addCommand("link-account", "Begins the process of linking your minecraft account with your discord account.", false, DefaultMemberPermissions.enabledFor(Permission.EMPTY_PERMISSIONS),
		(event) ->
		{
			LinkProcess process = new LinkProcess(plugin, event.getMember());
			event.reply("Link process initiated, run the command `/link-account " + process.getConfirmationCode() + "` in the minecraft server to link your accounts. This process will " +
						"expire in " + (LinkProcess.confirmationTimeout(plugin) / 60000) + " minutes.").setEphemeral(true).queue();
		});
		
		addCommand("view-profile", "Displays a user's profile.", false, DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR),
		(event) ->
		{
			Member member = event.getOption("user").getAsMember();
			User user = User.getByPlayerDiscordID(plugin, member.getIdLong());
			if (user == null)
				event.reply("This person hasn't linked their minecraft account.").queue();
			else
			{
				event.reply(member.getAsMention() + "'s minecraft UUID is `" + user.getOfflinePlayer().getUniqueId() + "` and their name was `" + user.getLastSeenMinecraftName() + "` when " +
							"they last joined the minecraft server.").setEphemeral(true).queue();
			}
		}, new OptionData(OptionType.USER, "user", "The user who's profile you want to view.", true));
		
		guild = jda.getGuildById(plugin.getConfig().getLong(GUILD_ID));
		if (guild == null)
		{
			plugin.getLogger().log(Level.INFO, "Could not retrieve discord server, make sure the guild-id is properly set in the config.");
			DiscordSync.announceToAdmins("Could not retrieve discord server, make sure the guild-id is properly set in the config. Once the bot is running in your discord server you can run the " +
										 "§b/get-guild-id§r command in your server to get the id.");
		}
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
		
		boolean started = false;
		try
		{
			plugin.getLogger().log(Level.INFO, "Starting discord bot.");
			jda = JDABuilder.createDefault(plugin.getConfig().getString(TOKEN)).addEventListeners(this).build();
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
				started = true;
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
		if (started)
			botStarted();
	}
	
	/**
	 * Stops the bot
	 */
	public void stop()
	{
		if (!status.canStop())
			return;
		
		if (jda != null)
		{
			plugin.getLogger().log(Level.INFO, "Shutting down discord bot.");
			jda.shutdownNow();
			status = Status.SHUTTING_DOWN;
			while (jda.getStatus() != JDA.Status.SHUTDOWN)
			{
				try
				{
					// Allow at most 2 seconds for remaining requests to finish
					if (!jda.awaitShutdown(Duration.ofSeconds(2)))
					{
						jda.shutdownNow(); // Cancel all remaining requests
						break;
					}
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
	
	/**
	 * Runs when a player joins the minecraft server
	 * @param event
	 */
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event)
	{
		Player player = event.getPlayer();
		if (player.isOp())
		{
			if (status.sendMessageToAdmins())
				player.sendMessage("[DiscordSync] " + status.message());
			if (guild == null)
				player.sendMessage("Could not retrieve discord server, make sure the guild-id is properly set in the config. Once the bot is running in your discord server you can run the " +
								   "§b/get-guild-id§r command in your server to get the id.");
		}
	}
	
	/**
	 * Adds a new command to the bot
	 * @param name
	 * @param description
	 * @param guildOnly whether the command can only be used in a discord server, or if it could be used
	 *                  in a private message with the bot.
	 * @param permissions
	 * @param executor
	 * @param options
	 */
	public void addCommand(String name, String description, boolean guildOnly, DefaultMemberPermissions permissions, CommandExecutor executor, OptionData... options)
	{
		jda.upsertCommand(name, description).setDefaultPermissions(permissions).setGuildOnly(guildOnly).addOptions(options).submit().whenComplete((command, exception) ->
		{
			if (command != null)
				commandExecutors.put(command.getIdLong(), executor);
		});
	}
	
	HashMap<Long, CommandExecutor> commandExecutors = new HashMap<>();
	
	/**
	 * Runs when a bot command is executed in discord
	 * @param event
	 */
	@Override
	public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event)
	{
		CommandExecutor executor = commandExecutors.get(event.getCommandIdLong());
		if (executor != null)
			executor.execute(event);
	}
	
	interface CommandExecutor
	{
		void execute(SlashCommandInteractionEvent event);
	}
}