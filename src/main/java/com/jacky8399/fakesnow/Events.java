package com.jacky8399.fakesnow;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public class Events implements Listener {

    private final WorldWeatherManager worldWeatherManager = WorldWeatherManager.getInstance();

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        worldWeatherManager.loadWorld(e.getWorld());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent e) {
        worldWeatherManager.unloadWorld(e.getWorld());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        worldWeatherManager.loadChunk(e.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        worldWeatherManager.unloadChunk(e.getChunk());
    }

}
