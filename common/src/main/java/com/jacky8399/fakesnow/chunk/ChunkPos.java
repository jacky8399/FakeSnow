package com.jacky8399.fakesnow.chunk;

public record ChunkPos(int x, int z) {

    @Override
    public int hashCode() {
        // ChunkPos#hash
        int i = 1664525 * x + 1013904223;
        int j = 1664525 * (z ^ -559038737) + 1013904223;
        return i ^ j;
    }

}