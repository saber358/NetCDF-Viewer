package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.testsupport.SampleDatasetPaths;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MainViewLayoutStabilityTest {
    @BeforeAll
    static void initToolkit() {
        new JFXPanel();
        Platform.setImplicitExit(false);
    }

    @Test
    void centerCanvasAreaStabilizesAfterLoadingDataset() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<List<Double>> widthsRef = new AtomicReference<>(new ArrayList<>());
        AtomicReference<List<Double>> heightsRef = new AtomicReference<>(new ArrayList<>());

        Platform.runLater(() -> {
            try {
                Stage stage = new Stage();
                MainView view = new MainView();
                MainController controller = new MainController(stage, view);
                controller.initialize();
                stage.setScene(new Scene(view, 1440, 900));
                stage.show();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("ydw.nc"));

                Timeline timeline = new Timeline();
                for (int index = 0; index < 10; index++) {
                    timeline.getKeyFrames().add(new KeyFrame(Duration.millis(100L * (index + 1)), event -> {
                        widthsRef.get().add(view.getCanvasHost().getWidth());
                        heightsRef.get().add(view.getCanvasHost().getHeight());
                    }));
                }
                timeline.setOnFinished(event -> {
                    stage.close();
                    latch.countDown();
                });
                timeline.play();
            } catch (Throwable throwable) {
                errorRef.set(throwable);
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }

        double widthDrift = drift(widthsRef.get());
        double heightDrift = drift(heightsRef.get());

        assertTrue(widthDrift < 2.0, "Canvas host width kept changing: " + widthsRef.get());
        assertTrue(heightDrift < 2.0, "Canvas host height kept changing: " + heightsRef.get());
    }

    private double drift(List<Double> values) {
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        return max - min;
    }
}
