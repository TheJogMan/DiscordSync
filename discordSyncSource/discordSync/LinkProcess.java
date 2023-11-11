package discordSync;

import net.dv8tion.jda.api.entities.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.jetbrains.annotations.*;

import java.util.*;

public class LinkProcess
{
	public static final String CONFIRMATION_TIMEOUT = "confirmation-timeout";
	
	/**
	 * Gets the time in milliseconds before a link process expires
	 * @return
	 */
	public static long confirmationTimeout(DiscordSync plugin)
	{
		return plugin.getConfig().getInt(CONFIRMATION_TIMEOUT) * 1000L;
	}
	
	private static final HashMap<Integer, LinkProcess> processes = new HashMap<>();
	
	/**
	 * Gets the link process that has the given code
	 * <p>
	 *     Returns null if no currently active process has the given code.
	 * </p>
	 * @param code
	 * @return
	 */
	public static LinkProcess getProcess(int code)
	{
		LinkProcess process = processes.get(code);
		if (process == null || !process.valid())
			return null;
		else
			return process;
	}
	
	/**
	 * removes expired processes from the HashMap
	 */
	public static void cull()
	{
		ArrayList<LinkProcess> expiredProcesses = new ArrayList<>();
		for (LinkProcess process : processes.values())
			if (!process.valid())
				expiredProcesses.add(process);
		for (LinkProcess expiredProcess : expiredProcesses)
			processes.remove(expiredProcess);
	}
	
	static final Random random = new Random();
	
	Member initiator;
	int confirmationCode;
	long initiationTime;
	DiscordSync plugin;
	boolean completed = false;
	
	public LinkProcess(DiscordSync plugin, Member initiator)
	{
		this.plugin = plugin;
		this.initiator = initiator;
		confirmationCode = random.nextInt(10000, 100000);
		initiationTime = System.currentTimeMillis();
		
		processes.put(confirmationCode, this);
	}
	
	public int getConfirmationCode()
	{
		return confirmationCode;
	}
	
	/**
	 * Gets the discord guild member that initiated this link process
	 * @return
	 */
	public Member initiator()
	{
		return initiator;
	}
	
	/**
	 * Gets the time when this process was initiated
	 * @return
	 */
	public long getInitiationTime()
	{
		return initiationTime;
	}
	
	/**
	 * Checks if this process is still active
	 * @return
	 */
	public boolean valid()
	{
		return !completed && (System.currentTimeMillis() - initiationTime) < confirmationTimeout(plugin);
	}
	
	/**
	 * Complete this link process with the given minecraft player
	 * @param player
	 */
	public void complete(Player player)
	{
		completed = true;
		User user = new User(plugin, player.getUniqueId());
		user.setDiscordUuid(initiator.getIdLong());
		player.sendMessage("[DiscordSync] Linked to " + initiator.getEffectiveName() + " for syncing.");
		initiator.getUser().openPrivateChannel().onSuccess(channel -> channel.sendMessage("Linked to " + player.getName() + " for syncing.").queue()).queue();
	}
	
	public static class LinkAccountCommand implements CommandExecutor
	{
		@Override
		public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args)
		{
			if (!(sender instanceof Player))
			{
				sender.sendMessage("[DiscordSync] You must be a player to use this command.");
				return false;
			}
			
			if (args.length == 1)
			{
				try
				{
					int code = Integer.parseInt(args[0]);
					LinkProcess process = LinkProcess.getProcess(code);
					if (process != null)
						process.complete((Player)sender);
					else
						sender.sendMessage("[DiscordSync] That code does not match a valid and active link process, make sure you typed the code correctly and try again. If your code has expired " +
										   "or you have lost the code, just re-run the command in discord to get a new code.");
					return true;
				}
				catch (Exception ignored)
				{
				
				}
			}
			sender.sendMessage("[DiscordSync] You must provide a valid code.");
			return false;
		}
	}
}