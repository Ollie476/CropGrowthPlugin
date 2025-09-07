package MCTest.cropGrowth;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.world.*;
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

    static HashMap<Material, HashMap<Biome, Integer>> tickMap;
    Set<CropData> allCrops;

    static Set<Material> ageableCrops = Set.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.COCOA,
            Material.SWEET_BERRY_BUSH
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

        tickMap = loadFromTickFile();

        for (Material crop : ageableCrops)
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
                        String[] options = new String[] {"tick", "reset"};
                        List<String> filtered = new ArrayList<>();

                        for (String option : options) {
                            if (option.toLowerCase().startsWith(strings[0].toLowerCase())) {
                                filtered.add(option);
                            }
                        }
                        return filtered;
                    case 2:
                        List<String> cropNames = new ArrayList<>();
                        for (Material crop : ageableCrops)
                            if (crop.toString().toLowerCase().startsWith(strings[1].toLowerCase()))
                                cropNames.add(crop.name());

                        return cropNames;
                    case 3:
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

                    Integer tickSpeed = biomeMap.get(biome);
                    if (tickSpeed == null) continue;

                    Block below = block.getRelative(0, -1, 0);
                    double moistureBuff = 0.5;
                    if (below.getBlockData() instanceof org.bukkit.block.data.type.Farmland farmland) {
                        moistureBuff = (farmland.getMoisture() == 7) ? 2.0 : 0.5;
                    }

                    double biomeTickSpeed = tickSpeed * moistureBuff;
                    if (biomeTickSpeed < 1) continue;

                    if (Math.random() < 1 / (500.0 / biomeTickSpeed)) {
                        incrementCropAge(block);
                    }
                }
            }
        };
        task.runTaskTimer(this, 0L, 20L);
        chunkTasks.put(key, task);
    }


    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + "," + chunk.getX() + "," + chunk.getZ();
    }

    private void incrementCropAge(Block block) {
        if (block.getLightLevel() < 9)
            return;


        if (block.getBlockData() instanceof Ageable ageable) {
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
    public void onBlockBreak(BlockBreakEvent event) {
        removeFromPlacedFile(event.getBlock());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getBlockData() instanceof Ageable && block.getType() != Material.TURTLE_EGG) {
            saveToPlacedFile(new CropData(
                    block.getWorld().getName(),
                    block.getX(), block.getY(), block.getZ(),
                    block.getBiome()
            ));
        }
    }

    @EventHandler
    public void onBlockGrow(BlockGrowEvent event) {
        if (event.getBlock().getType() != Material.TURTLE_EGG) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        startChunkTask(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        String key = getChunkKey(event.getChunk());
        BukkitRunnable task = chunkTasks.remove(key);
        if (task != null) task.cancel();
    }

    private void saveToPlacedFile(CropData cropData) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(placedFile);
        config.set(cropData.getKey(), cropData.biome.toString());
        try {
            config.save(placedFile);
        } catch (IOException e) {
            e.printStackTrace();
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
            e.printStackTrace();
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

    static public void saveToTickFile(Material crop, Biome biome, int tickSpeed) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(tickFile);
        config.set(crop.toString() + "," + biome.toString(), tickSpeed);
        try {
            config.save(tickFile);
        } catch (IOException e) {
            e.printStackTrace();
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
                tickSpeed = Integer.parseInt(config.getString(key));
            } catch (NumberFormatException e) {
                Bukkit.getLogger().warning("[CropGrowth] Invalid tick speed for " + key);
                continue;
            }

            tickMap.computeIfAbsent(crop, k -> new HashMap<>()).put(biome, tickSpeed);
        }

        return tickMap;
    }
}
