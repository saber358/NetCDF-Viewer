package com.example.netcdfviewer.overlay;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 简化版 Shapefile 几何解析器。
 * 当前只读取 .shp 文件中的线和面坐标，不读取属性表。
 */
public final class ShapefileOverlayLoader {
    private static final int HEADER_BYTES = 100;

    private ShapefileOverlayLoader() {
        // 工具类不允许被实例化。
    }

    public static CoastlineOverlay load(Path path) throws IOException {
        List<OverlayPath> paths = new ArrayList<>();
        try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
            skipHeader(input);
            while (true) {
                try {
                    readRecord(input, paths);
                } catch (EOFException exception) {
                    break;
                }
            }
        }
        return new CoastlineOverlay(path.toAbsolutePath().normalize(), path.getFileName().toString(), paths);
    }

    private static void skipHeader(DataInputStream input) throws IOException {
        input.readNBytes(HEADER_BYTES);
    }

    private static void readRecord(DataInputStream input, List<OverlayPath> paths) throws IOException {
        input.readInt();
        int contentLengthWords = input.readInt();
        int contentLengthBytes = contentLengthWords * 2;
        byte[] content = input.readNBytes(contentLengthBytes);
        if (content.length != contentLengthBytes) {
            throw new EOFException("Unexpected end of shapefile record.");
        }
        parseRecordContent(content, paths);
    }

    private static void parseRecordContent(byte[] content, List<OverlayPath> paths) {
        if (content.length < Integer.BYTES) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        int shapeType = buffer.getInt();
        if (!isSupportedPathShape(shapeType)) {
            return;
        }
        if (buffer.remaining() < 32 + Integer.BYTES * 2) {
            return;
        }
        buffer.position(buffer.position() + 32);
        int partCount = buffer.getInt();
        int pointCount = buffer.getInt();
        if (partCount <= 0 || pointCount <= 1 || buffer.remaining() < partCount * Integer.BYTES + pointCount * 16) {
            return;
        }

        int[] parts = new int[partCount];
        for (int index = 0; index < partCount; index++) {
            parts[index] = buffer.getInt();
        }

        double[] allX = new double[pointCount];
        double[] allY = new double[pointCount];
        for (int index = 0; index < pointCount; index++) {
            allX[index] = buffer.getDouble();
            allY[index] = buffer.getDouble();
        }

        for (int partIndex = 0; partIndex < partCount; partIndex++) {
            int start = parts[partIndex];
            int end = partIndex + 1 < partCount ? parts[partIndex + 1] : pointCount;
            if (start < 0 || end > pointCount || end - start < 2) {
                continue;
            }
            double[] x = new double[end - start];
            double[] y = new double[end - start];
            System.arraycopy(allX, start, x, 0, x.length);
            System.arraycopy(allY, start, y, 0, y.length);
            paths.add(new OverlayPath(x, y));
        }
    }

    private static boolean isSupportedPathShape(int shapeType) {
        return shapeType == 3
            || shapeType == 5
            || shapeType == 13
            || shapeType == 15
            || shapeType == 23
            || shapeType == 25;
    }
}
