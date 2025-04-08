package com.talt.serverPassword;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import com.talt.serverPassword.commands.Commands;
import com.talt.serverPassword.events.PasswordEvents;

public class ServerPassword extends JavaPlugin{

	private PasswordEvents passwordEvents;
	
	@Override
	public void onEnable() {
		setupFiles();
		passwordEvents = new PasswordEvents(this);
        getServer().getPluginManager().registerEvents(passwordEvents, this);
        passwordEvents.schedulePeriodicSave();
        Commands commands = new Commands(passwordEvents);
        this.getCommand("setPlayerAttempts").setExecutor(commands);
        this.getCommand("verify").setExecutor(commands);
        this.getCommand("unverify").setExecutor(commands);
        this.getCommand("setServerPassword").setExecutor(commands);
        this.getCommand("audit").setExecutor(commands);
        
        if (this.getCommand("setPlayerAttempts") == null) {
            getLogger().severe("Command 'setPlayerAttempts' failed to register!");
        }
	}
	
	@Override
	public void onDisable() {
		passwordEvents.savePlayerData();
	}
	
	public void setupFiles() {
		if (!getDataFolder().exists()) {
	        if (getDataFolder().mkdirs()) {
	            getLogger().info("Plugin folder created.");
	        } else {
	            getLogger().severe("Failed to create plugin folder!");
	        }
	    }
		saveDefaultConfig();
		if(getDataFolder().exists()) {
			File jsonFile = new File(getDataFolder(), "verifiedPlayers.json");
			if(!jsonFile.exists()) {
				try {
					jsonFile.createNewFile();
					getLogger().info("Verified Players JSON created.");
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		}
	}
}
