package com.jacky8399.fakesnow;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandFakesnow implements TabExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0)
            return false;
        switch (args[0]) {
            case "refreshregions": {
                if (!(sender instanceof Player)) {
                    for (World world : Bukkit.getWorlds())
                        (new Events()).onWorldLoad(new WorldLoadEvent(world));
                    sender.sendMessage(ChatColor.GREEN + "Reloaded all worlds");
                    return true;
                }
                World world = ((Player) sender).getWorld();
                sender.sendMessage(ChatColor.GREEN + "Reloading " + world.getName());
                (new Events()).onWorldLoad(new WorldLoadEvent(world));
            }
            break;
            case "realbiome": {
                if (!(sender instanceof Player)) {
                    return false;
                }
                Player player = ((Player) sender);
                player.sendMessage(ChatColor.GREEN + "The current biome you are in: " + player.getLocation().getBlock().getBiome().getKey().toString());
            }
            break;
            default:
                return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1)
            return Arrays.asList("refreshregions", "realbiome");
        else
            return Collections.emptyList();
    }
}
