package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.AppMetadata;
import com.example.netcdfviewer.io.ParsedDataset;
import com.example.netcdfviewer.model.VariableInfo;
import com.example.netcdfviewer.testsupport.SampleDatasetPaths;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainControllerLoadFileTest {
    @BeforeAll
    static void initToolkit() {
        new JFXPanel();
        Platform.setImplicitExit(false);
    }

    @Test
    void controllerLoadsVerifiedSampleDataset() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("ydw.nc"));

                assertTrue(view.getDatasetLabel().getText().contains("ydw.nc"));
                assertFalse(view.getVariableList().getItems().isEmpty());
                assertTrue(view.getSummaryArea().getText().contains("Connectivity:"));
                assertTrue(view.getStatusLabel().getText().contains("Loaded"));
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void controllerLoadsStructuredGridDatasetAndCompletesRender() throws Exception {
        CountDownLatch initLatch = new CountDownLatch(1);
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
                openFile.invoke(controller, SampleDatasetPaths.resolve("XTPY-wrf.nc"));

                viewRef.set(view);
                stageRef.set(stage);
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

        waitForRender(viewRef.get());
        closeStage(stageRef.get());

        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
        assertTrue(viewRef.get().getDatasetLabel().getText().contains("XTPY-wrf.nc"));
        assertFalse(viewRef.get().getOverlayLabel().isVisible());
    }

    @Test
    void loadingStructuredDatasetPopulatesCoordinateControls() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("XTPY-roms.nc"));

                assertTrue(view.getCoordinateSelectionBox().isVisible());
                assertEquals("lon_rho", view.getCoordinateXCombo().getValue());
                assertEquals("lat_rho", view.getCoordinateYCombo().getValue());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void selectingStructuredVariableSwitchesToCompatibleCoordinateBinding() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("XTPY-roms.nc"));

                VariableInfo velocity = view.getVariableList().getItems().stream()
                    .filter(variable -> variable.name().equals("u"))
                    .findFirst()
                    .orElseThrow();
                view.getVariableList().getSelectionModel().select(velocity);

                assertEquals("lon_u", view.getCoordinateXCombo().getValue());
                assertEquals("lat_u", view.getCoordinateYCombo().getValue());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void clickQueryOnStructuredGridUpdatesStatusBar() throws Exception {
        CountDownLatch initLatch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<MainController> controllerRef = new AtomicReference<>();
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
                openFile.invoke(controller, SampleDatasetPaths.resolve("XTPY-wrf.nc"));

                controllerRef.set(controller);
                viewRef.set(view);
                stageRef.set(stage);
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

        waitForRender(viewRef.get());
        clickStructuredGridCenter(controllerRef.get(), viewRef.get(), errorRef);
        waitForStatus(viewRef.get(), text -> text.contains("Query") && text.contains("value="));
        closeStage(stageRef.get());

        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void aboutContentIncludesBrandingMetadata() {
        String content = MainController.aboutContent();

        assertTrue(content.contains(AppMetadata.APP_NAME));
        assertTrue(content.contains(AppMetadata.VERSION));
        assertTrue(content.contains(AppMetadata.AUTHOR_NAME));
        assertTrue(content.contains(AppMetadata.AUTHOR_EMAIL));
    }

    @Test
    void loadingAdditionalDatasetAddsSecondDatasetEntryAndAllowsSwitching() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("ydw.nc"));
                openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));

                assertEquals(2, view.getDatasetList().getItems().size());
                assertTrue(view.getDatasetLabel().getText().contains("HBHQY.nc"));

                view.getDatasetList().getSelectionModel().select(0);

                assertTrue(view.getDatasetLabel().getText().contains("ydw.nc"));
                assertFalse(view.getVariableList().getItems().isEmpty());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void removingActiveDatasetFallsBackToRemainingDataset() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("ydw.nc"));
                openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));

                view.getRemoveDatasetButton().fire();

                assertEquals(1, view.getDatasetList().getItems().size());
                assertTrue(view.getDatasetLabel().getText().contains("ydw.nc"));
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void reopeningSameDatasetDoesNotCreateDuplicateEntry() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("ydw.nc"));
                openFile.invoke(controller, SampleDatasetPaths.resolve("ydw.nc"));

                assertEquals(1, view.getDatasetList().getItems().size());
                assertTrue(view.getStatusLabel().getText().contains("already loaded"));
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void loadingCoastlineOverlayStoresOverlayState() throws Exception {
        Path overlayFile = createGeoJsonOverlayFile();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method loadCoastline = MainController.class.getDeclaredMethod("loadCoastline", Path.class);
                loadCoastline.setAccessible(true);
                loadCoastline.invoke(controller, overlayFile);

                Field overlayField = MainController.class.getDeclaredField("currentOverlay");
                overlayField.setAccessible(true);
                assertTrue(overlayField.get(controller) != null);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        Files.deleteIfExists(overlayFile);
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void switchingDatasetsPreservesCoastlineOverlayState() throws Exception {
        Path overlayFile = createGeoJsonOverlayFile();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("ydw.nc"));

                Method loadCoastline = MainController.class.getDeclaredMethod("loadCoastline", Path.class);
                loadCoastline.setAccessible(true);
                loadCoastline.invoke(controller, overlayFile);

                openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));

                Field overlayField = MainController.class.getDeclaredField("currentOverlay");
                overlayField.setAccessible(true);
                assertTrue(overlayField.get(controller) != null);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        Files.deleteIfExists(overlayFile);
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void clearingCoastlineOverlayResetsOverlayState() throws Exception {
        Path overlayFile = createGeoJsonOverlayFile();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method loadCoastline = MainController.class.getDeclaredMethod("loadCoastline", Path.class);
                loadCoastline.setAccessible(true);
                loadCoastline.invoke(controller, overlayFile);

                Method clearCoastlineOverlay = MainController.class.getDeclaredMethod("clearCoastlineOverlay");
                clearCoastlineOverlay.setAccessible(true);
                clearCoastlineOverlay.invoke(controller);

                Field overlayField = MainController.class.getDeclaredField("currentOverlay");
                overlayField.setAccessible(true);
                assertEquals(null, overlayField.get(controller));
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        Files.deleteIfExists(overlayFile);
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void controllerInitializesWithBuiltInCoastlineOverlay() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Field overlayField = MainController.class.getDeclaredField("currentOverlay");
                overlayField.setAccessible(true);
                assertTrue(overlayField.get(controller) != null);
                assertFalse(view.getClearCoastlineMenuItem().isDisable());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void useBuiltInCoastlineRestoresOverlayAfterClear() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method clearCoastlineOverlay = MainController.class.getDeclaredMethod("clearCoastlineOverlay");
                clearCoastlineOverlay.setAccessible(true);
                clearCoastlineOverlay.invoke(controller);

                Method useBuiltInCoastline = MainController.class.getDeclaredMethod("useBuiltInCoastline");
                useBuiltInCoastline.setAccessible(true);
                useBuiltInCoastline.invoke(controller);

                Field overlayField = MainController.class.getDeclaredField("currentOverlay");
                overlayField.setAccessible(true);
                assertTrue(overlayField.get(controller) != null);
                assertFalse(view.getClearCoastlineMenuItem().isDisable());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void clickQueryUpdatesStatusBarAfterDatasetRender() throws Exception {
        CountDownLatch initLatch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<MainController> controllerRef = new AtomicReference<>();
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
                openFile.invoke(controller, SampleDatasetPaths.resolve("ydw.nc"));

                controllerRef.set(controller);
                viewRef.set(view);
                stageRef.set(stage);
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

        waitForRender(viewRef.get());
        clickFirstTriangleCentroid(controllerRef.get(), viewRef.get(), errorRef);
        waitForStatus(viewRef.get(), text -> text.contains("Query") && text.contains("triangle #"));
        closeStage(stageRef.get());

        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void clickQueryOutsideMeshShowsMissMessage() throws Exception {
        CountDownLatch initLatch = new CountDownLatch(1);
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
                openFile.invoke(controller, SampleDatasetPaths.resolve("ydw.nc"));

                viewRef.set(view);
                stageRef.set(stage);
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

        waitForRender(viewRef.get());
        clickAt(viewRef.get(), -50.0, -50.0, errorRef);
        waitForStatus(viewRef.get(), text -> text.equals("No mesh value at clicked location."));
        closeStage(stageRef.get());

        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void dragPanDoesNotTriggerPointQueryStatus() throws Exception {
        CountDownLatch initLatch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<MainView> viewRef = new AtomicReference<>();
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<String> statusRef = new AtomicReference<>("");

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

                viewRef.set(view);
                stageRef.set(stage);
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

        waitForRender(viewRef.get());
        dragAcross(viewRef.get(), 200.0, 200.0, 260.0, 260.0, errorRef);
        waitForStatus(viewRef.get(), text -> text.startsWith("Rendered "));
        readStatus(viewRef.get(), statusRef);
        closeStage(stageRef.get());

        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
        assertFalse(statusRef.get().contains("Query"), "Drag should not trigger point query: " + statusRef.get());
    }

    @Test
    void loadingWaveDatasetEnablesWaveArrowToggle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));

                assertFalse(view.getWaveArrowCheck().isDisable());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void loadingDatasetWithoutWavePairDisablesWaveArrowToggle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));
                openFile.invoke(controller, SampleDatasetPaths.resolve("ydw.nc"));

                assertTrue(view.getWaveArrowCheck().isDisable());
                assertFalse(view.getWaveArrowCheck().isSelected());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void enablingWaveArrowOverlayKeepsRenderPipelineAlive() throws Exception {
        CountDownLatch initLatch = new CountDownLatch(1);
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
                openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));

                viewRef.set(view);
                stageRef.set(stage);
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

        waitForRender(viewRef.get());

        CountDownLatch toggleLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                viewRef.get().getWaveArrowCheck().setSelected(true);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                toggleLatch.countDown();
            }
        });
        assertTrue(toggleLatch.await(5, TimeUnit.SECONDS));

        waitForRender(viewRef.get());
        closeStage(stageRef.get());

        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
        assertTrue(viewRef.get().getWaveArrowCheck().isSelected());
        assertFalse(viewRef.get().getOverlayLabel().isVisible());
    }

    @Test
    void loadingStructuredWaveDatasetEnablesWaveArrowToggle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("XTPY-roms.nc"));

                assertFalse(view.getWaveArrowCheck().isDisable());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void loadingVelocityDatasetEnablesFlowLineToggle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));

                assertFalse(view.getFlowLineCheck().isDisable());
                assertFalse(view.getFlowLineCheck().isSelected());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void loadingTriangleWindDatasetEnablesWindBarbToggle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));

                assertFalse(view.getWindBarbCheck().isDisable());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void loadingStructuredWindDatasetEnablesWindBarbToggle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("XTPY-wrf.nc"));

                assertFalse(view.getWindBarbCheck().isDisable());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void enablingWindBarbOverlayBuildsTriangleWindOverlayFrame() throws Exception {
        CountDownLatch initLatch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<MainController> controllerRef = new AtomicReference<>();
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
                openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));

                controllerRef.set(controller);
                viewRef.set(view);
                stageRef.set(stage);
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

        waitForRender(viewRef.get());

        CountDownLatch toggleLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                viewRef.get().getWindBarbCheck().setSelected(true);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                toggleLatch.countDown();
            }
        });
        assertTrue(toggleLatch.await(5, TimeUnit.SECONDS));

        waitForControllerFieldNotNull(controllerRef.get(), "latestWindOverlayFrame");
        closeStage(stageRef.get());

        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void enablingWindBarbOverlayBuildsStructuredWindOverlayFrame() throws Exception {
        CountDownLatch initLatch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<MainController> controllerRef = new AtomicReference<>();
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
                openFile.invoke(controller, SampleDatasetPaths.resolve("XTPY-wrf.nc"));

                controllerRef.set(controller);
                viewRef.set(view);
                stageRef.set(stage);
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

        waitForRender(viewRef.get());

        CountDownLatch toggleLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                viewRef.get().getWindBarbCheck().setSelected(true);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                toggleLatch.countDown();
            }
        });
        assertTrue(toggleLatch.await(5, TimeUnit.SECONDS));

        waitForControllerFieldNotNull(controllerRef.get(), "latestWindOverlayFrame");
        closeStage(stageRef.get());

        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void loadingDatasetWithoutVelocityPairDisablesFlowLineToggle() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));
                openFile.invoke(controller, SampleDatasetPaths.resolve("DSD1211.nc"));

                assertTrue(view.getFlowLineCheck().isDisable());
                assertFalse(view.getFlowLineCheck().isSelected());
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void enablingFlowLineOverlayKeepsRenderPipelineAlive() throws Exception {
        CountDownLatch initLatch = new CountDownLatch(1);
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
                openFile.invoke(controller, SampleDatasetPaths.resolve("HBHQY.nc"));

                viewRef.set(view);
                stageRef.set(stage);
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

        waitForRender(viewRef.get());

        CountDownLatch toggleLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                viewRef.get().getFlowLineCheck().setSelected(true);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                toggleLatch.countDown();
            }
        });
        assertTrue(toggleLatch.await(5, TimeUnit.SECONDS));

        waitForRender(viewRef.get());
        closeStage(stageRef.get());

        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
        assertTrue(viewRef.get().getFlowLineCheck().isSelected());
        assertFalse(viewRef.get().getOverlayLabel().isVisible());
    }

    @Test
    void enablingFlowLineOverlayOnStructuredDatasetKeepsRenderPipelineAlive() throws Exception {
        CountDownLatch initLatch = new CountDownLatch(1);
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
                openFile.invoke(controller, SampleDatasetPaths.resolve("XTPY-roms.nc"));

                viewRef.set(view);
                stageRef.set(stage);
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

        waitForRender(viewRef.get());

        CountDownLatch toggleLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                viewRef.get().getFlowLineCheck().setSelected(true);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                toggleLatch.countDown();
            }
        });
        assertTrue(toggleLatch.await(5, TimeUnit.SECONDS));

        waitForRender(viewRef.get());
        closeStage(stageRef.get());

        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
        assertTrue(viewRef.get().getFlowLineCheck().isSelected());
        assertFalse(viewRef.get().getOverlayLabel().isVisible());
    }

    private static void waitForRender(MainView view) throws Exception {
        AtomicBoolean ready = new AtomicBoolean(false);
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadlineNanos) {
            CountDownLatch pollLatch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    ready.set(!view.getOverlayLabel().isVisible()
                        && !view.getCurrentVariableLabel().getText().equals("Variable: -"));
                } finally {
                    pollLatch.countDown();
                }
            });
            assertTrue(pollLatch.await(2, TimeUnit.SECONDS), "FX thread did not respond while waiting for render.");
            if (ready.get()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for initial render.");
    }

    private static void waitForStatus(MainView view, StatusPredicate predicate) throws Exception {
        AtomicReference<String> statusRef = new AtomicReference<>("");
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadlineNanos) {
            CountDownLatch pollLatch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    statusRef.set(view.getStatusLabel().getText());
                } finally {
                    pollLatch.countDown();
                }
            });
            assertTrue(pollLatch.await(2, TimeUnit.SECONDS), "FX thread did not respond while waiting for status.");
            if (predicate.matches(statusRef.get())) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for status update. Last status: " + statusRef.get());
    }

    private static void waitForControllerFieldNotNull(MainController controller, String fieldName) throws Exception {
        AtomicReference<Object> valueRef = new AtomicReference<>();
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadlineNanos) {
            CountDownLatch pollLatch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    Field field = MainController.class.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    valueRef.set(field.get(controller));
                } catch (Throwable throwable) {
                    valueRef.set(throwable);
                } finally {
                    pollLatch.countDown();
                }
            });
            assertTrue(pollLatch.await(2, TimeUnit.SECONDS), "FX thread did not respond while waiting for controller field.");
            if (valueRef.get() instanceof Throwable throwable) {
                throw new AssertionError(throwable);
            }
            if (valueRef.get() != null) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for controller field: " + fieldName);
    }

    private static void readStatus(MainView view, AtomicReference<String> statusRef) throws Exception {
        CountDownLatch pollLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                statusRef.set(view.getStatusLabel().getText());
            } finally {
                pollLatch.countDown();
            }
        });
        assertTrue(pollLatch.await(2, TimeUnit.SECONDS), "FX thread did not respond while reading status.");
    }

    private static void clickFirstTriangleCentroid(MainController controller, MainView view, AtomicReference<Throwable> errorRef) throws Exception {
        CountDownLatch clickLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Field datasetField = MainController.class.getDeclaredField("currentDataset");
                datasetField.setAccessible(true);
                ParsedDataset dataset = (ParsedDataset) datasetField.get(controller);

                Field viewportField = MainController.class.getDeclaredField("viewportState");
                viewportField.setAccessible(true);
                ViewportState viewportState = (ViewportState) viewportField.get(controller);

                int[] triangle = dataset.mesh().triangles()[0];
                double centroidX = (dataset.mesh().x()[triangle[0]] + dataset.mesh().x()[triangle[1]] + dataset.mesh().x()[triangle[2]]) / 3.0;
                double centroidY = (dataset.mesh().y()[triangle[0]] + dataset.mesh().y()[triangle[1]] + dataset.mesh().y()[triangle[2]]) / 3.0;
                clickAt(view, viewportState.screenX(centroidX), viewportState.screenY(centroidY), errorRef);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                clickLatch.countDown();
            }
        });
        assertTrue(clickLatch.await(5, TimeUnit.SECONDS));
    }

    private static void clickStructuredGridCenter(MainController controller, MainView view, AtomicReference<Throwable> errorRef) throws Exception {
        CountDownLatch clickLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Field domainField = MainController.class.getDeclaredField("activeSpatialDomain");
                domainField.setAccessible(true);
                Object domainObject = domainField.get(controller);

                Field viewportField = MainController.class.getDeclaredField("viewportState");
                viewportField.setAccessible(true);
                ViewportState viewportState = (ViewportState) viewportField.get(controller);

                Method minX = domainObject.getClass().getMethod("minX");
                Method maxX = domainObject.getClass().getMethod("maxX");
                Method minY = domainObject.getClass().getMethod("minY");
                Method maxY = domainObject.getClass().getMethod("maxY");

                double worldX = (((double) minX.invoke(domainObject)) + ((double) maxX.invoke(domainObject))) * 0.5;
                double worldY = (((double) minY.invoke(domainObject)) + ((double) maxY.invoke(domainObject))) * 0.5;
                clickAt(view, viewportState.screenX(worldX), viewportState.screenY(worldY), errorRef);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                clickLatch.countDown();
            }
        });
        assertTrue(clickLatch.await(5, TimeUnit.SECONDS));
    }

    private static void clickAt(MainView view, double clickX, double clickY, AtomicReference<Throwable> errorRef) throws Exception {
        if (Platform.isFxApplicationThread()) {
            emitClick(view, clickX, clickY, errorRef);
            return;
        }

        CountDownLatch clickLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                emitClick(view, clickX, clickY, errorRef);
            } finally {
                clickLatch.countDown();
            }
        });
        assertTrue(clickLatch.await(5, TimeUnit.SECONDS), "FX thread did not respond while dispatching click.");
    }

    private static void dragAcross(MainView view, double startX, double startY, double endX, double endY, AtomicReference<Throwable> errorRef) throws Exception {
        CountDownLatch dragLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                view.getCanvasHost().getOnMousePressed().handle(new MouseEvent(
                    MouseEvent.MOUSE_PRESSED, startX, startY, startX, startY, MouseButton.PRIMARY,
                    1, false, false, false, false, true, false, false, true, false, false, null
                ));
                view.getCanvasHost().getOnMouseDragged().handle(new MouseEvent(
                    MouseEvent.MOUSE_DRAGGED, endX, endY, endX, endY, MouseButton.PRIMARY,
                    1, false, false, false, false, true, false, false, false, false, false, null
                ));
                view.getCanvasHost().getOnMouseReleased().handle(new MouseEvent(
                    MouseEvent.MOUSE_RELEASED, endX, endY, endX, endY, MouseButton.PRIMARY,
                    1, false, false, false, false, true, false, false, false, false, false, null
                ));
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                dragLatch.countDown();
            }
        });
        assertTrue(dragLatch.await(5, TimeUnit.SECONDS), "FX thread did not respond while dispatching drag.");
    }

    private static void emitClick(MainView view, double clickX, double clickY, AtomicReference<Throwable> errorRef) {
        try {
            view.getCanvasHost().getOnMousePressed().handle(new MouseEvent(
                MouseEvent.MOUSE_PRESSED, clickX, clickY, clickX, clickY, MouseButton.PRIMARY,
                1, false, false, false, false, true, false, false, true, false, false, null
            ));
            view.getCanvasHost().getOnMouseReleased().handle(new MouseEvent(
                MouseEvent.MOUSE_RELEASED, clickX, clickY, clickX, clickY, MouseButton.PRIMARY,
                1, false, false, false, false, true, false, false, true, false, false, null
            ));
        } catch (Throwable throwable) {
            errorRef.set(throwable);
        }
    }

    private static void closeStage(Stage stage) throws Exception {
        if (stage == null) {
            return;
        }
        CountDownLatch closeLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                stage.close();
            } finally {
                closeLatch.countDown();
            }
        });
        assertTrue(closeLatch.await(5, TimeUnit.SECONDS));
    }

    @FunctionalInterface
    private interface StatusPredicate {
        boolean matches(String text);
    }

    private static Path createGeoJsonOverlayFile() throws Exception {
        Path file = Files.createTempFile("coastline-overlay-", ".geojson");
        Files.writeString(file, """
            {
              "type": "LineString",
              "coordinates": [[120.0, 30.0], [121.0, 31.0]]
            }
            """);
        return file;
    }
}
