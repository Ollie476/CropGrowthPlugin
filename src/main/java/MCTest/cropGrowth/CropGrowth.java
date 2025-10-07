package MCTest.cropGrowth;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

class CropData {
    public String worldName;
    public int x, y, z;
    public Biome biome;

    CropData(String worldName, int x, int y, int z, Biome biome) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.biome = biome;
    }

    public Block getBlock() {
        World world = Bukkit.getWorld(worldName);
        return world != null ? world.getBlockAt(x, y, z) : null;
    }

    public String getKey() {
        return worldName + "," + x + "," + y + "," + z;
    }
}

public final class CropGrowth extends JavaPlugin implements Listener {
    static private File placedFile;
    static private File tickFile;

    public final Plugin plugin = this;

    static HashMap<Material, HashMap<Biome, Integer>> tickMap;
    Set<CropData> allCrops;

    static Set<Material> cropSet = Set.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.COCOA,
            Material.SWEET_BERRY_BUSH,
            Material.SUGAR_CANE,
            Material.PUMPKIN_STEM,
            Material.MELON_STEM,
            Material.CACTUS
    );

    private final Map<String, BukkitRunnable> chunkTasks = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        File pluginFolder = getDataFolder();
        if (!pluginFolder.exists()) pluginFolder.mkdirs();

        placedFile = new File(pluginFolder, "placedData.yml");
        if (!placedFile.exists()) {
            try {
                placedFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        tickFile = new File(pluginFolder, "tickData.yml");
        if (!tickFile.exists()) {
            try {
                tickFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                startChunkTask(chunk);
            }
        }

        commandSetup();
        commandSetup();

        tickMap = loadFromTickFile();

        for (Material crop : cropSet)
            for (Biome biome: Biome.values()) {
                HashMap<Biome, Integer> biomeMap = tickMap.get(crop);
                if (biomeMap == null || !biomeMap.containsKey(biome)) {
                    saveToTickFile(crop, biome, 20);
                }
            }
        allCrops = loadFromPlacedFile();
    }

    private void commandSetup() {
        getCommand("cropgrowth").setExecutor(new CropCommands());

        getCommand("cropgrowth").setTabCompleter(new TabCompleter() {
            @Override
            public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
                switch (strings.length) {
                    case 1:
                        String[] options = new String[] {"tick", "reset", "list"};
                        List<String> filtered = new ArrayList<>();

                        for (String option : options) {
                            if (option.toLowerCase().startsWith(strings[0].toLowerCase())) {
                                filtered.add(option);
                            }
                        }
                        return filtered;
                    case 2:
                        if(strings[0].equals("reset"))
                            break;
                        List<String> cropNames = new ArrayList<>();
                        for (Material crop : cropSet)
                            if (crop.toString().toLowerCase().startsWith(strings[1].toLowerCase()))
                                cropNames.add(crop.name());

                        return cropNames;
                    case 3:
                        if(strings[0].equals("reset "))
                            break;
                        List<String> biomeNames = new ArrayList<>();
                        for (Biome biome : Biome.values())
                            if (biome.toString().toLowerCase().startsWith(strings[2].toLowerCase()))
                                biomeNames.add(biome.name());

                        return biomeNames;
                    }
                return List.of();
            }
        });
    }

    private void startChunkTask(Chunk chunk) {
        String key = getChunkKey(chunk);
        if (chunkTasks.containsKey(key)) return;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                for (CropData crop : allCrops) {
                    Block block = crop.getBlock();
                    if (block == null || !block.getChunk().equals(chunk)) continue;

                    Material cropType = block.getType();
                    Biome biome = block.getBiome();

                    HashMap<Biome, Integer> biomeMap = tickMap.get(cropType);
                    if (biomeMap == null) continue;

                    int tickSpeed = biomeMap.get(biome);

                    Block below = block.getRelative(0, -1, 0);
                    double moistureBuff = 0.5;

                    if (below.getBlockData() instanceof Farmland farmland) {
                        int moisture = farmland.getMoisture();
                        moistureBuff = 0.5 + (1.5 * (moisture / 7.0));
                    }

                    double biomeTickSpeed = tickSpeed * moistureBuff;
                    if (biomeTickSpeed < 1) continue;

                    double chance;

                    if (cropType == Material.SUGAR_CANE) {
                        chance = biomeTickSpeed / 410;
                    }
                    else {
                        chance = biomeTickSpeed / 125;
                    }

                    chance = Math.clamp(chance, 0.0, 1.0);
                    if (Math.random() < chance) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            incrementCropAge(block);
                        }, 1L);
                    }
                }
            }
        };
        task.runTaskTimerAsynchronously(this, 0L, 400L);
        chunkTasks.put(key, task);
    }


    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ();
    }

    private boolean growVerticalCrops(Block block, int maxHeight) {
        if (block.getType() == Material.SUGAR_CANE || block.getType() == Material.CACTUS) {
            Material material = block.getType();
            Block aboveBlock = block.getRelative(BlockFace.UP, 1);
            if (aboveBlock.getType() != Material.AIR) return true;

            int height = 1;
            Block below = block.getRelative(BlockFace.DOWN, 1);
            while (below.getType() == material && height < maxHeight) {
                height++;
                below = below.getRelative(BlockFace.DOWN, 1);
            }

            if (height >= maxHeight) {
                removeFromPlacedFile(block);
                return true;
            }

            aboveBlock.setType(material);
            saveToPlacedFile(new CropData("world", aboveBlock.getX(), aboveBlock.getY(), aboveBlock.getZ(), aboveBlock.getBiome()));
            return true;
        }
        return false;
    }

    private void incrementCropAge(Block block) {
        if (block.getLightLevel() < 9)
            return;

        if (growVerticalCrops(block, 3))
            return;
        else if (block.getBlockData() instanceof Ageable ageable){
            int age = ageable.getAge();
            if (age < ageable.getMaximumAge()) {
                ageable.setAge(age + 1);
                block.setBlockData(ageable);
            } else {
                removeFromPlacedFile(block);
            }
        }

    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        removeFromPlacedFile(e.getBlock());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Block block = e.getBlock();
        if (cropSet.contains(block.getType())) {
            saveToPlacedFile(new CropData(
                    block.getWorld().getName(),
                    block.getX(), block.getY(), block.getZ(),
                    block.getBiome()
            ));
        }
    }

    @EventHandler
    public void onBlockGrow(BlockGrowEvent e) {
        Material type = e.getBlock().getType();

        if (cropSet.contains(type))
            e.setCancelled(true);
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.BONE_MEAL) {
            return;
        }

        Block block = e.getClickedBlock();
        if (block == null || !cropSet.contains(block.getType()))
            return;

        e.setCancelled(true);
        if (block.getBlockData() instanceof Ageable ageable && ageable.getAge() != ageable.getMaximumAge()) {
            block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.4, 0.5), 10, 0.3, 0.15, 0.3, 0);
            block.getWorld().playSound(block.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.0f);
            if (e.getPlayer().getGameMode() != GameMode.CREATIVE)
                item.setAmount(item.getAmount() - 1);

            Random rnd = new Random();
            for (int i = 0; i < rnd.nextInt(2,5); i++)
                incrementCropAge(block);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        startChunkTask(e.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        String key = getChunkKey(e.getChunk());
        BukkitRunnable task = chunkTasks.remove(key);
        if (task != null) task.cancel();
    }

    private void saveToPlacedFile(CropData cropData) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(placedFile);
        config.set(cropData.getKey(), cropData.biome.toString());
        try {
            config.save(placedFile);
        } catch (IOException e) {

        }
        allCrops = loadFromPlacedFile();
    }

    private void removeFromPlacedFile(Block block) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(placedFile);
        String key = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
        config.set(key, null);
        try {
            config.save(placedFile);
        } catch (IOException e) {

        }
        allCrops = loadFromPlacedFile();
    }

    private Set<CropData> loadFromPlacedFile() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(placedFile);
        Set<CropData> cropDataList = new HashSet<>();

        for (String key : config.getKeys(false)) {
            String[] parts = key.split(",");
            if (parts.length != 4) continue;

            String worldName = parts[0];
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            Biome biome = Biome.valueOf(config.getString(key));

            cropDataList.add(new CropData(worldName, x, y, z, biome));
        }

        return cropDataList;
    }

    static public void saveToTickFile(Material crop, Biome biome, Integer tickSpeed) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(tickFile);
        config.set(crop.toString() + "," + biome.toString(), tickSpeed);
        try {
            config.save(tickFile);
        } catch (IOException e) {

        }

        tickMap = loadFromTickFile();
    }

    static public HashMap<Material, HashMap<Biome, Integer>> loadFromTickFile() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(tickFile);
        HashMap<Material, HashMap<Biome, Integer>> tickMap = new HashMap<>();

        for (String key : config.getKeys(false)) {
            String[] keys = key.split(",");
            if (keys.length != 2) {
                Bukkit.getLogger().warning("[CropGrowth] Invalid tickData key format: " + key);
                continue;
            }

            Material crop = Material.matchMaterial(keys[0]);
            Biome biome;
            try {
                biome = Biome.valueOf(keys[1]);
            } catch (IllegalArgumentException e) {
                Bukkit.getLogger().warning("[CropGrowth] Invalid biome name: " + keys[1]);
                continue;
            }

            if (crop == null) {
                Bukkit.getLogger().warning("[CropGrowth] Invalid crop name: " + keys[0]);
                continue;
            }

            int tickSpeed;
            try {
                tickSpeed = config.getInt(key);
            } catch (NumberFormatException e) {
                Bukkit.getLogger().warning("[CropGrowth] Invalid tick speed for " + key);
                continue;
            }

            tickMap.computeIfAbsent(crop, k -> new HashMap<>()).put(biome, tickSpeed);
        }

        return tickMap;
    }
}
