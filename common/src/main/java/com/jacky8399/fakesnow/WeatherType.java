package com.jacky8399.fakesnow;

import org.bukkit.block.Biome;

public enum WeatherType {
    RAIN(Biome.FOREST),
    SNOW(Biome.SNOWY_TAIGA),
    NONE(Biome.THE_VOID),
    DUST(Biome.BASALT_DELTAS);
    public final Biome biome;

    WeatherType(Biome biome) {
        this.biome = biome;
    }
}
