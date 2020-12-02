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
import java.util.HashSet;
import java.util.List;

public class CommandFakesnow implements TabExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0)
            return false;
        switch (args[0]) {
            case "refreshregions": {
                // Clear old cache first
                FakeSnow.get().regionChunkCache.clear();
                FakeSnow.get().regionWorldCache.clear();
                for (World world : Bukkit.getWorlds())
                    (new Events()).onWorldLoad(new WorldLoadEvent(world));
                sender.sendMessage(ChatColor.GREEN + "Reloaded " + Bukkit.getWorlds() + " worlds");
                sender.sendMessage(ChatColor.GREEN + "Discovered " +
                        FakeSnow.get().regionChunkCache.values().stream().mapToInt(HashSet::size).sum() + " region(s)");
                return true;
            }
            case "realbiome": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "You can only run this command as a player!");
                    return false;
                }
                Player player = ((Player) sender);
                player.sendMessage(ChatColor.GREEN + "The current biome you are in: " + player.getLocation().getBlock().getBiome().getKey().toString());
                return true;
            }
        }
        sender.sendMessage(ChatColor.RED + "/fakesnow <refreshregions/realbiome>");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1)
            return Arrays.asList("refreshregions", "realbiome");
        else
            return Collections.emptyList();
    }
}
