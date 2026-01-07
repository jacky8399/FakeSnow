package com.jacky8399.fakesnow.chunk;

import com.jacky8399.fakesnow.WeatherType;
import org.jetbrains.annotations.Nullable;

public record ChunkCache(WeatherType[][] chunkCache) {

    public static int getBlockIndex(int x, int y, int z) {
        // 0-15 to 0-3
        x >>= 2;
        y >>= 2;
        z >>= 2;
        // iteration order is yxz
        return (y << 4) + (x << 2) + z;
    }

    public @Nullable WeatherType @Nullable [] getSectionCache(int sectionIndex) {
        return chunkCache[sectionIndex];
    }

    public @Nullable WeatherType getBlockWeather(int sectionIndex, int x, int y, int z) {
        var sectionCache = getSectionCache(sectionIndex);
        return sectionCache != null ? sectionCache[getBlockIndex(x, y, z)] : null;
    }

}