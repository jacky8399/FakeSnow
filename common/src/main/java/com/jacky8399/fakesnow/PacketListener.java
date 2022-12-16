package com.jacky8399.fakesnow;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.logging.Logger;

public abstract class PacketListener extends PacketAdapter {
    protected final Plugin plugin;
    protected final Logger logger;
    public PacketListener(Plugin plugin) {
        super(plugin, ListenerPriority.NORMAL, List.of(PacketType.Play.Server.MAP_CHUNK));
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public abstract void onPacketSending(PacketEvent event);
}
