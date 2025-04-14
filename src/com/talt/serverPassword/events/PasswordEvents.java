package com.talt.serverPassword.events;


import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.bukkit.BanList;

import com.talt.serverPassword.ServerPassword;

public class PasswordEvents implements Listener{
	
	private final File jsonFile;
	private final ServerPassword plugin;
	private String pass;
	private int attemptsAllowed;
	private double periodicSaveTime;
	private boolean needsSave;
	private boolean alwaysSave;
	private HashSet<Player> restrictedPlayers = new HashSet<>();
	private HashMap<String, JSONObject> playerDataMap = new HashMap<>();
	private HashMap<String, String> nameToUUIDMap = new HashMap<>();
	
	public PasswordEvents(ServerPassword plugin) {
		this.plugin = plugin;
		validateAndScanConfig();
		jsonFile = new File(plugin.getDataFolder(), "verifiedPlayers.json");
		loadPlayerData();
		needsSave = false;
	}
	
	public void changePassword(String newPass) {
		pass = newPass;
		try {
			plugin.getConfig().set("password", newPass);
			plugin.saveConfig();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void validateAndScanConfig() {
		if (!plugin.getConfig().contains("password") || !plugin.getConfig().contains("allowed-attempts") || !plugin.getConfig().contains("periodic-save-time") || !plugin.getConfig().contains("always-auto-save")) {
			plugin.getLogger().severe("Invalid config detected! Shutting down plugin...");
	        plugin.getServer().getPluginManager().disablePlugin(plugin);
		}
		try {
			pass = plugin.getConfig().getString("password");
			attemptsAllowed = plugin.getConfig().getInt("allowed-attempts");
			periodicSaveTime = plugin.getConfig().getDouble("periodic-save-time");
			alwaysSave = plugin.getConfig().getBoolean("always-auto-save");
		}
		catch (Exception e) {
			plugin.getLogger().severe("Invalid config detected! Shutting down plugin...");
	        plugin.getServer().getPluginManager().disablePlugin(plugin);
		}
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
	    Player player = event.getPlayer();
	    String playerUUID = player.getUniqueId().toString();

	    JSONObject playerJSONData = getPlayer(playerUUID);
	    if (playerJSONData != null && (boolean) playerJSONData.get("verified")) {
	        return;
	    }

	    if (playerJSONData == null) {
	        addOrUpdatePlayer(playerUUID, player.getName(), false, 0);
	        playerJSONData = getPlayer(playerUUID);
	    }
	    
	    player.sendTitle(
	         ChatColor.RED + "SERVER LOCKED",
	         ChatColor.YELLOW + "Enter the password in chat to play",
	         10,  
	         12000,  // 10 minutes
	         0    
	    );
	    
	    restrictedPlayers.add(player);
	}
	
	@EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (restrictedPlayers.contains(event.getPlayer())) {
            if (!event.getFrom().getBlock().equals(event.getTo().getBlock())) {
                event.setTo(event.getFrom());
            }
        }
    }
	
	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		if (restrictedPlayers.contains(event.getPlayer())) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event) {
		if (restrictedPlayers.contains(event.getPlayer())) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (restrictedPlayers.contains(event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }
	
	@EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (restrictedPlayers.contains(event.getPlayer())) {
            event.setCancelled(true); 
        }
    }
	
	@EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            if (restrictedPlayers.contains(attacker)) {
                event.setCancelled(true); 
            }
        }

