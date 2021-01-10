package com.jacky8399.fakesnow;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;

public class PacketListener extends PacketAdapter {
    @Override
    public void onPacketSending(PacketEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL)
            return;
        World world = player.getWorld();
        PacketContainer packet = event.getPacket();
        int x = packet.getIntegers().read(0), z = packet.getIntegers().read(1);
        Chunk chunk = world.getChunkAt(x, z);

        // check for __global__ first
        if (FakeSnow.get().regionWorldCache.get(world) != null) {
            ProtectedRegion globalRegion = FakeSnow.get().regionWorldCache.get(world);
            FakeSnow.WeatherType weatherType = globalRegion.getFlag(FakeSnow.CUSTOM_WEATHER_TYPE);
            if (weatherType != null) {
                Object biomeStorage = NMSUtils.cloneBiomeStorage(NMSUtils.getBiomeStorage(chunk));
                // set entire chunk to be that biome
                for (int i = 0; i < 4; i++) {
                    for (int j = 0; j < 16; j++) {
                        for (int k = 0; k < 4; k++) {
                            NMSUtils.setBiome(biomeStorage, i << 2, j << 2, k << 2, weatherType.biome);
                        }
                    }
                }
            }
        }

        ChunkCoordIntPair chunkCoords = new ChunkCoordIntPair(x, z);
        HashSet<ProtectedRegion> regions = PLUGIN.regionChunkCache.get(chunkCoords);
        if (regions == null || regions.size() == 0)
            return;

        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (manager == null)
            return;
        Object biomeStorage = NMSUtils.cloneBiomeStorage(NMSUtils.getBiomeStorage(chunk));
        // find things to change
        for (ProtectedRegion region : regions) {
            // check if in the correct world
            FakeSnow.WeatherType weather = region.getFlag(FakeSnow.CUSTOM_WEATHER_TYPE);
            if (!manager.hasRegion(region.getId()) || weather == null)
                continue;

            //FakeSnow.get().logger.info(String.format("Chunks (%d, %d) contained region %s", x, z, region.getId()));

            int blocks = 0;
            BlockVector3 min = region.getMinimumPoint(), max = region.getMaximumPoint();
//            for (int i = min.getBlockX(); i < max.getBlockX() && i > x * 16 && i < x * 16 + 15; i += 4) {
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 64; j++) {
//                    for (int k = min.getBlockZ(); k < max.getBlockZ() && k > z * 16 && k < z * 16 + 15; k += 4) {
                    for (int k = 0; k < 16; k++) {
                        // extra check just in case
//                        if (!region.contains(i, j, k))
//                            continue;
                        blocks++;
//                        NMSUtils.setBiome(biomeStorage, (i & 15) >> 2, j, (k & 15) >> 2, weather.biome);
                        NMSUtils.setBiome(biomeStorage, i, j, k, weather.biome);
                    }
                }
            }
            //FakeSnow.get().logger.info("Changed " + (blocks) + "blocks, I guess");
        }

        // write it back
        int[] biomes = NMSUtils.getBiomes(biomeStorage);
        packet.getIntegerArrays().write(0, biomes);
    }

    private static final FakeSnow PLUGIN = FakeSnow.get();
    public PacketListener() {
        super(PLUGIN, ListenerPriority.NORMAL, PacketType.Play.Server.MAP_CHUNK);
    }
}
