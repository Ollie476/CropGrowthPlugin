package MCTest.cropGrowth;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.HashSet;

import java.io.File;
import java.io.IOException;
import java.util.*;

class CropData {
    public Block block;
    public Biome biome;

    CropData(Block block , Biome biome) {
        this.block = block;
        this.biome = biome;
    }
}

public final class CropGrowth extends JavaPlugin implements Listener {
    static private File file;

    @Override
    public void onEnable() {
        File pluginFolder = getDataFolder();

        if (!pluginFolder.exists())
            pluginFolder.mkdirs();


        file = new File(pluginFolder, "cropData.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) throws IOException {
        Chunk chunk = event.getChunk();

        growCropsInChunk(chunk);
    }

    private void growCropsInChunk(Chunk chunk) {
        long tickSpeed = 20;

        new BukkitRunnable() {
            private HashSet<CropData> cropDataList;

            @Override
            public void run() {
                this.cropDataList = loadAllFromYml();
                for (CropData crop : cropDataList) {
                    incrementCropAge(crop.block);
                }

            }
        }.runTaskTimer(this, 0L, tickSpeed);
    }

    private void incrementCropAge(Block block) {
        if (block.getBlockData() instanceof Ageable ageable) {
            int age = ageable.getAge();
            if (age > 6 || age < 0) return;

            ageable.setAge(age + 1);
            block.setBlockData(ageable);
        }
    }

    @EventHandler
    public void blockGrowEvent(BlockGrowEvent event) {
        if (event.getBlock().getType() == Material.TURTLE_EGG)
            return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlaced(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getBlockData() instanceof Ageable) {
            if (block.getType() == Material.TURTLE_EGG)
                return;
            CropData cropData = new CropData(block, block.getBiome());
            saveToYml(cropData);
        }
    }

    static private void saveToYml(CropData cropData) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Block block = cropData.block;
        String key = block.getX() + "," + block.getY() + "," + block.getZ();
        config.set(key, cropData.biome.toString());

        try {
            config.save(file);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    static private HashSet<CropData> loadAllFromYml() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        HashSet<CropData> cropDataList = new HashSet<CropData>();

        for (String key : config.getKeys(false)) {
            String[] parts = key.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);

            CropData cropData = new CropData(Bukkit.getWorlds().get(0).getBlockAt(x,y,z), Biome.valueOf(Objects.requireNonNull(config.getString(key))));

            cropDataList.add(cropData);
        }

        return cropDataList;
    }
}
