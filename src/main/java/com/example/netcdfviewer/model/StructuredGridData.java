package com.example.netcdfviewer.model;

public record StructuredGridData(
    CoordinateBinding binding,
    double[] xAxis,
    double[] yAxis,
    double[][] xCoordinates,
    double[][] yCoordinates,
    int width,
    int height
) {
    public boolean rectilinear() {
        return xAxis != null && yAxis != null;
    }

    public boolean curvilinear() {
        return xCoordinates != null && yCoordinates != null;
    }
}
