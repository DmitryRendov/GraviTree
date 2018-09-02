package me.ryanhamshire.GraviTree;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;

public class GraviTree extends JavaPlugin implements Listener
{
	//for logging to the console and log file
	private static Logger log = Logger.getLogger("Minecraft");
    static GraviTree instance;
	
	//adds a server log entry
	public static void AddLogEntry(String entry)
	{
		log.info("GraviTree: " + entry);
	}

    private boolean config_universalPermission;
    private String config_chopInfoMessage;
    private String config_chopOn;
    private String config_chopOff;
    private boolean config_canDisable;
    boolean config_chopModeOnByDefault;
	
	//initializes well...   everything
	public void onEnable()
	{ 		
		GraviTree.instance = this;
		
        //read configuration settings (note defaults)
        this.getDataFolder().mkdirs();
        File configFile = new File(this.getDataFolder().getPath() + File.separatorChar + "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        FileConfiguration outConfig = new YamlConfiguration();
        
        this.config_universalPermission = config.getBoolean("All Players Have Permission", true);
        outConfig.set("All Players Have Permission", this.config_universalPermission);
        
        this.config_chopModeOnByDefault = config.getBoolean("Chop Mode Defaults ON", true);
        outConfig.set("Chop Mode Defaults ON", this.config_chopModeOnByDefault);
        
        this.config_chopInfoMessage = config.getString("Messages.Chop Toggle Info", "You can toggle falling tree blocks with /TreesFall.");
        outConfig.set("Messages.Chop Toggle Info", this.config_chopInfoMessage);
        
        this.config_chopOn = config.getString("Messages.Chop Toggle On", "Falling tree blocks enabled.");
        outConfig.set("Messages.Chop Toggle On", this.config_chopOn);
        
        this.config_chopOff = config.getString("Messages.Chop Toggle Off", "Falling tree blocks disabled.");
        outConfig.set("Messages.Chop Toggle Off", this.config_chopOff);
        
        this.config_canDisable = config.getBoolean("Players Can Disable", true);
        outConfig.set("Players Can Disable", this.config_canDisable);
        
        try
        {
            outConfig.save(configFile);
        }
        catch(IOException  e)
        {
            AddLogEntry("Encountered an issue while writing to the config file.");
            e.printStackTrace();
        }
	    
	    //register for events
		this.getServer().getPluginManager().registerEvents(this, this);
		
		@SuppressWarnings("unchecked")
        Collection<Player> players = (Collection<Player>)this.getServer().getOnlinePlayers();
        for(Player player : players)
        {
            PlayerData.Preload(player);
        }

        Metrics metrics = new Metrics(this);

    }
	
	public void onDisable()
	{
	    @SuppressWarnings("unchecked")
        Collection<Player> players = (Collection<Player>)this.getServer().getOnlinePlayers();
        for(Player player : players)
        {
            PlayerData data = PlayerData.FromPlayer(player);
            data.saveChanges();
            data.waitForSaveComplete();
        }
        
        AddLogEntry("GraviTree disabled.");
	}
	
	//handles slash commands
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
    {
        Player player = null;
        if (sender instanceof Player) 
        {
            player = (Player) sender;
        }
        
        if(cmd.getName().equalsIgnoreCase("treesfall") && player != null)
        {
            if(!this.config_canDisable) return true;
            
            PlayerData playerData = PlayerData.FromPlayer(player);
            playerData.setChopEnabled(!playerData.isChopEnabled());
            if(playerData.isChopEnabled())
            {
                player.sendMessage(ChatColor.AQUA + this.config_chopOn);
            }
            else
            {
                player.sendMessage(ChatColor.AQUA + this.config_chopOff);
            }
            return true;
        }
        
        return false;
    }
	
	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerJoin(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();
        PlayerData.Preload(player);
    }
    
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerQuit(PlayerQuitEvent event)
    {
        Player player = event.getPlayer();
        PlayerData.FromPlayer(player).saveChanges();
    }
	
    @SuppressWarnings("deprecation")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onBlockBreak(BlockBreakEvent event)
    {
        Block brokenBlock = event.getBlock();
        
        World world = brokenBlock.getWorld();
        if(world.getEnvironment() != Environment.NORMAL) return;
               
        if(!GraviTree.blockIsLog(brokenBlock)) return;
        
        Player player = event.getPlayer();
        if(!this.hasPermission(player)) return;
        
        PlayerData playerData = PlayerData.FromPlayer(player);
        
        if(!playerData.isGotChopInfo() && (!playerData.isChopEnabled() || this.config_canDisable))
        {
            player.sendMessage(ChatColor.AQUA + GraviTree.instance.config_chopInfoMessage);
            playerData.setGotChopInfo(true);
        }
        
        if(!playerData.isChopEnabled()) return;
        
        Block bestUnderBlock = brokenBlock;
        do
        {
            bestUnderBlock = bestUnderBlock.getRelative(BlockFace.DOWN);
            while(GraviTree.blockIsLog(bestUnderBlock)) bestUnderBlock = bestUnderBlock.getRelative(BlockFace.DOWN);
            ConcurrentLinkedQueue<BlockFace> nearBelowFaces = new ConcurrentLinkedQueue<BlockFace>();
            nearBelowFaces.add(BlockFace.EAST);
            nearBelowFaces.add(BlockFace.SOUTH);
            nearBelowFaces.add(BlockFace.NORTH);
            nearBelowFaces.add(BlockFace.WEST);
            nearBelowFaces.add(BlockFace.NORTH_EAST);
            nearBelowFaces.add(BlockFace.SOUTH_EAST);
            nearBelowFaces.add(BlockFace.NORTH_WEST);
            nearBelowFaces.add(BlockFace.SOUTH_WEST);
                
            do
            {
                Block nearBelowBlock = bestUnderBlock.getRelative(nearBelowFaces.poll());
                if(this.blockIsRootType(nearBelowBlock))
                {
                    bestUnderBlock = nearBelowBlock;
                    break;
                }
                if(GraviTree.blockIsLog(nearBelowBlock))
                {
                    bestUnderBlock = nearBelowBlock;
                    break;
                }
            } while(!nearBelowFaces.isEmpty());
        } while(GraviTree.blockIsLog(bestUnderBlock));
        
        
        if(!this.blockIsRootType(bestUnderBlock)) return;
        
        BlockFace [] adjacentFaces = new BlockFace []
        {
            BlockFace.EAST,
            BlockFace.WEST,
            BlockFace.SOUTH,
            BlockFace.NORTH,
            BlockFace.NORTH_EAST,
            BlockFace.NORTH_WEST,
            BlockFace.SOUTH_EAST,
            BlockFace.SOUTH_WEST
        };
        
        for(BlockFace adjacentFace : adjacentFaces)
        {
            Block adjacentBlock = brokenBlock.getRelative(adjacentFace);
            if(GraviTree.blockIsLog(adjacentBlock)) return;
        }
        
        Block aboveBlock = brokenBlock.getRelative(BlockFace.UP);
        while(GraviTree.blockIsLog(aboveBlock))
        {
            aboveBlock = aboveBlock.getRelative(BlockFace.UP);
        }
        
        if(!this.blockIsTreeTopper(aboveBlock)) return;
        
        int radius = 20;
        if(brokenBlock.getType() == Material.DARK_OAK_LOG || brokenBlock.getType() == Material.ACACIA_LOG || brokenBlock.getType() == Material.SPRUCE_LOG)
        {
            if(brokenBlock.getData() == 1)
            {
                radius = 2;
            }
        }
        FallTask fallTask = new FallTask(brokenBlock, true, bestUnderBlock.getX() - radius, bestUnderBlock.getX() + radius, bestUnderBlock.getZ() - radius, bestUnderBlock.getZ() + radius, player);
        Bukkit.getScheduler().runTaskLater(GraviTree.instance, fallTask, 1L);
    }
    
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    void onEntityDamage(EntityDamageEvent event)
    {
        if(event.getEntityType() != EntityType.PLAYER) return;
        
        DamageCause cause = event.getCause();
        if(cause != DamageCause.FALLING_BLOCK && cause != DamageCause.SUFFOCATION) return;
        
        Block faceBlock = ((Player)(event.getEntity())).getEyeLocation().getBlock();
        
        Material type = faceBlock.getType();
        if(type == Material.OAK_LOG || type == Material.JUNGLE_LOG || type == Material.ACACIA_LOG || type == Material.BIRCH_LOG || type == Material.DARK_OAK_LOG || type == Material.SPRUCE_LOG || type == Material.AIR)
        {
            WorldBorder border = faceBlock.getWorld().getWorldBorder();
            if(border != null)
            {
                if(!outsideWorldBorder(faceBlock.getX(), faceBlock.getY(), faceBlock.getZ(), border.getCenter().getBlockX(), border.getCenter().getBlockY(), border.getCenter().getBlockZ(), border.getSize(), border.getDamageBuffer()))
                {
                    event.setCancelled(true);
                }
            }
        }
    }

    static boolean outsideWorldBorder(int x, int y, int z, int borderCenterX, int borderCenterY, int borderCenterZ, double borderSize, double borderDamageBuffer)
    {
        int damageDistance = (int)(borderSize / 2 + borderDamageBuffer);
        
        int maxx = borderCenterX + damageDistance;
        if(x > maxx) return true;
        int minx = borderCenterX - damageDistance;
        if(x < minx) return true;
        
        int maxz = borderCenterZ + damageDistance;
        if(z > maxz) return true;
        int minz = borderCenterZ - damageDistance;
        if(z < minz) return true;
        
        return false;
    }

    private boolean hasPermission(Player player)
    {
        if(GraviTree.instance.config_universalPermission) return true;
        return player.hasPermission("gravitree.chop");
    }

    private static void markVisited(Block block)
    {
        block.setMetadata("gravitree.seen", new FixedMetadataValue(GraviTree.instance, Boolean.TRUE));
    }
    
    private static boolean haveVisited(Block block)
    {
        return block.hasMetadata("gravitree.seen");
    }

    private static boolean blockIsLog(Block block)
    {
        switch (block.getType()) {
            case OAK_LOG:
            case JUNGLE_LOG:
            case ACACIA_LOG:
            case BIRCH_LOG:
            case DARK_OAK_LOG:
            case SPRUCE_LOG:
            case OAK_WOOD:
            case JUNGLE_WOOD:
            case ACACIA_WOOD:
            case BIRCH_WOOD:
            case DARK_OAK_WOOD:
            case SPRUCE_WOOD:
                return true;
        }
        return false;
    }
    
    private boolean blockIsRootType(Block block)
    {
        switch (block.getType()) {
            case DIRT:
            case GRASS_BLOCK:
            case STONE:
            case GRANITE:
            case DIORITE:
            case ANDESITE:
            case COBBLESTONE:
            case CLAY:
            case SAND:
            case TERRACOTTA:
                return true;
        }
//GraviTree.AddLogEntry("not root type " + block.getType().name());
        return false;
    }
    
    private boolean blockIsTreeTopper(Block block)
    {
        switch (block.getType()) {
            case OAK_LEAVES:
            case SPRUCE_LEAVES:
            case BIRCH_LEAVES:
            case JUNGLE_LEAVES:
            case ACACIA_LEAVES:
            case DARK_OAK_LEAVES:
            case AIR:
            case SNOW:
                return true;
        }
//GraviTree.AddLogEntry("not tree topper " + block.getType().name());
        return false;
    }
    
    private static boolean blockIsPassthrough(Block block)
    {
        switch (block.getType()) {
            case OAK_LEAVES:
            case SPRUCE_LEAVES:
            case BIRCH_LEAVES:
            case JUNGLE_LEAVES:
            case ACACIA_LEAVES:
            case DARK_OAK_LEAVES:
            case OAK_LOG:
            case JUNGLE_LOG:
            case ACACIA_LOG:
            case BIRCH_LOG:
            case DARK_OAK_LOG:
            case SPRUCE_LOG:
            case OAK_WOOD:
            case JUNGLE_WOOD:
            case ACACIA_WOOD:
            case BIRCH_WOOD:
            case DARK_OAK_WOOD:
            case SPRUCE_WOOD:
            case SNOW:
                return true;
        }
        return false;
    }
    
    static boolean blockIsBreakable(Block block)
    {
        if(block.getY() < 0) return false;
        
        switch (block.getType()) {
            case OAK_LEAVES:
            case SPRUCE_LEAVES:
            case BIRCH_LEAVES:
            case JUNGLE_LEAVES:
            case ACACIA_LEAVES:
            case DARK_OAK_LEAVES:
            case AIR:
            case VINE:
            case COCOA:
            case TORCH:
            case SNOW:
                return true;
        }
        return false;
    }
    
    static boolean blockIsTreeAdjacent(Block block)
    {
        switch (block.getType()) {
            case OAK_LEAVES:
            case SPRUCE_LEAVES:
            case BIRCH_LEAVES:
            case JUNGLE_LEAVES:
            case ACACIA_LEAVES:
            case DARK_OAK_LEAVES:
            case AIR:
            case VINE:
            case COCOA:
            case TORCH:
            case SNOW:
            case GRASS_BLOCK:
            case DIRT:
            case STONE:
            case GRANITE:
            case DIORITE:
            case ANDESITE:
            case COBBLESTONE:
            case TALL_GRASS:
                return true;
        }
        return false;
    }
    
    class FallTask implements Runnable
    {
        private Block blockToDrop;
        private boolean breakUnderBlocks;
        private int max_x;
        private int max_z;
        private int min_x;
        private int min_z;
        private Player player;

        public FallTask(Block blockToDrop, boolean breakUnderBlocks, int min_x, int max_x, int min_z, int max_z, Player player)
        {
            this.blockToDrop = blockToDrop;
            this.breakUnderBlocks = breakUnderBlocks;
            this.min_x = min_x;
            this.min_z = min_z;
            this.max_x = max_x;
            this.max_z = max_z;
            this.player = player;
        }

        @Override
        public void run()
        {
            if(GraviTree.blockIsLog(this.blockToDrop))
            {
                @SuppressWarnings("deprecation")
                FallingBlock fallingBlock = blockToDrop.getWorld().spawnFallingBlock(blockToDrop.getLocation().add(.5, 0, .5), blockToDrop.getBlockData());
                fallingBlock.setDropItem(false);

                blockToDrop.setType(Material.AIR);
                
                if(this.breakUnderBlocks)
                {
                    Block underBlock = blockToDrop.getRelative(BlockFace.DOWN);
                    while(GraviTree.blockIsBreakable(underBlock))
                    {
                        underBlock.breakNaturally();
                        underBlock = underBlock.getRelative(BlockFace.DOWN);
                    }
                }
            }
            
            ConcurrentLinkedQueue<Block> nearAboveBlocks = new ConcurrentLinkedQueue<Block>();
            Block aboveBlock = this.blockToDrop.getRelative(BlockFace.UP);
            nearAboveBlocks.add(aboveBlock);
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.EAST));
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.SOUTH));
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.NORTH));
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.WEST));
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.NORTH_EAST));
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.SOUTH_EAST));
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.NORTH_WEST));
            nearAboveBlocks.add(aboveBlock.getRelative(BlockFace.SOUTH_WEST));
            
            boolean foundLogAbove = false;
            ConcurrentLinkedQueue<FallTask> newTasks = new ConcurrentLinkedQueue<FallTask>();
            for(Block block : nearAboveBlocks)
            {
                if(GraviTree.blockIsLog(block))
                {
                    Block underBlock = block.getRelative(BlockFace.DOWN);
                    if(GraviTree.blockIsBreakable(underBlock))
                    {
                        FallTask fallTask = new FallTask(block, block != aboveBlock, this.min_x, this.max_x, this.min_z, this.max_z, this.player);
                        newTasks.add(fallTask);
                        foundLogAbove = true;
                    }
                }
                else if(!GraviTree.blockIsTreeAdjacent(block))
                {
                    return;
                }
            }
            
            long i = 0;
            for(FallTask task : newTasks)
            {
                Bukkit.getScheduler().runTaskLater(GraviTree.instance, task, ++i);
            }
            
            if(!foundLogAbove && !this.breakUnderBlocks)
            {
                ConcurrentLinkedQueue<Block> blocksToFall = new ConcurrentLinkedQueue<Block>();
                        
                GraviTree.markVisited(aboveBlock);
                ConcurrentLinkedQueue<Block> blocksToVisit = new ConcurrentLinkedQueue<Block>();
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.EAST));
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.WEST));
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.NORTH));
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.SOUTH));
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.SOUTH_EAST));
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.SOUTH_WEST));
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.NORTH_EAST));
                blocksToVisit.add(this.blockToDrop.getRelative(BlockFace.SOUTH_WEST));
                for(Block block : blocksToVisit)
                {
                    GraviTree.markVisited(block);
                }
                
                Block nextBlock;
                while((nextBlock = blocksToVisit.poll()) != null)
                {
                    if(GraviTree.blockIsLog(nextBlock))
                    {
                        Block underBlock = nextBlock.getRelative(BlockFace.DOWN);
                        if(blockIsBreakable(underBlock))
                        {
                            if(!blocksToFall.contains(nextBlock))
                            {
                                blocksToFall.add(nextBlock);
                            }
                        }
                    }
                    
                    if(GraviTree.blockIsPassthrough(nextBlock))
                    {
                        Block [] nextBlocks = new Block []
                        {
                            nextBlock,
                            nextBlock.getRelative(BlockFace.UP),
                            nextBlock.getRelative(BlockFace.DOWN)
                        };
                        
                        for(Block nextNextBlock : nextBlocks)
                        {
                            Block adjacentBlock = nextNextBlock.getRelative(BlockFace.EAST);
                            if(adjacentBlock.getX() >= min_x && adjacentBlock.getX() <= max_x && adjacentBlock.getZ() >= min_z && adjacentBlock.getZ() <= max_z && !GraviTree.haveVisited(adjacentBlock))
                            {
                                blocksToVisit.add(adjacentBlock);
                                GraviTree.markVisited(adjacentBlock);
                            }
                            
                            adjacentBlock = nextNextBlock.getRelative(BlockFace.WEST);
                            if(adjacentBlock.getX() >= min_x && adjacentBlock.getX() <= max_x && adjacentBlock.getZ() >= min_z && adjacentBlock.getZ() <= max_z && !GraviTree.haveVisited(adjacentBlock))
                            {
                                blocksToVisit.add(adjacentBlock);
                                GraviTree.markVisited(adjacentBlock);
                            }
                            
                            adjacentBlock = nextNextBlock.getRelative(BlockFace.NORTH);
                            if(adjacentBlock.getX() >= min_x && adjacentBlock.getX() <= max_x && adjacentBlock.getZ() >= min_z && adjacentBlock.getZ() <= max_z && !GraviTree.haveVisited(adjacentBlock))
                            {
                                blocksToVisit.add(adjacentBlock);
                                GraviTree.markVisited(adjacentBlock);
                            }
                            
                            adjacentBlock = nextNextBlock.getRelative(BlockFace.SOUTH);
                            if(adjacentBlock.getX() >= min_x && adjacentBlock.getX() <= max_x && adjacentBlock.getZ() >= min_z && adjacentBlock.getZ() <= max_z && !GraviTree.haveVisited(adjacentBlock))
                            {
                                blocksToVisit.add(adjacentBlock);
                                GraviTree.markVisited(adjacentBlock);
                            }
                            
                            adjacentBlock = nextNextBlock.getRelative(BlockFace.SOUTH_EAST);
                            if(adjacentBlock.getX() >= min_x && adjacentBlock.getX() <= max_x && adjacentBlock.getZ() >= min_z && adjacentBlock.getZ() <= max_z && !GraviTree.haveVisited(adjacentBlock))
                            {
                                blocksToVisit.add(adjacentBlock);
                                GraviTree.markVisited(adjacentBlock);
                            }
                            
                            adjacentBlock = nextNextBlock.getRelative(BlockFace.NORTH_EAST);
                            if(adjacentBlock.getX() >= min_x && adjacentBlock.getX() <= max_x && adjacentBlock.getZ() >= min_z && adjacentBlock.getZ() <= max_z && !GraviTree.haveVisited(adjacentBlock))
                            {
                                blocksToVisit.add(adjacentBlock);
                                GraviTree.markVisited(adjacentBlock);
                            }
                            
                            adjacentBlock = nextNextBlock.getRelative(BlockFace.SOUTH_WEST);
                            if(adjacentBlock.getX() >= min_x && adjacentBlock.getX() <= max_x && adjacentBlock.getZ() >= min_z && adjacentBlock.getZ() <= max_z && !GraviTree.haveVisited(adjacentBlock))
                            {
                                blocksToVisit.add(adjacentBlock);
                                GraviTree.markVisited(adjacentBlock);
                            }
                            
                            adjacentBlock = nextNextBlock.getRelative(BlockFace.NORTH_WEST);
                            if(adjacentBlock.getX() >= min_x && adjacentBlock.getX() <= max_x && adjacentBlock.getZ() >= min_z && adjacentBlock.getZ() <= max_z && !GraviTree.haveVisited(adjacentBlock))
                            {
                                blocksToVisit.add(adjacentBlock);
                                GraviTree.markVisited(adjacentBlock);
                            }
                            
                            adjacentBlock = nextNextBlock.getRelative(BlockFace.DOWN);
                            if(adjacentBlock.getX() >= min_x && adjacentBlock.getX() <= max_x && adjacentBlock.getZ() >= min_z && adjacentBlock.getZ() <= max_z && !GraviTree.haveVisited(adjacentBlock))
                            {
                                blocksToVisit.add(adjacentBlock);
                                GraviTree.markVisited(adjacentBlock);
                            }
                            
                            adjacentBlock = nextNextBlock.getRelative(BlockFace.UP);
                            if(adjacentBlock.getX() >= min_x && adjacentBlock.getX() <= max_x && adjacentBlock.getZ() >= min_z && adjacentBlock.getZ() <= max_z && !GraviTree.haveVisited(adjacentBlock))
                            {
                                blocksToVisit.add(adjacentBlock);
                                GraviTree.markVisited(adjacentBlock);
                            }
                        }
                    }
                }
                
                Block blockToDrop;
                long delayInTicks = 1;
                while((blockToDrop = blocksToFall.poll()) != null)
                {
                    FallTask fallTask = new FallTask(blockToDrop, true, this.min_x, this.max_x, this.min_z, this.max_z, this.player);
                    Bukkit.getScheduler().runTaskLater(GraviTree.instance, fallTask, delayInTicks++);
                }
            }
        }
    }
}