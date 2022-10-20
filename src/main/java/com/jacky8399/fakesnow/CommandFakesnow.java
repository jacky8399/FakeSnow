package com.jacky8399.fakesnow;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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
                // Clear old cache first
                WeatherCache.refreshCache();
                sender.sendMessage(ChatColor.GREEN + "Reloaded " + Bukkit.getWorlds().size() + " worlds");
//                sender.sendMessage(ChatColor.GREEN + "Discovered " +
//                        FakeSnow.get().regionChunkCache.values().stream().mapToInt(Set::size).sum() + " region(s)");
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
            case "checkchunk": {
                var location = ((Player) sender).getLocation();
                var worldCache = WeatherCache.getWorldCache(location.getWorld());
                var chunkCache = worldCache.getChunkCache(location.getBlockX() / 16, location.getBlockZ() / 16);
                if (chunkCache == null) {
                    sender.sendMessage(ChatColor.RED + "No chunk cache");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Has chunk cache");
                }
            }
        }
        sender.sendMessage(ChatColor.RED + "/fakesnow <refreshregions/realbiome>");
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length <= 1) {
            return Arrays.asList("refreshregions", "realbiome");
        } else if ("realbiome".equals(args[0]) && args.length <= 5 && sender instanceof Player player) {
            // position of player
            Location location = player.getLocation();

            return Collections.singletonList(switch (args.length) {
                case 2 -> ""+location.getBlockX();
                case 3 -> ""+location.getBlockY();
                case 4 -> ""+location.getBlockZ();
                case 5 -> location.getWorld().getName();
                default -> throw new IllegalArgumentException();
            });
        } else {
            return Collections.emptyList();
        }
    }
}
