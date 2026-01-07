package com.jacky8399.fakesnow.v1_21_10_R1;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.jacky8399.fakesnow.PacketListener;
import com.jacky8399.fakesnow.WeatherType;
import com.jacky8399.fakesnow.WorldWeatherManager;
import com.jacky8399.fakesnow.cache.WorldWeatherCache;
import com.jacky8399.fakesnow.chunk.ChunkCache;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBiome;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PacketListener_v1_21_10_R1 extends PacketListener {

    private static final long MAX_NANOS_PER_TICK  = 1_000_000;

    private final Map<UUID, Set<ChunkPos>> biomeUpdateQueue = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final WorldWeatherManager weatherManager = WorldWeatherManager.getInstance();

    public PacketListener_v1_21_10_R1(Plugin plugin) {
        super(plugin);
        this.plugin = plugin;
        startTask();
    }

    private void startTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    private void tick() {
        if (biomeUpdateQueue.isEmpty()) return;

        Iterator<Map.Entry<UUID, Set<ChunkPos>>> iterator = biomeUpdateQueue.entrySet().iterator();
        long startTime = System.nanoTime();

        while (iterator.hasNext()) {

            if (System.nanoTime() - startTime > MAX_NANOS_PER_TICK) {
                break;
            }

            Map.Entry<UUID, Set<ChunkPos>> entry = iterator.next();
            UUID playerId = entry.getKey();
            Set<ChunkPos> positions = entry.getValue();

            iterator.remove();

            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                if (positions != null && !positions.isEmpty()) {
                    sendBatchBiomePacket(player, positions);
                }
            }
        }
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        var worldCache = weatherManager.getWorldCache(world);
        if (worldCache == null) return;

        PacketContainer packet = event.getPacket();

        int chunkX = packet.getIntegers().read(0);
        int chunkZ = packet.getIntegers().read(1);

        if (!worldCache.hasChunk(chunkX, chunkZ)) return;

        biomeUpdateQueue.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>())
                .add(new ChunkPos(chunkX, chunkZ));
    }

    private void sendBatchBiomePacket(Player player, Set<ChunkPos> positions) {
        World world = player.getWorld();
        var worldCache = weatherManager.getWorldCache(world);
        if (worldCache == null) return;

        List<ClientboundChunksBiomesPacket.ChunkBiomeData> chunkDataList = new ArrayList<>();
        Level level = ((CraftWorld) world).getHandle();

        for (ChunkPos pos : positions) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) continue;

            PalettedContainer<Holder<Biome>>[] biomes = copyBiomes(chunk, chunk.getSections(), worldCache);
            byte[] serialized = serializeBiomes(biomes);

            chunkDataList.add(new ClientboundChunksBiomesPacket.ChunkBiomeData(pos, serialized));
        }

        if (!chunkDataList.isEmpty()) {
            ClientboundChunksBiomesPacket batchPacket = new ClientboundChunksBiomesPacket(chunkDataList);
            ((CraftPlayer) player).getHandle().connection.send(batchPacket);
        }
    }

    // single packet
//    private void sendBiomePacket(Player player, int chunkX, int chunkZ) {
//        if (!player.isOnline()) return;
//
//        CraftWorld craftWorld = (CraftWorld) player.getWorld();
//        Level level = craftWorld.getHandle();
//
//        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
//        if (chunk == null) return;
//
//        var worldCache = weatherManager.getWorldCache(player.getWorld());
//        if (worldCache == null) return;
//
//        PalettedContainer<Holder<Biome>>[] biomes = copyBiomes(chunk, chunk.getSections(), worldCache);
//        byte[] biomeBuffer = serializeBiomes(biomes);
//
//        ClientboundChunksBiomesPacket biomePacket =
//                new ClientboundChunksBiomesPacket(
//                        List.of(
//                                new ClientboundChunksBiomesPacket.ChunkBiomeData(
//                                        chunk.getPos(),
//                                        biomeBuffer
//                                )
//                        )
//                );
//
//        ((CraftPlayer) player).getHandle().connection.send(biomePacket);
//    }

    private static PalettedContainer<Holder<Biome>>[] copyBiomes(LevelChunk nmsChunk, LevelChunkSection[] sections, WorldWeatherCache worldCache) {
        int chunkX = nmsChunk.getPos().x;
        int chunkZ = nmsChunk.getPos().z;

        @SuppressWarnings("unchecked")
        PalettedContainer<Holder<Biome>>[] arr = new PalettedContainer[sections.length];

        var biomeStrategy = nmsChunk.level.palettedContainerFactory().biomeStrategy();

        var weatherToNmsBiomeMap = new EnumMap<WeatherType, Holder<Biome>>(WeatherType.class);
        for (var weatherType : WeatherType.values()) {
            weatherToNmsBiomeMap.put(weatherType, CraftBiome.bukkitToMinecraftHolder(weatherType.biome));
        }

        ChunkCache chunkCache = worldCache.getChunkCache(chunkX, chunkZ);
        WeatherType globalWeather = worldCache.globalWeather();

        for (int idx = 0; idx < sections.length; idx++) {
            PalettedContainer<Holder<Biome>> container;

            if (worldCache.isSectionUniform(chunkCache, idx)) {
                var uniformBiomeHolder = weatherToNmsBiomeMap.get(globalWeather);
                container = new PalettedContainer<>(uniformBiomeHolder, biomeStrategy);
            } else {
                container = sections[idx].getBiomes().copy();
                WeatherType[] sectionCache = chunkCache != null ? chunkCache.getSectionCache(idx) : null;

                if (sectionCache != null) {
                    for (int j = 0; j < 4; j++) {
                        for (int i = 0; i < 4; i++) {
                            for (int k = 0; k < 4; k++) {
                                var blockWeather = sectionCache[ChunkCache.getBlockIndex(i << 2, j << 2, k << 2)];
                                WeatherType targetWeather = (blockWeather != null) ? blockWeather : globalWeather;

                                if (targetWeather != null) {
                                    container.set(i, j, k, weatherToNmsBiomeMap.get(targetWeather));
                                }
                            }
                        }
                    }
                }
            }

            arr[idx] = container;
        }

        return arr;
    }

    public static byte[] serializeBiomes(PalettedContainer<Holder<Biome>>[] biomes) {
        ByteBuf byteBuf = Unpooled.buffer();
        FriendlyByteBuf buf = new FriendlyByteBuf(byteBuf);

        for (var container : biomes) {
            if (container != null) {
                container.write(buf);
            }
        }

        byte[] arr = new byte[buf.readableBytes()];
        buf.readBytes(arr);
        return arr;
    }

}
