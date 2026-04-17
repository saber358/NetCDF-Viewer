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
    void renderPaintsVisiblePixelsForFlowLineBody() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<Color> colorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Canvas canvas = new Canvas(120, 40);
                FlowLineGenerator.FlowLine line = FlowLineGenerator.FlowLine.fromPoints(List.of(
                    new FlowLineGenerator.FlowPoint(10.0, 20.0),
                    new FlowLineGenerator.FlowPoint(110.0, 20.0)
                ));

                renderer.render(canvas.getGraphicsContext2D(), List.of(line), 0.0);
                colorRef.set(canvas.snapshot(new SnapshotParameters(), null).getPixelReader().getColor(20, 20));
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
        assertTrue(colorRef.get().getOpacity() > 0.0);
    }

    @Test
    void renderMovesHighlightBandWhenPhaseChanges() {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<Color> earlyAtPhaseZeroRef = new AtomicReference<>();
        AtomicReference<Color> lateAtPhaseZeroRef = new AtomicReference<>();
        AtomicReference<Color> earlyAtPhaseLateRef = new AtomicReference<>();
        AtomicReference<Color> lateAtPhaseLateRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FlowLineGenerator.FlowLine line = FlowLineGenerator.FlowLine.fromPoints(List.of(
                    new FlowLineGenerator.FlowPoint(10.0, 20.0),
                    new FlowLineGenerator.FlowPoint(110.0, 20.0)
                ));

                Canvas phaseZeroCanvas = new Canvas(120, 40);
                renderer.render(phaseZeroCanvas.getGraphicsContext2D(), List.of(line), 0.0);
                earlyAtPhaseZeroRef.set(phaseZeroCanvas.snapshot(new SnapshotParameters(), null).getPixelReader().getColor(12, 20));
                lateAtPhaseZeroRef.set(phaseZeroCanvas.snapshot(new SnapshotParameters(), null).getPixelReader().getColor(90, 20));

                Canvas phaseLateCanvas = new Canvas(120, 40);
                renderer.render(phaseLateCanvas.getGraphicsContext2D(), List.of(line), 0.8);
                earlyAtPhaseLateRef.set(phaseLateCanvas.snapshot(new SnapshotParameters(), null).getPixelReader().getColor(12, 20));
                lateAtPhaseLateRef.set(phaseLateCanvas.snapshot(new SnapshotParameters(), null).getPixelReader().getColor(90, 20));
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

        Color earlyAtPhaseZero = earlyAtPhaseZeroRef.get();
        Color lateAtPhaseZero = lateAtPhaseZeroRef.get();
        Color earlyAtPhaseLate = earlyAtPhaseLateRef.get();
        Color lateAtPhaseLate = lateAtPhaseLateRef.get();

        assertNotEquals(earlyAtPhaseZero, earlyAtPhaseLate);
        assertNotEquals(lateAtPhaseZero, lateAtPhaseLate);
        assertTrue(earlyAtPhaseZero.getOpacity() >= lateAtPhaseZero.getOpacity());
        assertTrue(lateAtPhaseLate.getOpacity() >= earlyAtPhaseLate.getOpacity());
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
