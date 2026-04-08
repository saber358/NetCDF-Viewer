package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.model.MeshData;

public final class ViewportState {
    private double scale = 1.0;
    private double translateX;
    private double translateY;
    private boolean initialized;
    private boolean autoFit = true;

    public void ensureFitted(MeshData mesh, double width, double height) {
        if (autoFit || !initialized) {
            fit(mesh, width, height);
        }
    }

    public void fit(MeshData mesh, double width, double height) {
        if (mesh == null || width <= 0 || height <= 0 || mesh.nodeCount() == 0) {
            return;
        }

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (double value : mesh.x()) {
            minX = Math.min(minX, value);
            maxX = Math.max(maxX, value);
        }
        for (double value : mesh.y()) {
            minY = Math.min(minY, value);
            maxY = Math.max(maxY, value);
        }

        double spanX = Math.max(1e-9, maxX - minX);
        double spanY = Math.max(1e-9, maxY - minY);
        double padding = 40.0;
        scale = Math.min((width - padding * 2) / spanX, (height - padding * 2) / spanY);
        double centerX = (minX + maxX) * 0.5;
        double centerY = (minY + maxY) * 0.5;
        translateX = width * 0.5 - centerX * scale;
        translateY = height * 0.5 + centerY * scale;
        initialized = true;
    }

    public void reset() {
        initialized = false;
        autoFit = true;
        scale = 1.0;
        translateX = 0.0;
        translateY = 0.0;
    }

    public void pan(double deltaX, double deltaY) {
        autoFit = false;
        translateX += deltaX;
        translateY += deltaY;
        initialized = true;
    }

    public void zoom(double factor, double anchorX, double anchorY) {
        autoFit = false;
        double worldX = (anchorX - translateX) / scale;
        double worldY = (translateY - anchorY) / scale;
        scale *= factor;
        translateX = anchorX - worldX * scale;
        translateY = anchorY + worldY * scale;
        initialized = true;
    }

    public double screenX(double worldX) {
        return worldX * scale + translateX;
    }

    public double screenY(double worldY) {
        return translateY - worldY * scale;
    }

    public Snapshot snapshot() {
        return new Snapshot(scale, translateX, translateY);
    }

    public record Snapshot(double scale, double translateX, double translateY) {
        public double screenX(double worldX) {
            return worldX * scale + translateX;
        }

        public double screenY(double worldY) {
            return translateY - worldY * scale;
        }
    }
}
