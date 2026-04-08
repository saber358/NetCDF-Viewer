package com.example.netcdfviewer.render;

public record RangeStats(double min, double max, int validCount) {
    public boolean empty() {
        return validCount <= 0;
    }

    public double span() {
        return max - min;
    }
}
