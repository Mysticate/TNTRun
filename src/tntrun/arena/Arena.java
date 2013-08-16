package tntrun.arena;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import tntrun.TNTRun;

public class Arena {

	private TNTRun plugin;
	public Arena(String name, TNTRun plugin)
	{
		this.arenaname = name;
		this.plugin = plugin;
	}
	

	
	private boolean enabled = false;
	public boolean running = false;
	
	private String arenaname;
	public String getArenaName()
	{
		return arenaname;
	}
	private World world;
	private Vector p1 = null;
	private Vector p2 = null;
	public int maxPlayers = 6;
	public double votesPercent = 0.75;
	private int curPlayers = 0;
	private HashSet<String> votes = new HashSet<String>();
	
	private HashMap<String, GameLevel> gamelevels = new HashMap<String, GameLevel>();
	private LooseLevel looselevel = new LooseLevel();
	private Location spawnpoint = null;
	
	
	public boolean enableArena()
	{
		if (isArenaConfigured())
		{
			enabled = true;
			return true;
		}
		return false;
	}
	public void disableArena()
	{
		enabled = false;
		//drop players
		for (String player : plugin.pdata.getArenaPlayers(this))
		{
			Bukkit.getPlayerExact(player).sendMessage("Arena is disabling");
			removePlayerFromArena(Bukkit.getPlayerExact(player));
		}
	}
	public boolean isArenaEnabled()
	{
		return enabled;
	}


	public boolean isArenaConfigured()
	{
		if (world == null) {return false;}
		if (p1 == null || p2==null) {return false;}
		if (gamelevels.size() == 0) {return false;}
		if (!looselevel.isConfigured()) {return false;}
		if (spawnpoint == null) {return false;}
		return true;
	}
	
	public void setArenaPoints(Location loc1, Location loc2)
	{
		this.world = loc1.getWorld();
		this.p1 = loc1.toVector();
		this.p2 = loc2.toVector();
	}
	public void setGameLevel(String glname, Location loc1, Location loc2)
	{
		GameLevel gl = gamelevels.get(glname);
		if (gl == null)
		{
			gl = new GameLevel();
			gamelevels.put(glname, gl);
		}
		gl.setGameLocation(loc1, loc2, world);
	}
	public void setLooseLevel(Location loc1, Location loc2)
	{
		looselevel.setLooseLocation(loc1, loc2, world);
	}
	public void setSpawnPoint(Location loc)
	{
		spawnpoint = loc;
	}
	

	public void spawnPlayer(Player player)
	{
		player.teleport(spawnpoint);
		curPlayers++;
		if (curPlayers == maxPlayers)
		{
			this.running = true;
			curPlayers = 0;
		}
	}
	public void leavePlayer(Player player)
	{
		removePlayerFromArena(player);
		votes.remove(player.getName());
		curPlayers--;
	}
	public void vote(Player player)
	{
		votes.add(player.getName());
		if (votes.size() >= ((int)curPlayers*votesPercent))
		{
			this.running = true;
			curPlayers = 0;
		}
	}
	
	public void handlePlayer(final Player player)
	{
		//check if player is in arena
		if (!player.getLocation().toVector().isInAABB(p1, p2))
		{
			player.sendMessage("You left the arena");
			removePlayerFromArena(player);
		}
		//check for game location
		for (final GameLevel gl : gamelevels.values())
		{
			if (gl.isSandLocation(player.getLocation().add(0,-1,0)))
			{
				Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
				{
					public void run()
					{ 
						if (running)
						{
							gl.destroyBlock(player.getLocation(), world);
						}
					}
				}, 10);
			}
		}
		//check for loose location
		if (looselevel.isLooseLocation(player.getLocation()))
		{
			//player lost
			player.sendMessage("You lost the arena");
			removePlayerFromArena(player);
			//now check for win
			if (plugin.pdata.getArenaPlayers(this).size() == 1)
			{
				//last player won
				Player winner = Bukkit.getPlayerExact(plugin.pdata.getArenaPlayers(this).iterator().next());
				winner.sendMessage("You won the arena");
				removePlayerFromArena(winner);
				rewardPlayer(winner);
				//regenerate arena
				for (final GameLevel gl : gamelevels.values())
				{
					gl.regen(world);
				}
			}
			//not running
			this.running = false;
		}
	}
	private void removePlayerFromArena(Player player)
	{
		plugin.pdata.removePlayerFromArena(player.getName());
		player.teleport(plugin.pdata.getPlayerLocation(player.getName()));
		player.getInventory().setContents(plugin.pdata.getPlayerInventory(player.getName()));
		player.getInventory().setArmorContents(plugin.pdata.getPlayerArmor(player.getName()));
	}
	private void rewardPlayer(Player player)
	{
		
	}

	
	public void saveToConfig()
	{
		FileConfiguration config = new YamlConfiguration();
		config.set("world", world.getName());
		config.set("p1", p1);
		config.set("p2", p2);
		config.set("spawnpoint", spawnpoint.toVector());
		config.set("maxPlayers", maxPlayers);
		for (String glname : gamelevels.keySet())
		{
			try 
			{
				GameLevel gl = gamelevels.get(glname);
				gl.saveToConfig("gamelevel"+glname, config);
			} catch (Exception e) {}
		}
		looselevel.saveToConfig(config);
		try {
			config.save(new File("plugins/TNTRun/arenas/"+arenaname+".yml"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void loadFromConfig()
	{
		FileConfiguration config = YamlConfiguration.loadConfiguration(new File("plugins/TNTRun/arenas/"+arenaname+".yml"));
		this.world = Bukkit.getWorld(config.getString("world", null));
		this.p1 = config.getVector("p1", null);
		this.p2 = config.getVector("p2", null);
		Vector v = config.getVector("spawnpoint", null);
		try {
			this.spawnpoint = new Location(world, v.getX(), v.getY(), v.getZ());
		} catch (Exception e) {}
		this.maxPlayers = config.getInt("maxPlayers");
		ConfigurationSection cs = config.getConfigurationSection("");
		if (cs != null)
		{
			for (String key : cs.getKeys(false))
			{
				if (key.contains("gamelevel"))
				{
					try{
						GameLevel gl = new GameLevel();
						gl.loadFromConfig(key, config);
						gamelevels.put(key,gl);
					} catch (Exception e) {}
				}
			}
		}
		looselevel.loadFromConfig(config);
		plugin.pdata.putArenaInHashMap(this);
		if (isArenaConfigured())
		{
			for (GameLevel gl : gamelevels.values())
			{
				gl.regen(world);
			}
			looselevel.regen(world);
			this.enabled = true;
		}
	}
	
}
