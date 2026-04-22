package com.example.netcdfviewer.basemap;

public final class TileUrlTemplate {
    private TileUrlTemplate() {
    }

    public static String expand(BaseMapLayer layer, TileAddress address) {
        String subdomain = resolveSubdomain(layer, address);
        return layer.urlTemplate()
            .replace("{z}", Integer.toString(address.z()))
            .replace("{x}", Integer.toString(address.x()))
            .replace("{y}", Integer.toString(address.y()))
            .replace("{s}", subdomain)
            .replace("{tk}", layer.token());
    }

    private static String resolveSubdomain(BaseMapLayer layer, TileAddress address) {
        if (layer.subdomains().isEmpty()) {
            return "";
        }
        int index = Math.floorMod(address.x() + address.y(), layer.subdomains().size());
        return layer.subdomains().get(index);
    }
}
