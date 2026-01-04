package com.jacky8399.fakesnow.handler;

import com.jacky8399.fakesnow.cache.AlwaysSnowyWorldCache;
import com.jacky8399.fakesnow.cache.WorldWeatherCache;
import com.jacky8399.fakesnow.chunk.ChunkCache;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

public final class AlwaysSnowyCacheHandler implements CacheHandler {

    @Override
    public @Nullable WorldWeatherCache loadWorld(World world) {
        return AlwaysSnowyWorldCache.instance();
    }

    @Override
    public @Nullable ChunkCache loadChunk(Chunk chunk, WorldWeatherCache worldCache) {
        return null;
    }

}
