package MCTest.cropGrowth;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class CropCommands implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (commandSender instanceof Player && strings.length > 0 && commandSender.isOp()) {
            Player commandPlayer = (Player) commandSender;

            switch (strings[0]) {
                case "tick":
                    changeCrop(commandPlayer, strings[1], strings[2], strings[3]);
                    break;
                case "reset":
                    resetAll(commandPlayer);
                    break;
                case "list":
                    viewChanged(commandPlayer);
                    break;
            }
        }
        return true;
    }

    private void changeCrop(Player sender, String cropName, String biomeName, String tickSpeed) {
        try {
            CropGrowth.saveToTickFile(Material.valueOf(cropName), Biome.valueOf(biomeName), Integer.valueOf(Integer.parseInt(tickSpeed)));
            sender.sendMessage(ChatColor.GREEN + cropName + " in " + biomeName + " is now running at a tick speed of: " + tickSpeed + " (default: 20)");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Didn't work");
        }
    }

    private void resetAll(Player sender) {
        for (Material crop : CropGrowth.cropSet)
            for (Biome biome : Biome.values()) {
                HashMap<Biome, Integer> biomeMap = CropGrowth.tickMap.get(crop);
                if (biomeMap == null || !biomeMap.containsKey(biome)) {
                    CropGrowth.saveToTickFile(crop, biome, (Integer)20);
                }
            }

        sender.sendMessage(ChatColor.GREEN + "All biome tick speeds have reset to 20");
    }

    private void viewChanged(Player sender) {
        for (Material crop : CropGrowth.tickMap.keySet()) {
            HashMap<Biome, Integer> biomeMap = CropGrowth.tickMap.get(crop);
            if (biomeMap == null || biomeMap.isEmpty()) continue;

            boolean hasChanges = false;
            StringBuilder output = new StringBuilder(ChatColor.GREEN + crop.name() + ":\n");

            for (Map.Entry<Biome, Integer> entry : biomeMap.entrySet()) {
                if (entry.getValue() != 20) {
                    hasChanges = true;
                    output.append(ChatColor.GOLD)
                            .append("  ")
                            .append(entry.getKey().name())
                            .append(" → ")
                            .append(entry.getValue())
                            .append("\n");
                }
            }

            if (hasChanges) {
                sender.sendMessage(output.toString().trim());
            }
        }
    }

}
