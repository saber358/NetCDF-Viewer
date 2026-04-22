package com.example.netcdfviewer.basemap;

public record TileAddress(int z, int x, int y) {
    public TileAddress {
        if (z < 0 || x < 0 || y < 0) {
            throw new IllegalArgumentException("瓦片编号不能为负数。");
        }
    }
}
