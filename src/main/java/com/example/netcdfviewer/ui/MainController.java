package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.AppMetadata;
import com.example.netcdfviewer.io.NetcdfDatasetParser;
import com.example.netcdfviewer.io.ParsedDataset;
import com.example.netcdfviewer.model.VariableInfo;
import com.example.netcdfviewer.render.ColorMap;
import com.example.netcdfviewer.render.ColorMaps;
import com.example.netcdfviewer.render.RangeStats;
import com.example.netcdfviewer.render.RenderMath;
import com.example.netcdfviewer.render.TriangleImageRenderer;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.ListCell;
import javafx.scene.control.SelectionMode;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class MainController {
    private final Stage stage;
    private final MainView view;
    private final NetcdfDatasetParser parser = new NetcdfDatasetParser();
    private final TriangleImageRenderer imageRenderer = new TriangleImageRenderer();
    private final ViewportState viewportState = new ViewportState();
    private final Map<String, ColorMap> colorMaps = new LinkedHashMap<>();
    private ParsedDataset currentDataset;
    private VariableInfo currentVariable;
    private Point2D dragAnchor;
    private Path lastDirectory = Paths.get(System.getProperty("user.home", "."));
    private long renderSequence;

    public MainController(Stage stage, MainView view) {
        this.stage = stage;
        this.view = view;
    }

    public void initialize() {
        colorMaps.put("Viridis", ColorMaps.viridis());
        colorMaps.put("Jet", ColorMaps.jet());
        colorMaps.put("Greys", ColorMaps.greys());

        view.getColorMapCombo().setItems(FXCollections.observableArrayList(colorMaps.keySet()));
        view.getColorMapCombo().getSelectionModel().select("Viridis");
        view.getOpenMenuItem().setAccelerator(KeyCombination.keyCombination("Shortcut+O"));
        view.getExportPngMenuItem().setAccelerator(KeyCombination.keyCombination("Shortcut+Shift+E"));
        view.getVariableList().getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        view.getVariableList().setCellFactory(list -> new VariableCell());
        view.getDepthSlider().setDisable(true);
        view.getExportButton().setDisable(true);
        view.getExportPngMenuItem().setDisable(true);
        view.getVisualizeButton().setDisable(true);
        view.getApplyRangeButton().setDisable(true);
        view.getMinField().setDisable(true);
        view.getMaxField().setDisable(true);
        setStatus("Ready to open NetCDF file.");

        bindCanvasSize();
        wireActions();
        wireMouseNavigation();
        wireDragAndDrop();
    }

    private void bindCanvasSize() {
        view.getCanvasHost().widthProperty().addListener((obs, oldValue, newValue) -> {
            double width = Math.max(1, newValue.doubleValue());
            view.getRenderCanvas().setWidth(width);
            renderCurrentSelection();
        });
        view.getCanvasHost().heightProperty().addListener((obs, oldValue, newValue) -> {
            double height = Math.max(1, newValue.doubleValue());
            view.getRenderCanvas().setHeight(height);
            renderCurrentSelection();
        });
    }

    private void wireActions() {
        view.getOpenMenuItem().setOnAction(event -> openWithFileChooser());
        view.getOpenButton().setOnAction(event -> openWithFileChooser());
        view.getExportPngMenuItem().setOnAction(event -> exportPng());
        view.getExportButton().setOnAction(event -> exportPng());
        view.getExitMenuItem().setOnAction(event -> stage.close());
        view.getAboutMenuItem().setOnAction(event -> showAboutDialog());
        view.getResetViewButton().setOnAction(event -> {
            viewportState.reset();
            renderCurrentSelection();
        });
        view.getVisualizeButton().setOnAction(event -> renderCurrentSelection());

        view.getVariableList().getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            currentVariable = newValue;
            updateVariableMeta();
            updateDepthControls();
            renderCurrentSelection();
        });

        view.getDepthSlider().valueProperty().addListener((obs, oldValue, newValue) -> {
            if (currentVariable != null && currentVariable.layered()) {
                renderCurrentSelection();
            }
        });

        view.getColorMapCombo().valueProperty().addListener((obs, oldValue, newValue) -> renderCurrentSelection());
        view.getAutoRangeCheck().selectedProperty().addListener((obs, oldValue, autoRange) -> {
            view.getApplyRangeButton().setDisable(autoRange);
            view.getMinField().setDisable(autoRange);
            view.getMaxField().setDisable(autoRange);
            renderCurrentSelection();
        });
        view.getApplyRangeButton().setOnAction(event -> renderCurrentSelection());
    }

    private void wireMouseNavigation() {
        view.getCanvasHost().setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                dragAnchor = new Point2D(event.getX(), event.getY());
            }
        });
        view.getCanvasHost().setOnMouseDragged(event -> {
            if (dragAnchor == null || currentDataset == null || !currentDataset.hasMesh()) {
                return;
            }
            Point2D currentPoint = new Point2D(event.getX(), event.getY());
            Point2D delta = currentPoint.subtract(dragAnchor);
            viewportState.pan(delta.getX(), delta.getY());
            dragAnchor = currentPoint;
            renderCurrentSelection();
        });
        view.getCanvasHost().setOnMouseReleased(event -> dragAnchor = null);
        view.getCanvasHost().setOnScroll(event -> {
            if (currentDataset == null || !currentDataset.hasMesh()) {
                return;
            }
            double factor = event.getDeltaY() >= 0 ? 1.12 : 1.0 / 1.12;
            viewportState.zoom(factor, event.getX(), event.getY());
            renderCurrentSelection();
            event.consume();
        });
    }

    private void wireDragAndDrop() {
        view.setOnDragOver(event -> {
            if (hasNetcdfFile(event.getDragboard())) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        view.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            if (hasNetcdfFile(dragboard)) {
                loadFile(dragboard.getFiles().get(0).toPath());
                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }
            event.consume();
        });
    }

    private boolean hasNetcdfFile(Dragboard dragboard) {
        return dragboard.hasFiles()
            && dragboard.getFiles().stream().anyMatch(file -> file.getName().toLowerCase(Locale.ROOT).endsWith(".nc"));
    }

    private void openWithFileChooser() {
        try {
            Path path = SwingFileDialogs.chooseOpenFile(lastDirectory);
            if (path != null) {
                loadFile(path);
            } else {
                setStatus("Open canceled.");
            }
        } catch (Exception exception) {
            showError("Open failed", "Could not open the file chooser: " + exception.getMessage());
        }
    }

    private void loadFile(Path path) {
        if (path == null) {
            return;
        }

        lastDirectory = path.toAbsolutePath().getParent();
        currentDataset = null;
        currentVariable = null;
        view.getVariableList().getItems().clear();
        view.getDatasetLabel().setText(path.getFileName().toString());
        view.getCoordinateVariableLabel().setText("Coordinates: -");
        view.getConnectivityVariableLabel().setText("Connectivity: -");
        view.getVariableMetaLabel().setText("Variable details: loading...");
        view.getSummaryArea().clear();
        view.getAttributesArea().clear();
        view.getWarningsArea().clear();
        view.getExportButton().setDisable(true);
        view.getExportPngMenuItem().setDisable(true);
        view.getVisualizeButton().setDisable(true);
        viewportState.reset();
        renderPlaceholder("Loading " + path.getFileName() + " ...");
        setStatus("Loading " + path.getFileName() + " ...");
        updateWindowTitle();

        Task<ParsedDataset> loadTask = new Task<>() {
            @Override
            protected ParsedDataset call() throws Exception {
                return parser.open(path);
            }
        };

        loadTask.setOnSucceeded(event -> applyLoadedDataset(path, loadTask.getValue()));
        loadTask.setOnFailed(event -> {
            Throwable error = loadTask.getException();
            showError("Open failed", "Could not parse " + path.getFileName() + ": " + (error == null ? "unknown error" : error.getMessage()));
            renderPlaceholder("Failed to load " + path.getFileName() + ".");
        });

        Thread worker = new Thread(loadTask, "netcdf-load-" + path.getFileName());
        worker.setDaemon(true);
        worker.start();
    }

    private void applyLoadedDataset(Path path, ParsedDataset dataset) {
        currentDataset = dataset;
        currentVariable = null;
        updateDatasetPanels(dataset);
        view.getVariableList().setItems(FXCollections.observableArrayList(dataset.variables()));
        VariableInfo preferredVariable = dataset.plottableVariables().stream().findFirst().orElse(null);
        if (preferredVariable != null) {
            view.getVariableList().getSelectionModel().select(preferredVariable);
        } else if (!dataset.variables().isEmpty()) {
            view.getVariableList().getSelectionModel().select(0);
        } else {
            updateVariableMeta();
            renderPlaceholder("The file contains no variables.");
        }
        updateWindowTitle();
        setStatus("Loaded " + path.getFileName());
    }

    private void openFile(Path path) {
        try {
            lastDirectory = path.toAbsolutePath().getParent();
            ParsedDataset dataset = parser.open(path);
            viewportState.reset();
            applyLoadedDataset(path, dataset);
        } catch (Exception exception) {
            showError("Open failed", "Could not parse " + path.getFileName() + ": " + exception.getMessage());
        }
    }

    private void updateDatasetPanels(ParsedDataset dataset) {
        view.getDatasetLabel().setText(dataset.sourcePath().getFileName().toString());
        view.getCoordinateVariableLabel().setText("Coordinates: "
            + Optional.ofNullable(dataset.xVariableName()).orElse("-")
            + " / "
            + Optional.ofNullable(dataset.yVariableName()).orElse("-"));
        view.getConnectivityVariableLabel().setText("Connectivity: "
            + Optional.ofNullable(dataset.connectivityVariableName()).orElse("-"));
        String summary = "File: " + dataset.sourcePath().toAbsolutePath() + System.lineSeparator()
            + "Coordinates: " + Optional.ofNullable(dataset.xVariableName()).orElse("-")
            + " / " + Optional.ofNullable(dataset.yVariableName()).orElse("-") + System.lineSeparator()
            + "Connectivity: " + Optional.ofNullable(dataset.connectivityVariableName()).orElse("-") + System.lineSeparator()
            + "Mesh: " + (dataset.hasMesh() ? dataset.mesh().nodeCount() + " nodes, " + dataset.mesh().triangleCount() + " triangles" : "Unavailable") + System.lineSeparator()
            + System.lineSeparator()
            + "Dimensions:" + System.lineSeparator()
            + dataset.dimensions().entrySet().stream()
            .map(entry -> "  - " + entry.getKey() + " = " + entry.getValue())
            .collect(Collectors.joining(System.lineSeparator()));

        view.getSummaryArea().setText(summary);
        view.getAttributesArea().setText(dataset.globalAttributes().isEmpty()
            ? "No global attributes."
            : dataset.globalAttributes().entrySet().stream()
            .map(entry -> entry.getKey() + " = " + entry.getValue())
            .collect(Collectors.joining(System.lineSeparator())));
        view.getWarningsArea().setText(dataset.warnings().isEmpty()
            ? "No warnings."
            : String.join(System.lineSeparator(), dataset.warnings()));
    }

    private void updateDepthControls() {
        if (currentVariable == null || !currentVariable.plottable() || !currentVariable.layered()) {
            view.getDepthSlider().setDisable(true);
            view.getDepthSlider().setValue(0);
            view.getLayerInfoLabel().setText("Layer: single");
            view.getVisualizeButton().setDisable(currentVariable == null || !currentVariable.plottable());
            return;
        }
        view.getDepthSlider().setDisable(false);
        view.getDepthSlider().setMin(0);
        view.getDepthSlider().setMax(Math.max(0, currentVariable.layerCount() - 1));
        view.getDepthSlider().setValue(Math.min(view.getDepthSlider().getValue(), currentVariable.layerCount() - 1));
        view.getVisualizeButton().setDisable(false);
    }

    private void updateVariableMeta() {
        if (currentVariable == null) {
            view.getVariableMetaLabel().setText("Variable details: -");
            return;
        }
        String mode = currentVariable.plottable()
            ? (currentVariable.layered() ? "Layered " : "Single-layer ")
                + (currentVariable.elementCentered() ? "element field" : "node field")
            : "Info only";
        view.getVariableMetaLabel().setText("Variable details: "
            + currentVariable.presentableType()
            + " "
            + currentVariable.dimensionSummary()
            + " | "
            + mode);
    }

    private void renderCurrentSelection() {
        Canvas canvas = view.getRenderCanvas();
        if (currentDataset == null) {
            renderPlaceholder("Open a NetCDF file to begin.");
            return;
        }
        if (!currentDataset.hasMesh()) {
            renderPlaceholder("This file opened successfully, but it does not contain a plottable triangle mesh.");
            return;
        }
        if (currentVariable == null) {
            renderPlaceholder("Select a variable from the list.");
            return;
        }
        if (!currentVariable.plottable()) {
            renderPlaceholder("Selected variable is informational only and cannot be rendered as a planar scalar field.");
            return;
        }

        try {
            int layerIndex = currentVariable.layered() ? (int) Math.round(view.getDepthSlider().getValue()) : 0;
            double[] values = parser.readLayer(currentDataset, currentVariable, layerIndex);
            RangeStats computedRange = RenderMath.computeRange(values, currentVariable.fillValue());
            if (computedRange.empty()) {
                renderPlaceholder("Selected layer contains no valid values.");
                return;
            }
            RangeStats displayRange = resolveRange(computedRange);
            ColorMap colorMap = colorMaps.getOrDefault(view.getColorMapCombo().getValue(), ColorMaps.viridis());
            viewportState.ensureFitted(currentDataset.mesh(), canvas.getWidth(), canvas.getHeight());
            long requestId = ++renderSequence;
            view.getOverlayLabel().setText("Rendering " + currentVariable.name() + " ...");
            view.getOverlayLabel().setVisible(true);
            setStatus("Rendering " + currentVariable.name() + " ...");
            renderAsync(requestId, layerIndex, values, colorMap, displayRange);
        } catch (Exception exception) {
            renderPlaceholder("Could not render the selected variable: " + exception.getMessage());
        }
    }

    private void renderAsync(long requestId, int layerIndex, double[] values, ColorMap colorMap, RangeStats displayRange) {
        Canvas canvas = view.getRenderCanvas();
        ParsedDataset dataset = currentDataset;
        VariableInfo variable = currentVariable;
        int width = Math.max(1, (int) Math.round(canvas.getWidth()));
        int height = Math.max(1, (int) Math.round(canvas.getHeight()));
        ViewportState.Snapshot snapshot = viewportState.snapshot();

        Task<WritableImage> renderTask = new Task<>() {
            @Override
            protected WritableImage call() {
                var bufferedImage = imageRenderer.render(
                    width,
                    height,
                    dataset.mesh(),
                    values,
                    colorMap,
                    displayRange,
                    snapshot,
                    variable.elementCentered(),
                    variable.fillValue()
                );
                return SwingFXUtils.toFXImage(bufferedImage, null);
            }
        };

        renderTask.setOnSucceeded(event -> {
            if (requestId != renderSequence || variable != currentVariable || dataset != currentDataset) {
                return;
            }
            canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            canvas.getGraphicsContext2D().drawImage(renderTask.getValue(), 0, 0, canvas.getWidth(), canvas.getHeight());
            view.getColorBarCanvas().render(colorMap, displayRange);
            view.getCurrentVariableLabel().setText("Variable: " + variable.name() + " " + variable.dimensionSummary());
            view.getRangeInfoLabel().setText("Range: " + format(displayRange.min()) + " to " + format(displayRange.max()));
            updateLayerLabel(layerIndex);
            updateWindowTitle();
            view.getOverlayLabel().setVisible(false);
            view.getExportButton().setDisable(false);
            view.getExportPngMenuItem().setDisable(false);
            setStatus("Rendered " + variable.name());
        });

        renderTask.setOnFailed(event -> {
            if (requestId != renderSequence) {
                return;
            }
            Throwable error = renderTask.getException();
            renderPlaceholder("Could not render the selected variable: " + (error == null ? "unknown error" : error.getMessage()));
        });

        Thread worker = new Thread(renderTask, "render-" + requestId);
        worker.setDaemon(true);
        worker.start();
    }

    private RangeStats resolveRange(RangeStats computedRange) {
        if (view.getAutoRangeCheck().isSelected()) {
            view.getMinField().setText(format(computedRange.min()));
            view.getMaxField().setText(format(computedRange.max()));
            return computedRange;
        }
        try {
            double manualMin = Double.parseDouble(view.getMinField().getText().trim());
            double manualMax = Double.parseDouble(view.getMaxField().getText().trim());
            if (manualMax <= manualMin) {
                throw new IllegalArgumentException("Manual max must be greater than manual min.");
            }
            return new RangeStats(manualMin, manualMax, computedRange.validCount());
        } catch (Exception exception) {
            view.getAutoRangeCheck().setSelected(true);
            setStatus("Invalid manual range. Reverting to automatic range.");
            return computedRange;
        }
    }

    private void updateLayerLabel(int layerIndex) {
        if (currentVariable == null || !currentVariable.layered()) {
            view.getLayerInfoLabel().setText("Layer: single");
            return;
        }
        String label = "Layer: " + (layerIndex + 1) + " / " + currentVariable.layerCount();
        double[] axisValues = currentDataset.axisValues(currentVariable.layerDimensionName()).orElse(null);
        if (axisValues != null && layerIndex < axisValues.length) {
            label += " (value=" + format(axisValues[layerIndex]) + ")";
        }
        view.getLayerInfoLabel().setText(label);
    }

    private void renderPlaceholder(String message) {
        view.getRenderCanvas().getGraphicsContext2D().clearRect(0, 0, view.getRenderCanvas().getWidth(), view.getRenderCanvas().getHeight());
        view.getColorBarCanvas().clear();
        view.getOverlayLabel().setText(message);
        view.getOverlayLabel().setVisible(true);
        view.getExportButton().setDisable(true);
        view.getExportPngMenuItem().setDisable(true);
        updateWindowTitle();
    }

    private void exportPng() {
        try {
            Path path = chooseSavePngFile();
            if (path == null) {
                setStatus("Export canceled.");
                return;
            }
            lastDirectory = path.toAbsolutePath().getParent();
            WritableImage image = view.getVisualizationBox().snapshot(new SnapshotParameters(), null);
            PngExportSupport.writePng(image, path);
            setStatus("Exported " + path.getFileName());
        } catch (IOException exception) {
            showError("Export failed", "Could not export PNG: " + exception.getMessage());
        } catch (Exception exception) {
            showError("Export failed", "Could not open the save dialog: " + exception.getMessage());
        }
    }

    private void setStatus(String text) {
        view.getStatusLabel().setText(text);
    }

    private void showError(String title, String message) {
        setStatus(message);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("About " + AppMetadata.APP_NAME);
        alert.setHeaderText(AppMetadata.APP_NAME + " " + AppMetadata.VERSION);
        alert.setContentText(aboutContent());
        alert.showAndWait();
    }

    static String aboutContent() {
        return AppMetadata.APP_NAME
            + System.lineSeparator()
            + AppMetadata.DESCRIPTION
            + System.lineSeparator()
            + System.lineSeparator()
            + "Version: " + AppMetadata.VERSION
            + System.lineSeparator()
            + "Author: " + AppMetadata.AUTHOR_NAME
            + System.lineSeparator()
            + "Email: " + AppMetadata.AUTHOR_EMAIL;
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private Path chooseSavePngFile() {
        return SwingFileDialogs.chooseSavePngFile(lastDirectory);
    }

    private void updateWindowTitle() {
        StringBuilder title = new StringBuilder("NetCDF Viewer");
        if (currentDataset != null) {
            title.append(" - ").append(currentDataset.sourcePath().getFileName());
        }
        if (currentVariable != null) {
            title.append(" - ").append(currentVariable.name());
            if (currentVariable.layered()) {
                int layerIndex = (int) Math.round(view.getDepthSlider().getValue());
                title.append(" [Layer ").append(layerIndex + 1).append("/").append(currentVariable.layerCount()).append("]");
            }
        }
        stage.setTitle(title.toString());
    }

    private static final class VariableCell extends ListCell<VariableInfo> {
        @Override
        protected void updateItem(VariableInfo item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                return;
            }
            String marker = item.plottable() ? (item.layered() ? "[Layered] " : "[Planar] ") : "[Info] ";
            setText(marker + item.name() + " : " + item.presentableType() + " " + item.dimensionSummary());
            if (!item.plottable()) {
                setStyle("-fx-text-fill: #6B7280;");
            } else {
                setStyle("");
            }
        }
    }
}
