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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandFakesnow implements TabExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GREEN + "You are running " + FakeSnow.get().getDescription().getFullName());
            return true;
        }
        switch (args[0]) {
            case "refreshregions" -> {
                Map<World, WeatherType> worldWeather = WeatherCache.worldCache.entrySet().stream()
                        .filter(entry -> entry.getValue().globalWeather() != null)
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().globalWeather()));
                WeatherCache.refreshCache(FakeSnow.get().cacheHandler);
                // try to refresh all chunks
                WeatherCache.worldCache.forEach((world, worldCache) -> {
                    WeatherType newWeather = worldCache.globalWeather();
                    if (newWeather != worldWeather.get(world)) {
                        // __global__ has changed, refresh the whole world
                        for (var chunk : world.getLoadedChunks()) {
                            world.refreshChunk(chunk.getX(), chunk.getZ());
                        }
                    } else {
                        for (WeatherCache.ChunkPos chunkPos : worldCache.chunkMap().keySet()) {
                            world.refreshChunk(chunkPos.x(), chunkPos.z());
                        }
                    }
                });

                sender.sendMessage(ChatColor.GREEN + "Reloaded " + Bukkit.getWorlds().size() + " worlds, cached " +
                        WeatherCache.worldCache.values().stream()
                                .mapToInt(worldCache -> worldCache.chunkMap().size())
                                .sum() + " chunks");
                return true;
            }
            case "realbiome" -> {
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
                sender.sendMessage(ChatColor.GREEN + "The current biome at " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ": " +
                        location.getBlock().getBiome().getKey());
                return true;
            }
            case "reload" -> {
                FakeSnow.get().reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
                return true;
            }
        }
        sender.sendMessage(ChatColor.RED + "/fakesnow <refreshregions/realbiome/reload>");
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length <= 1) {
            return List.of("refreshregions", "realbiome", "reload");
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
            return List.of();
        }
    }
}
