package com.jacky8399.fakesnow;

import com.comphenix.protocol.wrappers.ChunkCoordIntPair;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.HashSet;

public class Events implements Listener {
    private static int nearestChunkCoord(int i) {
        return i >> 4 << 4;
    }

    public static void addRegionToCache(ProtectedRegion region, World world) {
        if (region instanceof GlobalProtectedRegion) {
            FakeSnow.get().regionWorldCache.put(world, region);
            return;
        }

        BlockVector3 min = region.getMinimumPoint(), max = region.getMaximumPoint();
        for (int i = nearestChunkCoord(min.getBlockX()); i < nearestChunkCoord(max.getBlockX() + 15); i += 16) {
            for (int k = nearestChunkCoord(min.getBlockZ()); k < nearestChunkCoord(max.getBlockZ() + 15); k += 16) {
                ChunkCoordIntPair coords = new ChunkCoordIntPair((int) Math.floor(i / 16f), (int) Math.floor(k / 16f));
                FakeSnow.get().regionChunkCache.computeIfAbsent(coords, ignored -> new HashSet<>()).add(region);
            }
        }
    }

    public static void addRegionsToCache(World world) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard"))
            return;
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (manager != null) {
            for (ProtectedRegion region : manager.getRegions().values()) {
                addRegionToCache(region, world);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk chunk = e.getChunk();
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(e.getWorld()));
        if (manager == null)
            return;
        ProtectedCuboidRegion area = new ProtectedCuboidRegion("dummy", BlockVector3.at(chunk.getX() * 16, 0, chunk.getZ() * 16), BlockVector3.at(chunk.getX() * 16 + 15, 0, chunk.getZ() * 16 + 15));
        ApplicableRegionSet set = manager.getApplicableRegions(area);
        for (ProtectedRegion region : set) {
            addRegionToCache(region, e.getWorld());
        }
    }
}
