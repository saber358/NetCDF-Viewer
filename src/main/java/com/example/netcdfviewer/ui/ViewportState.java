package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.model.MeshData;

/**
 * 视口状态对象。
 * 负责维护当前缩放比例、平移偏移以及自动适配状态。
 */
public final class ViewportState {
    // 当前缩放比例。
    private double scale = 1.0;
    // X 方向平移量。
    private double translateX;
    // Y 方向平移量。
    private double translateY;
    // 是否已经完成过初始化适配。
    private boolean initialized;
    // 是否保持自动适配模式。
    private boolean autoFit = true;

    public void ensureFitted(MeshData mesh, double width, double height) {
        // 只有在自动适配开启或尚未初始化时，才重新执行适配。
        if (autoFit || !initialized) {
            fit(mesh, width, height);
        }
    }

    public void fit(MeshData mesh, double width, double height) {
        // 如果网格或画布尺寸无效，则不做任何处理。
        if (mesh == null || width <= 0 || height <= 0 || mesh.nodeCount() == 0) {
            return;
        }

        // 初始化网格范围边界。
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        // 扫描所有节点的 X 范围。
        for (double value : mesh.x()) {
            minX = Math.min(minX, value);
            maxX = Math.max(maxX, value);
        }
        // 扫描所有节点的 Y 范围。
        for (double value : mesh.y()) {
            minY = Math.min(minY, value);
            maxY = Math.max(maxY, value);
        }

        // 计算网格在两个方向上的跨度，并提供最小保护值防止除零。
        double spanX = Math.max(1e-9, maxX - minX);
        double spanY = Math.max(1e-9, maxY - minY);
        // 预留画布边缘留白。
        double padding = 40.0;
        // 选择能够完整放下网格的最小缩放比例。
        scale = Math.min((width - padding * 2) / spanX, (height - padding * 2) / spanY);
        // 计算网格中心点。
        double centerX = (minX + maxX) * 0.5;
        double centerY = (minY + maxY) * 0.5;
        // 将网格中心平移到画布中心。
        translateX = width * 0.5 - centerX * scale;
        translateY = height * 0.5 + centerY * scale;
        // 标记已经完成初始化。
        initialized = true;
    }

    public void reset() {
        // 重置为初始状态，并恢复自动适配。
        initialized = false;
        autoFit = true;
        scale = 1.0;
        translateX = 0.0;
        translateY = 0.0;
    }

    public void pan(double deltaX, double deltaY) {
        // 用户手动平移后，自动适配应关闭。
        autoFit = false;
        // 累加平移偏移量。
        translateX += deltaX;
        translateY += deltaY;
        // 一旦手动交互，就认为视口已初始化。
        initialized = true;
    }

    public void zoom(double factor, double anchorX, double anchorY) {
        // 用户手动缩放后，自动适配应关闭。
        autoFit = false;
        // 将鼠标锚点转换回世界坐标，保证缩放围绕鼠标位置进行。
        double worldX = (anchorX - translateX) / scale;
        double worldY = (translateY - anchorY) / scale;
        // 应用新的缩放比例。
        scale *= factor;
        // 调整平移量，使缩放后锚点仍然落在原来的屏幕位置。
        translateX = anchorX - worldX * scale;
        translateY = anchorY + worldY * scale;
        // 标记为已初始化。
        initialized = true;
    }

    public double screenX(double worldX) {
        // 将世界坐标 X 转换为屏幕坐标 X。
        return worldX * scale + translateX;
    }

    public double screenY(double worldY) {
        // 将世界坐标 Y 转换为屏幕坐标 Y。
        return translateY - worldY * scale;
    }

    public Snapshot snapshot() {
        // 生成当前视口快照，供后台线程安全使用。
        return new Snapshot(scale, translateX, translateY);
    }

    public record Snapshot(double scale, double translateX, double translateY) {
        public double screenX(double worldX) {
            // 使用快照中的缩放和平移参数计算屏幕 X 坐标。
            return worldX * scale + translateX;
        }

        public double screenY(double worldY) {
            // 使用快照中的缩放和平移参数计算屏幕 Y 坐标。
            return translateY - worldY * scale;
        }
    }
}
