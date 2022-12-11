package com.jacky8399.fakesnow;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.EnumFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.logging.Logger;

import static com.jacky8399.fakesnow.WeatherCache.getIndex;

public class WorldGuardCacheHandler implements CacheHandler {
    private static Logger LOGGER;

    public static EnumFlag<WeatherType> CUSTOM_WEATHER_TYPE;

    @SuppressWarnings("unchecked")
    public static void tryAddFlag() {
        try {
            CUSTOM_WEATHER_TYPE = new EnumFlag<>("custom-weather-type", WeatherType.class);
            WorldGuard.getInstance().getFlagRegistry().register(WorldGuardCacheHandler.CUSTOM_WEATHER_TYPE);
        } catch (FlagConflictException e) {
            var flag = WorldGuard.getInstance().getFlagRegistry().get("custom-weather-type");
            if (!(flag instanceof EnumFlag<?> enumFlag) || enumFlag.getEnumClass() != WeatherType.class)
                throw new Error("Another plugin has already registered flag 'custom-weather-type'!", e);
            CUSTOM_WEATHER_TYPE = (EnumFlag<WeatherType>) flag;
        }
    }

    static {
        tryAddFlag();
    }

    @Override
    public @Nullable WeatherCache.WorldCache loadWorld(World world) {
        var regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (regionManager == null)
            return null;
        // global weather
        var globalRegion = regionManager.getRegion("__global__");
        WeatherType globalWeather = null;
        if (globalRegion != null) {
            globalWeather = globalRegion.getFlag(WorldGuardCacheHandler.CUSTOM_WEATHER_TYPE);
        }

        var loadedChunks = world.getLoadedChunks();
        var weatherCache = new WeatherCache.WorldCache(globalWeather, new HashMap<>());

        for (var chunk : loadedChunks) {
            addChunkToCache(weatherCache, world, regionManager, chunk);
        }
        return weatherCache;
    }
    private static final int QUERY_OFFSET = 2;
    public WeatherCache.ChunkCache addChunkToCache(WeatherCache.WorldCache cache, World world, RegionManager regionManager, Chunk chunk) {

        // TODO WorldGuard might support async queries
        int xOffset = chunk.getX() * 16;
        int zOffset = chunk.getZ() * 16;

        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight();

        WeatherType globalWeather = cache.globalWeather();

        var regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
        var query = regionContainer.createQuery();
        var location = new com.sk89q.worldedit.util.Location(BukkitAdapter.adapt(world));

        long startTime = System.nanoTime();

        var fakeRegion = new ProtectedCuboidRegion("dummy", true,
                BlockVector3.at(xOffset, minHeight, zOffset),
                BlockVector3.at(xOffset + 15, maxHeight, zOffset + 15));
        var chunkRegionSet = regionManager.getApplicableRegions(fakeRegion);

        if (chunkRegionSet.isVirtual() || chunkRegionSet.size() == 0)
            return null; // chunk does not contain regions

        long chunkTime = System.nanoTime();

        // number of sections (subchunks)
        WeatherType[][] chunkCache = new WeatherType[(maxHeight - minHeight) / 16][];
        boolean changed = false;

        for (int y = 0; y < maxHeight - minHeight; y += 4) {
            int sectionIndex = y >> 4;
            WeatherType[] sectionCache = chunkCache[sectionIndex];
            if (sectionCache == null) {
                sectionCache = new WeatherType[4 * 4 * 4];
                chunkCache[sectionIndex] = sectionCache;
            }

            for (int x = 0; x < 16; x += 4) {
                for (int z = 0; z < 16; z += 4) {
                    location = location.setPosition(Vector3.at(xOffset + x + QUERY_OFFSET, y + minHeight, zOffset + z + QUERY_OFFSET));
                    var regionSet = query.getApplicableRegions(location);
                    // queryValue respects priority, and ignores multiple values
                    // (from regions with the same priority)
                    var blockWeather = regionSet.queryValue(null, WorldGuardCacheHandler.CUSTOM_WEATHER_TYPE);
                    if (blockWeather == null || blockWeather == globalWeather)
                        continue; // no need to store null/global weather
                    sectionCache[getIndex(x, y & 0xF, z)] = blockWeather;
                    changed = true;
                }
            }
        }

        long queryTime = System.nanoTime();
        if (Config.debug) {
            LOGGER.info("Caching chunk (%d, %d) (contains weather data: %b):\n chunk query: %dns, blocks query: %dns"
                    .formatted(chunk.getX(), chunk.getZ(), changed,
                            chunkTime - startTime, queryTime - chunkTime));
        }

        if (changed) {
            // chunk contains custom weather type
            return new WeatherCache.ChunkCache(chunkCache);
        }
        return null;
    }


    @Override
    public void unloadWorld(World world, WeatherCache.@Nullable WorldCache cache) {

    }

    @Override
    public @Nullable WeatherCache.ChunkCache loadChunk(Chunk chunk, WeatherCache.WorldCache worldCache) {
        World world = chunk.getWorld();
        var regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (regionManager == null)
            return null;
        return addChunkToCache(worldCache, world, regionManager, chunk);
    }

    @Override
    public void unloadChunk(Chunk chunk, WeatherCache.WorldCache worldCache, WeatherCache.@Nullable ChunkCache chunkCache) {

    }

}
