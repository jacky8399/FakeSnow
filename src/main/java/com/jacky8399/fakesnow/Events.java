package com.jacky8399.fakesnow;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class Events implements Listener {
    @EventHandler(priority = EventPriority.HIGH)
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk chunk = e.getChunk();
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(e.getWorld()));
        if (manager == null)
            return;
        World world = chunk.getWorld();
        WeatherCache.WorldCache worldCache = WeatherCache.getWorldCache(world);
        if (worldCache != null) {
            WeatherCache.addChunkToCache(worldCache, world, manager, chunk);
        }
    }
}
