package com.jacky8399.fakesnow.handler;

import com.jacky8399.fakesnow.cache.AlwaysSnowyWorldCache;
import com.jacky8399.fakesnow.cache.WorldWeatherCache;
import com.jacky8399.fakesnow.chunk.ChunkCache;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

public class AlwaysSnowyCacheHandler implements CacheHandler {

    @Override
    public @Nullable WorldWeatherCache loadWorld(World world) {
        return world.getEnvironment() == World.Environment.NORMAL ? AlwaysSnowyWorldCache.instance() : null;
    }

    @Override
    public @Nullable ChunkCache loadChunk(Chunk chunk, WorldWeatherCache worldCache) {
        return null;
    }

}
