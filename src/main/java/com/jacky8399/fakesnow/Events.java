package com.jacky8399.fakesnow;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public class Events implements Listener {
    private static final FakeSnow PLUGIN = FakeSnow.get();
    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        World world = e.getWorld();
        WeatherCache.WorldCache worldCache = PLUGIN.cacheHandler.loadWorld(world);
        if (worldCache != null)
            WeatherCache.worldCache.put(world, worldCache);
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent e) {
        World world = e.getWorld();
        PLUGIN.cacheHandler.unloadWorld(world, WeatherCache.worldCache.remove(world));
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        World world = e.getWorld();
        Chunk chunk = e.getChunk();
        WeatherCache.WorldCache worldCache = WeatherCache.getWorldCache(world);
        // loadChunk won't be called if the world has no weather support
        if (worldCache != null) {
            WeatherCache.ChunkCache chunkCache = PLUGIN.cacheHandler.loadChunk(chunk, worldCache);
            if (chunkCache != null)
                worldCache.chunkMap().put(new WeatherCache.ChunkPos(chunk.getX(), chunk.getZ()), chunkCache);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        Chunk chunk = e.getChunk();
        World world = chunk.getWorld();
        WeatherCache.WorldCache worldCache = WeatherCache.getWorldCache(world);
        if (worldCache != null) {
            WeatherCache.ChunkCache chunkCache = worldCache.removeChunkCache(chunk.getX(), chunk.getZ());
            PLUGIN.cacheHandler.unloadChunk(chunk, worldCache, chunkCache);
        }
    }
}