        if (event.getEntity() instanceof Player) {
            Player target = (Player) event.getEntity();
            if (restrictedPlayers.contains(target)) {
                event.setCancelled(true);
            }
        }
    }
	
	@EventHandler
	public void onItemDrop(PlayerDropItemEvent event) {
		if (restrictedPlayers.contains(event.getPlayer())) {
            event.setCancelled(true);
        }
	}
	
	@EventHandler
	public void onItemPickup(EntityPickupItemEvent event) {
		if (event.getEntity() instanceof Player) {
			Player player = (Player) event.getEntity();
			if (restrictedPlayers.contains(player)) {
	            event.setCancelled(true);
	        }
		}
	}
	
	@EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
		event.setCancelled(true);
		
		if (restrictedPlayers.contains(event.getPlayer())) {
            String attempt = event.getMessage();
            passwordCheck(event.getPlayer(), attempt);
        }
		else {
			String message = String.format("<%s> %s", event.getPlayer().getName(), event.getMessage());
		    Bukkit.getLogger().info(message);
			for (Player player : Bukkit.getOnlinePlayers()) {
		        player.sendMessage(message);
		    }
		}
	}
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
	    Player player = event.getPlayer();
	    restrictedPlayers.remove(player);
	    
	    Bukkit.getScheduler().runTask(plugin, () -> {
	        if (Bukkit.getOnlinePlayers().isEmpty()) {
	            savePlayerData();
	            plugin.getLogger().info("Auto-saved player data (last player left).");
	        }
	    });
	}
	
	public JSONObject getPlayer(String uuid) {
	    return playerDataMap.get(uuid);
	}
	
	public JSONObject getPlayerByName(String name) {
		synchronized (playerDataMap) {
			String uuid = nameToUUIDMap.get(name);
	        return uuid != null ? playerDataMap.get(uuid) : null;
	    }
	}
	
    @SuppressWarnings("unchecked")
    public void addOrUpdatePlayer(String uuid, String name, boolean verified, int attempts) {
    	JSONObject player = playerDataMap.get(uuid);
    	if(player == null)
    	{
    		player = new JSONObject();
    	}
    	player.put("uuid", uuid);
        player.put("name", name);
        player.put("verified", verified);
        player.put("attempts", attempts);      
        playerDataMap.put(uuid, player);
        nameToUUIDMap.put(name, uuid);
        needsSave = true;
    }
    
    public void loadPlayerData() {
        synchronized (playerDataMap) {
            playerDataMap.clear();
            if (!jsonFile.exists()) return;

            try (FileReader reader = new FileReader(jsonFile)) {
                JSONArray playersArray = (JSONArray) new JSONParser().parse(reader);
                for (Object obj : playersArray) {
                    JSONObject player = (JSONObject) obj;
                    playerDataMap.put((String) player.get("uuid"), player);
                    nameToUUIDMap.put((String) player.get("name"), (String) player.get("uuid"));
                }
                plugin.getLogger().info("Loaded " + playerDataMap.size() + " players");
            } catch (Exception e) {
                plugin.getLogger().severe("Load failed: " + e.getMessage());
            }
        }
    }
    public void savePlayerData() {
        synchronized (playerDataMap) {
            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write("[\n");
                boolean first = true;
                
                for (JSONObject playerData : playerDataMap.values()) {
                    if (!first) {
                        writer.write(",\n");
                    }
                    first = false;
                    
                    writer.write("\t{\n"
                        + "\t\t\"name\": \"" + playerData.get("name") + "\",\n"
                        + "\t\t\"uuid\": \"" + playerData.get("uuid") + "\",\n"
                        + "\t\t\"verified\": " + playerData.get("verified") + ",\n"
                        + "\t\t\"attempts\": " + playerData.get("attempts") + "\n"
                        + "\t}");
                }
                writer.write("\n]");
                writer.flush();
                
                needsSave = false;
                plugin.getLogger().info("Saved " + playerDataMap.size() + " players");
            } catch (Exception e) {
                plugin.getLogger().severe("Save failed: " + e.getMessage());
            }
        }
    }
    
    public void schedulePeriodicSave() {
    	long ticks = (long) (periodicSaveTime * 60 * 20);
    	
    	if (periodicSaveTime > 0) {
	        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
	        	if (alwaysSave) {
	        		savePlayerData();
	        	} else if (needsSave) {
	        		savePlayerData();
	        	}
	        	}, ticks, ticks);
    	}
    }
    
    public synchronized void verifyPlayer(String uuid) {
        JSONObject playerJSON = playerDataMap.get(uuid);
        if (playerJSON == null) return;

        String playerName = (String) playerJSON.get("name");
        int attempts;
    	if (playerJSON.get("attempts") instanceof Long) {
    		attempts = ((Long) playerJSON.get("attempts")).intValue();
    	}
    	else {
    		attempts = (int) playerJSON.get("attempts");
    	}
    	addOrUpdatePlayer(uuid, playerName, true, attempts);
        
        Player player = Bukkit.getPlayer(UUID.fromString(uuid));
        if (player != null) {
            restrictedPlayers.remove(player);
            if (player.isOnline()) {
	            player.sendTitle(
	                ChatColor.GREEN + "ACCESS GRANTED", 
	                ChatColor.GRAY + "Welcome!", 
	                10, 40, 10
	            );
            }
        }
    }
    
    public synchronized void unverifyPlayer(String uuid) {
    	JSONObject playerJSON = playerDataMap.get(uuid);
    	if (playerJSON == null) return;
    	
    	String playerName = (String) playerJSON.get("name");
    	
    	int attempts;
    	if (playerJSON.get("attempts") instanceof Long) {
    		attempts = ((Long) playerJSON.get("attempts")).intValue();
    	}
    	else {
    		attempts = (int) playerJSON.get("attempts");
    	}
    	addOrUpdatePlayer(uuid, playerName, false, attempts);
    	
    	Player player = Bukkit.getPlayer(UUID.fromString(uuid));
    	if (player != null && player.isOnline()) {
    		restrictedPlayers.add(player);
    		player.sendTitle(
    		         ChatColor.RED + "SERVER LOCKED",
    		         ChatColor.YELLOW + "Enter the password in chat to play",
    		         10,  
    		         12000,  //10 minutes
    		         0   
    		);
    	}
    }
	
    public void passwordCheck(Player player, String attempt) {
        JSONObject playerData = getPlayer(player.getUniqueId().toString());
        int attemptCount = getAttemptCount(playerData);

        if (attempt.equals(pass)) {
            verifyPlayer(player.getUniqueId().toString());
        } 
        else {
            attemptCount++;
            int attemptsLeft = attemptsAllowed - attemptCount;
            
            if (attemptsAllowed == -1) {
            	Bukkit.getScheduler().runTask(plugin, () -> {
                    player.kickPlayer(
                        ChatColor.RED + "Wrong password."
                    );
            	});
            }
            else if (attemptCount >= attemptsAllowed)
            {
            	Bukkit.getScheduler().runTask(plugin, () -> {
            		
            		Bukkit.getBanList(BanList.Type.NAME).addBan(
            	            player.getName(),
            	            "Too many incorrect password attempts.",
            	            null,
            	            null  
            	        );
            		
            		player.kickPlayer(ChatColor.RED + "Wrong password. You have been banned for too many incorrect passwrod attempts.");
            		
                });
            }
            
            else {
            	Bukkit.getScheduler().runTask(plugin, () -> {
                    player.kickPlayer(
                        ChatColor.RED + "Wrong password. Attempts left: " + attemptsLeft
                    );
            	});
            }
            

            addOrUpdatePlayer(player.getUniqueId().toString(), player.getName(), false, attemptCount);
        }
    }
    
    private int getAttemptCount(JSONObject playerData) {
        Object attemptsObj = playerData.get("attempts");
        if (attemptsObj instanceof Long) return ((Long) attemptsObj).intValue();
        if (attemptsObj instanceof Integer) return (Integer) attemptsObj;
        return 0;
    }
}
