package com.example.netcdfviewer.render;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowLineOverlayRendererTest {
    private final FlowLineOverlayRenderer renderer = new FlowLineOverlayRenderer();

    @BeforeAll
    static void initToolkit() {
        new JFXPanel();
        Platform.setImplicitExit(false);
    }

    @Test
    void buildStrokeRangesCreatesLocalizedBlackBrushStrokes() {
        FlowLineGenerator.FlowLine line = FlowLineGenerator.FlowLine.fromPoints(List.of(
            new FlowLineGenerator.FlowPoint(10.0, 20.0),
            new FlowLineGenerator.FlowPoint(110.0, 20.0)
        ));

        List<FlowLineOverlayRenderer.StrokeRange> ranges = renderer.buildStrokeRanges(line, 0.0);
        double paintedLength = ranges.stream()
            .mapToDouble(range -> range.endLength() - range.startLength())
            .sum();

        assertFalse(ranges.isEmpty());
        assertTrue(ranges.stream().allMatch(range -> range.endLength() > range.startLength()));
        assertTrue(paintedLength < line.totalLength() * 0.70);
    }

    @Test
    void buildStrokeRangesMoveForwardWhenPhaseChanges() {
        FlowLineGenerator.FlowLine line = FlowLineGenerator.FlowLine.fromPoints(List.of(
            new FlowLineGenerator.FlowPoint(10.0, 20.0),
            new FlowLineGenerator.FlowPoint(110.0, 20.0)
        ));

        List<FlowLineOverlayRenderer.StrokeRange> phaseZero = renderer.buildStrokeRanges(line, 0.0);
        List<FlowLineOverlayRenderer.StrokeRange> phaseLater = renderer.buildStrokeRanges(line, 0.35);

        assertNotEquals(phaseZero, phaseLater);
        assertTrue(phaseLater.get(0).startLength() > phaseZero.get(0).startLength());
    }

    @Test
    void renderPaintsSeparatedBlackBrushStrokesInsteadOfWholeBody() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<Color> headRef = new AtomicReference<>();
        AtomicReference<Color> gapRef = new AtomicReference<>();
        AtomicReference<Color> tailRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Canvas canvas = new Canvas(120, 40);
                FlowLineGenerator.FlowLine line = FlowLineGenerator.FlowLine.fromPoints(List.of(
                    new FlowLineGenerator.FlowPoint(10.0, 20.0),
                    new FlowLineGenerator.FlowPoint(110.0, 20.0)
                ));

                renderer.render(canvas.getGraphicsContext2D(), List.of(line), 0.0);
                var reader = canvas.snapshot(new SnapshotParameters(), null).getPixelReader();
                headRef.set(reader.getColor(20, 20));
                gapRef.set(reader.getColor(35, 20));
                tailRef.set(reader.getColor(94, 20));
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(await(latch));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
        assertTrue(headRef.get().getBrightness() < 0.2);
        assertTrue(gapRef.get().getBrightness() > 0.95);
        assertTrue(tailRef.get().getBrightness() < 0.2);
    }

    @Test
    void renderMovesBlackBrushStrokesWhenPhaseChanges() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<Color> phaseZeroMidRef = new AtomicReference<>();
        AtomicReference<Color> phaseZeroTailRef = new AtomicReference<>();
        AtomicReference<Color> phaseLaterMidRef = new AtomicReference<>();
        AtomicReference<Color> phaseLaterTailRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FlowLineGenerator.FlowLine line = FlowLineGenerator.FlowLine.fromPoints(List.of(
                    new FlowLineGenerator.FlowPoint(10.0, 20.0),
                    new FlowLineGenerator.FlowPoint(110.0, 20.0)
                ));

                Canvas phaseZeroCanvas = new Canvas(120, 40);
                renderer.render(phaseZeroCanvas.getGraphicsContext2D(), List.of(line), 0.0);
                var phaseZeroReader = phaseZeroCanvas.snapshot(new SnapshotParameters(), null).getPixelReader();
                phaseZeroMidRef.set(phaseZeroReader.getColor(35, 20));
                phaseZeroTailRef.set(phaseZeroReader.getColor(94, 20));

                Canvas phaseLateCanvas = new Canvas(120, 40);
                renderer.render(phaseLateCanvas.getGraphicsContext2D(), List.of(line), 0.35);
                var phaseLaterReader = phaseLateCanvas.snapshot(new SnapshotParameters(), null).getPixelReader();
                phaseLaterMidRef.set(phaseLaterReader.getColor(35, 20));
                phaseLaterTailRef.set(phaseLaterReader.getColor(94, 20));
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(await(latch));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
        Color phaseZeroMid = phaseZeroMidRef.get();
        Color phaseZeroTail = phaseZeroTailRef.get();
        Color phaseLaterMid = phaseLaterMidRef.get();
        Color phaseLaterTail = phaseLaterTailRef.get();
        assertNotEquals(phaseZeroMid, phaseLaterMid);
        assertNotEquals(phaseZeroTail, phaseLaterTail);
        assertTrue(phaseZeroMid.getBrightness() > 0.95);
        assertTrue(phaseZeroTail.getBrightness() < 0.2);
        assertTrue(phaseLaterMid.getBrightness() < 0.2);
        assertTrue(phaseLaterTail.getBrightness() > 0.95);
    }

    private boolean await(CountDownLatch latch) {
        try {
            return latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
