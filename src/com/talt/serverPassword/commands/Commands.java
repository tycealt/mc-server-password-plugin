package com.talt.serverPassword.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.json.simple.JSONObject;

import com.talt.serverPassword.events.PasswordEvents;

public class Commands implements CommandExecutor {

	private final PasswordEvents events;

    public Commands(PasswordEvents events) {
        this.events = events;
    }
    
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("setPlayerAttempts")) {
            if (!sender.hasPermission("serverpassword.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission!");
                return true;
            }

            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /setPlayerAttempts <player> <attempts>");
                return true;
            }

            int attempts;
            try {
                attempts = Integer.parseInt(args[1]);
                if (attempts < 0) {
                    sender.sendMessage(ChatColor.RED + "Attempts must be 0 or positive!");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid number format for attempts!");
                return true;
            }
            
            JSONObject playerToChange = events.getPlayerByName(args[0]);
            if (playerToChange == null) {
            	sender.sendMessage(ChatColor.RED + "Player is not found in server database.");
            	return true;
            }
            events.addOrUpdatePlayer((String) playerToChange.get("uuid"), (String) playerToChange.get("name"), (boolean) playerToChange.get("verified"), attempts);
            sender.sendMessage(ChatColor.GREEN + "Set " + args[0] + "'s attempts to: " + attempts);
            return true;
        }
		else if (cmd.getName().equalsIgnoreCase("verify")) {
		    if (!sender.hasPermission("serverpassword.admin")) {
		        sender.sendMessage(ChatColor.RED + "You don't have permission!");
		        return true;
		    }

		    if (args.length != 1) {
		        sender.sendMessage(ChatColor.RED + "Usage: /verify <player>");
		        return true;
		    }
		    
		    JSONObject playerData = events.getPlayerByName(args[0]);
		    if (playerData == null) {
		        sender.sendMessage(ChatColor.RED + "Player not found in database!");
		        return true;
		    }
		    
		    events.verifyPlayer((String) playerData.get("uuid"));
		    sender.sendMessage(ChatColor.GREEN + "Verified " + args[0]);
		    return true;
		}
		else if (cmd.getName().equalsIgnoreCase("unverify")) {
			if (!sender.hasPermission("serverpassword.admin")) {
		        sender.sendMessage(ChatColor.RED + "You don't have permission!");
		        return true;
		    }

		    if (args.length != 1) {
		        sender.sendMessage(ChatColor.RED + "Usage: /unverify <player>");
		        return true;
		    }
		    
		    JSONObject playerData = events.getPlayerByName(args[0]);
		    if (playerData == null) {
		        sender.sendMessage(ChatColor.RED + "Player not found in database!");
		        return true;
		    }
		    
		    events.unverifyPlayer((String) playerData.get("uuid"));
		    sender.sendMessage(ChatColor.YELLOW + "Unverified " + args[0]);
		    return true;
		}
		else if (cmd.getName().equalsIgnoreCase("setServerPassword")) {
			if (!sender.hasPermission("serverpassword.admin")) {
		        sender.sendMessage(ChatColor.RED + "You don't have permission!");
		        return true;
		    }

		    if (args.length != 1) {
		        sender.sendMessage(ChatColor.RED + "Usage: /setServerPassword <password>");
		        return true;
		    }
		    
		    events.changePassword(args[0]);
		    sender.sendMessage(ChatColor.GREEN + "Successfuly changed server password to: " + args[0]);
		    return true;
		}
		else if (cmd.getName().equalsIgnoreCase("audit")) {
			if (!sender.hasPermission("serverpassword.admin")) {
		        sender.sendMessage(ChatColor.RED + "You don't have permission!");
		        return true;
		    }

		    if (args.length != 1) {
		        sender.sendMessage(ChatColor.RED + "Usage: /audit <player>");
		        return true;
		    }
		    
		    JSONObject player = events.getPlayerByName(args[0]);
		    if (player == null) {
		    	sender.sendMessage(ChatColor.RED + "Player not found in database!");
		    }
		    sender.sendMessage("Verified: " + player.get("verified") + ", Attempts: " + player.get("attempts"));
		}
        return true;
    }
}

