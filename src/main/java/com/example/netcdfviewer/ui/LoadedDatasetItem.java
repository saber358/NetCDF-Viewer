package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.io.ParsedDataset;

import java.nio.file.Path;

/**
 * 已加载数据集条目。
 * 用于在界面列表中展示并在控制器中跟踪已打开的数据集。
 */
public record LoadedDatasetItem(Path sourcePath, String displayName, ParsedDataset dataset) {
    @Override
    public String toString() {
        // 列表默认显示数据集名称。
        return displayName;
    }
}
