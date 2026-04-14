package com.example.netcdfviewer.overlay;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShapefileOverlayLoaderTest {
    @Test
    void polylineShapefileBecomesSingleOverlayPath() throws Exception {
        Path file = Files.createTempFile("coastline-shape-", ".shp");
        try {
            Files.write(file, createPolylineShpBytes());

            CoastlineOverlay overlay = CoastlineOverlayLoader.load(file);

            assertEquals(1, overlay.paths().size());
            assertEquals(2, overlay.paths().get(0).x().length);
            assertEquals(120.0, overlay.paths().get(0).x()[0]);
            assertEquals(31.0, overlay.paths().get(0).y()[1]);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private byte[] createPolylineShpBytes() throws Exception {
        byte[] recordContent = createPolylineRecordContent();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(output);

        data.writeInt(9994);
        for (int index = 0; index < 5; index++) {
            data.writeInt(0);
        }
        data.writeInt((100 + 8 + recordContent.length) / 2);

        ByteBuffer header = ByteBuffer.allocate(72).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(1000);
        header.putInt(3);
        header.putDouble(120.0);
        header.putDouble(30.0);
        header.putDouble(121.0);
        header.putDouble(31.0);
        header.putDouble(0.0);
        header.putDouble(0.0);
        header.putDouble(0.0);
        header.putDouble(0.0);
        data.write(header.array());

        data.writeInt(1);
        data.writeInt(recordContent.length / 2);
        data.write(recordContent);
        data.flush();
        return output.toByteArray();
    }

    private byte[] createPolylineRecordContent() {
        ByteBuffer buffer = ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(3);
        buffer.putDouble(120.0);
        buffer.putDouble(30.0);
        buffer.putDouble(121.0);
        buffer.putDouble(31.0);
        buffer.putInt(1);
        buffer.putInt(2);
        buffer.putInt(0);
        buffer.putDouble(120.0);
        buffer.putDouble(30.0);
        buffer.putDouble(121.0);
        buffer.putDouble(31.0);
        return buffer.array();
    }
}
