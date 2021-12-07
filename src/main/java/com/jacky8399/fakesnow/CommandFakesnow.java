package com.jacky8399.fakesnow;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
                    Events.addRegionsToCache(world);
                sender.sendMessage(ChatColor.GREEN + "Reloaded " + Bukkit.getWorlds().size() + " worlds");
                sender.sendMessage(ChatColor.GREEN + "Discovered " +
                        FakeSnow.get().regionChunkCache.values().stream().mapToInt(HashSet::size).sum() + " region(s)");
                return true;
            }
            case "realbiome": {
                Location location;
                if (args.length >= 4) {
                    try {
                        World world = args.length == 5 ? Bukkit.getWorld(args[4]) : Bukkit.getWorlds().get(0);
                        if (world == null) {
                            sender.sendMessage(ChatColor.RED + "No such world exist!");
                            return false;
                        }
                        location = new Location(world, Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Malformed coordinates!");
                        return false;
                    }
                } else if (args.length != 1) { // incomplete coordinates
                    sender.sendMessage(ChatColor.RED + "Invalid coordinates!");
                    return false;
                } else {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(ChatColor.RED + "Usage: /fakesnow realbiome <x> <y> <z> [world]");
                        return false;
                    }
                    location = ((Player) sender).getLocation();
                }
                sender.sendMessage(ChatColor.GREEN + "The current biome at "+location.getBlockX()+","+location.getBlockY()+","+location.getBlockZ()+": " +
                        location.getBlock().getBiome().getKey().toString());
                return true;
            }
        }
        sender.sendMessage(ChatColor.RED + "/fakesnow <refreshregions/realbiome>");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return Arrays.asList("refreshregions", "realbiome");
        } else if ("realbiome".equals(args[0]) && args.length <= 5 && sender instanceof Player) {
            // position of player
            Location location = ((Player) sender).getLocation();
            String[] pos = {""+location.getBlockX(), ""+location.getBlockY(), ""+location.getBlockZ(), location.getWorld().getName()};
            return Collections.singletonList(pos[args.length - 2]);
        } else {
            return Collections.emptyList();
        }
    }
}
