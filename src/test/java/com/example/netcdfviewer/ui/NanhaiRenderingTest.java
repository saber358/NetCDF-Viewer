package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.model.VariableInfo;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NanhaiRenderingTest {
    @BeforeAll
    static void initToolkit() {
        new JFXPanel();
        Platform.setImplicitExit(false);
    }

    @Test
    void nanhaiRendersNodeAndElementVariables() throws Exception {
        CountDownLatch initLatch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<MainView> viewRef = new AtomicReference<>();
        AtomicReference<VariableInfo> uaRef = new AtomicReference<>();

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
                openFile.invoke(controller, Path.of("nanhai.nc"));

                VariableInfo temp = view.getVariableList().getItems().stream()
                    .filter(variable -> variable.name().equals("temp"))
                    .findFirst()
                    .orElseThrow();
                VariableInfo ua = view.getVariableList().getItems().stream()
                    .filter(variable -> variable.name().equals("ua"))
                    .findFirst()
                    .orElseThrow();

                view.getVariableList().getSelectionModel().select(temp);
                stageRef.set(stage);
                viewRef.set(view);
                uaRef.set(ua);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                initLatch.countDown();
            }
        });

        assertTrue(initLatch.await(10, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }

        AtomicBoolean uaSelected = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicReference<String> lastLabel = new AtomicReference<>("");
        AtomicReference<Boolean> overlayVisible = new AtomicReference<>(true);
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);

        while (System.nanoTime() < deadlineNanos && !completed.get()) {
            CountDownLatch pollLatch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    MainView view = viewRef.get();
                    if (view == null) {
                        return;
                    }

                    overlayVisible.set(view.getOverlayLabel().isVisible());
                    lastLabel.set(view.getCurrentVariableLabel().getText());

                    if (!uaSelected.get()) {
                        if (!overlayVisible.get()) {
                            assertTrue(lastLabel.get().contains("temp"));
                            uaSelected.set(true);
                            view.getVariableList().getSelectionModel().select(uaRef.get());
                        }
                        return;
                    }

                    if (!overlayVisible.get() && lastLabel.get().contains("ua")) {
                        assertFalse(view.getOverlayLabel().isVisible(), "ua should finish rendering.");
                        completed.set(true);
                        stageRef.get().close();
                    }
                } catch (Throwable throwable) {
                    errorRef.set(throwable);
                } finally {
                    pollLatch.countDown();
                }
            });

            assertTrue(pollLatch.await(2, TimeUnit.SECONDS), "FX thread did not respond while polling render state.");
            if (errorRef.get() != null || completed.get()) {
                break;
            }
            Thread.sleep(100);
        }

        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
        assertTrue(completed.get(), "Timed out waiting for nanhai render. overlayVisible=" + overlayVisible.get() + ", label=" + lastLabel.get());
    }
}
