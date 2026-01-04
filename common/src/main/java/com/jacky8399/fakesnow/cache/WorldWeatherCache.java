package com.jacky8399.fakesnow.cache;

import com.jacky8399.fakesnow.WeatherType;
import com.jacky8399.fakesnow.chunk.ChunkCache;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

public interface WorldWeatherCache {

    @Nullable
    WeatherType getBlockWeather(World world, int x, int y, int z);

    boolean hasChunk(int chunkX, int chunkZ);

    void putChunkCache(int chunkX, int chunkZ, @Nullable ChunkCache chunkCache);

    /** {@return if a chunk section consists of only the global biome} */
    boolean isSectionUniform(@Nullable ChunkCache chunkCache, int sectionIndex);

    @Nullable
    ChunkCache getChunkCache(int chunkX, int chunkZ);

    @Nullable
    WeatherType globalWeather();

    void refreshChunks(World world);

    default void unloadChunk(int chunkX, int chunkZ) {}

    default void unloadWorld() {}

}