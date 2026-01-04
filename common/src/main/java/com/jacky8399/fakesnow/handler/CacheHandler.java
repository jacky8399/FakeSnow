package com.jacky8399.fakesnow.handler;

import com.jacky8399.fakesnow.cache.WorldWeatherCache;
import com.jacky8399.fakesnow.chunk.ChunkCache;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

public interface CacheHandler {

    @Nullable
    WorldWeatherCache loadWorld(World world);

    @Nullable
    ChunkCache loadChunk(Chunk chunk, WorldWeatherCache worldCache);

    default void unloadWorld(World world, WorldWeatherCache worldCache) {
        worldCache.unloadWorld();
    }

    default void unloadChunk(Chunk chunk, WorldWeatherCache worldCache, @Nullable ChunkCache chunkCache) {
        worldCache.unloadChunk(chunk.getX(), chunk.getZ());
    }

}
