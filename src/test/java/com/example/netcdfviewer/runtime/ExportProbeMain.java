package com.example.netcdfviewer.runtime;

import com.example.netcdfviewer.ui.MainController;
import com.example.netcdfviewer.ui.MainView;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ExportProbeMain {
    private ExportProbeMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected dataset path and output path.");
        }

        new JFXPanel();
        Platform.setImplicitExit(false);

        Path datasetPath = Path.of(args[0]).toAbsolutePath();
        File outputFile = Path.of(args[1]).toAbsolutePath().toFile();
        CountDownLatch initLatch = new CountDownLatch(1);
        CountDownLatch renderLatch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<MainView> viewRef = new AtomicReference<>();
        AtomicReference<Stage> stageRef = new AtomicReference<>();

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
                openFile.invoke(controller, datasetPath);

                viewRef.set(view);
                stageRef.set(stage);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                initLatch.countDown();
            }
        });

        if (!initLatch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX init timed out.");
        }
        if (errorRef.get() != null) {
            throw new RuntimeException(errorRef.get());
        }

        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadlineNanos) {
            CountDownLatch pollLatch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    MainView view = viewRef.get();
                    if (view != null && !view.getOverlayLabel().isVisible()) {
                        WritableImage image = view.getVisualizationBox().snapshot(new SnapshotParameters(), null);
                        boolean written = ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", outputFile);
                        if (!written) {
                            throw new IllegalStateException("No PNG writer available.");
                        }
                        if (ImageIO.read(outputFile) == null) {
                            throw new IllegalStateException("ImageIO could not read exported PNG.");
                        }
                        renderLatch.countDown();
                        stageRef.get().close();
                        Platform.exit();
                    }
                } catch (Throwable throwable) {
                    errorRef.set(throwable);
                    renderLatch.countDown();
                    Stage stage = stageRef.get();
                    if (stage != null) {
                        stage.close();
                    }
                    Platform.exit();
                } finally {
                    pollLatch.countDown();
                }
            });
            pollLatch.await(2, TimeUnit.SECONDS);
            if (renderLatch.getCount() == 0) {
                break;
            }
            Thread.sleep(100);
        }

        if (!renderLatch.await(1, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Export timed out.");
        }
        if (errorRef.get() != null) {
            throw new RuntimeException(errorRef.get());
        }

        System.out.println("png_bytes=" + outputFile.length());
    }
}
