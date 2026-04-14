package com.example.netcdfviewer.overlay;

import java.nio.file.Path;
import java.util.List;

/**
 * 单个海岸线叠加层。
 * 保存来源文件和解析后的路径集合。
 */
public record CoastlineOverlay(Path sourcePath, String displayName, List<OverlayPath> paths) {
    public CoastlineOverlay {
        if (sourcePath == null) {
            throw new IllegalArgumentException("Overlay source path must not be null.");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Overlay display name must not be blank.");
        }
        paths = List.copyOf(paths);
    }
}
