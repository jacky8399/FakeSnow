package com.jacky8399.fakesnow;

import com.jacky8399.fakesnow.cache.WorldWeatherCache;
import com.jacky8399.fakesnow.chunk.ChunkCache;
import com.jacky8399.fakesnow.handler.CacheHandler;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.WeakHashMap;

public class WorldWeatherManager {

    private static WorldWeatherManager instance;

    private final WeakHashMap<World, WorldWeatherCache> worldCache = new WeakHashMap<>();

    private CacheHandler cacheHandler;

    private WorldWeatherManager(CacheHandler cacheHandler) {
        this.cacheHandler = cacheHandler;
    }

    public static WorldWeatherManager updateInstance(CacheHandler cacheHandler) {
        if (instance == null) {
            instance = new WorldWeatherManager(cacheHandler);
        } else {
            instance.setCacheHandler(cacheHandler);
        }

        return instance;
    }

    public static WorldWeatherManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("WorldWeatherManager is not initialized yet");
        }
        return instance;
    }

    public void setCacheHandler(CacheHandler cacheHandler) {
        this.cacheHandler = cacheHandler;
    }

    public void loadWorld(World world) {
        var cache = cacheHandler.loadWorld(world);
        if (cache != null) worldCache.put(world, cache);
    }

    public void unloadWorld(World world) {
        var cache = worldCache.remove(world);
        if (cache != null) cacheHandler.unloadWorld(world, cache);
    }

    public void loadChunk(Chunk chunk) {
        WorldWeatherCache cache = getWorldCache(chunk.getWorld());
        // loadChunk won't be called if the world has no weather support
        if (cache != null) {
            ChunkCache chunkCache = cacheHandler.loadChunk(chunk, cache);
            cache.putChunkCache(chunk.getX(), chunk.getZ(), chunkCache);
        }
    }

    public void unloadChunk(Chunk chunk) {
        var cache = worldCache.get(chunk.getWorld());
        if (cache != null) {
            cache.unloadChunk(chunk.getX(), chunk.getZ());
        }
    }

    public void refreshCache() {
        worldCache.clear();

        for (World world : Bukkit.getWorlds()) {
            worldCache.put(world, cacheHandler.loadWorld(world));
        }
    }

    public void clearCache() {
        worldCache.clear();
    }

    @Nullable
    public WorldWeatherCache getWorldCache(World world) {
        return worldCache.get(world);
    }

}
