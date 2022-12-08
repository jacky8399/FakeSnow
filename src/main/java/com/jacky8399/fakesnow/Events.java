package com.jacky8399.fakesnow;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class Events implements Listener {
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk chunk = e.getChunk();
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(e.getWorld()));
        if (manager == null)
            return;
        World world = chunk.getWorld();
        WeatherCache.WorldCache worldCache = WeatherCache.getWorldCache(world);
        if (worldCache != null) {
            long start = System.nanoTime();
            // TODO WorldGuard might support async queries
            WeatherCache.addChunkToCache(worldCache, world, manager, chunk);
            if (false && Config.debug) {
                long end = System.nanoTime();
                FakeSnow.get().logger.info("Adding chunk " + chunk.getX() + "," + chunk.getZ() + " took " +
                        (end - start) + "ns");
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        Chunk chunk = e.getChunk();
        World world = chunk.getWorld();
        WeatherCache.WorldCache worldCache = WeatherCache.getWorldCache(world);
        if (worldCache != null) {
            worldCache.removeChunkCache(chunk.getX(), chunk.getZ());
        }
    }
}
