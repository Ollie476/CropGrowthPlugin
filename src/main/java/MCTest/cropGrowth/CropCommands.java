package MCTest.cropGrowth;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;

public class CropCommands implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (commandSender instanceof Player && strings.length > 0) {
            Player commandPlayer = (Player) commandSender;

            if (strings[0].equals("tick")) {
                changeCrop(commandPlayer, strings[1], strings[2], strings[3]);
            }
            else if (strings[0].equals("reset")) {
                resetAll(commandPlayer);
            }
        }
        return true;
    }

    private void changeCrop(Player sender, String cropName, String biomeName, String tickSpeed) {
        try {
            CropGrowth.saveToTickFile(Material.valueOf(cropName), Biome.valueOf(biomeName), Integer.parseInt(tickSpeed));
            sender.sendMessage(ChatColor.GREEN + biomeName + " is now running at a tick speed of: " + tickSpeed);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Didn't work");
        }
    }

    private void resetAll(Player sender) {
        for (Material crop : CropGrowth.ageableCrops)
            for (Biome biome: Biome.values()) {
                HashMap<Biome, Integer> biomeMap = CropGrowth.tickMap.get(crop);
                if (biomeMap == null || !biomeMap.containsKey(biome)) {
                    CropGrowth.saveToTickFile(crop, biome, 20);
                }
            }

        sender.sendMessage(ChatColor.GREEN + "All biome tick speeds have reset to 20");
    }
}
