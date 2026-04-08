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

/**
 * 主界面控制器。
 * 负责把界面、解析器、渲染器和导出逻辑串联起来。
 */
public final class MainController {
    // 当前窗口对象。
    private final Stage stage;
    // 主界面视图对象。
    private final MainView view;
    // NetCDF 数据解析器。
    private final NetcdfDatasetParser parser = new NetcdfDatasetParser();
    // 后台离屏渲染器。
    private final TriangleImageRenderer imageRenderer = new TriangleImageRenderer();
    // 当前视口状态，包含缩放和平移信息。
    private final ViewportState viewportState = new ViewportState();
    // 可用颜色表集合。
    private final Map<String, ColorMap> colorMaps = new LinkedHashMap<>();
    // 当前已打开的数据集。
    private ParsedDataset currentDataset;
    // 当前选中的变量。
    private VariableInfo currentVariable;
    // 鼠标拖拽起点。
    private Point2D dragAnchor;
    // 最近一次打开或导出的目录。
    private Path lastDirectory = Paths.get(System.getProperty("user.home", "."));
    // 渲染序号，用于丢弃旧的后台渲染结果。
    private long renderSequence;

    public MainController(Stage stage, MainView view) {
        // 保存窗口和视图引用，供后续初始化和事件处理使用。
        this.stage = stage;
        this.view = view;
    }

    public void initialize() {
        // 注册内置颜色表。
        colorMaps.put("Viridis", ColorMaps.viridis());
        colorMaps.put("Jet", ColorMaps.jet());
        colorMaps.put("Greys", ColorMaps.greys());

        // 初始化颜色表下拉框。
        view.getColorMapCombo().setItems(FXCollections.observableArrayList(colorMaps.keySet()));
        view.getColorMapCombo().getSelectionModel().select("Viridis");
        // 设置常用快捷键。
        view.getOpenMenuItem().setAccelerator(KeyCombination.keyCombination("Shortcut+O"));
        view.getExportPngMenuItem().setAccelerator(KeyCombination.keyCombination("Shortcut+Shift+E"));
        // 变量列表只允许单选。
        view.getVariableList().getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        // 使用自定义单元格样式显示变量信息。
        view.getVariableList().setCellFactory(list -> new VariableCell());
        // 初始化时先禁用没有意义的控件。
        view.getDepthSlider().setDisable(true);
        view.getExportButton().setDisable(true);
        view.getExportPngMenuItem().setDisable(true);
        view.getVisualizeButton().setDisable(true);
        view.getApplyRangeButton().setDisable(true);
        view.getMinField().setDisable(true);
        view.getMaxField().setDisable(true);
        // 设置初始状态提示。
        setStatus("Ready to open NetCDF file.");

        // 绑定画布尺寸变化监听。
        bindCanvasSize();
        // 绑定菜单、按钮和选项控件。
        wireActions();
        // 绑定鼠标平移和缩放事件。
        wireMouseNavigation();
        // 绑定拖拽打开文件事件。
        wireDragAndDrop();
    }

    private void bindCanvasSize() {
        // 宿主容器宽度变化时，同步更新画布尺寸并重绘。
        view.getCanvasHost().widthProperty().addListener((obs, oldValue, newValue) -> {
            double width = Math.max(1, newValue.doubleValue());
            view.getRenderCanvas().setWidth(width);
            renderCurrentSelection();
        });
        // 宿主容器高度变化时，同步更新画布尺寸并重绘。
        view.getCanvasHost().heightProperty().addListener((obs, oldValue, newValue) -> {
            double height = Math.max(1, newValue.doubleValue());
            view.getRenderCanvas().setHeight(height);
            renderCurrentSelection();
        });
    }

    private void wireActions() {
        // 打开文件菜单与按钮共用同一处理逻辑。
        view.getOpenMenuItem().setOnAction(event -> openWithFileChooser());
        view.getOpenButton().setOnAction(event -> openWithFileChooser());
        // 导出 PNG 菜单与按钮共用同一处理逻辑。
        view.getExportPngMenuItem().setOnAction(event -> exportPng());
        view.getExportButton().setOnAction(event -> exportPng());
        // 退出菜单直接关闭窗口。
        view.getExitMenuItem().setOnAction(event -> stage.close());
        // 关于菜单弹出项目信息。
        view.getAboutMenuItem().setOnAction(event -> showAboutDialog());
        // 重置视图时恢复自动适配状态。
        view.getResetViewButton().setOnAction(event -> {
            viewportState.reset();
            renderCurrentSelection();
        });
        // 手动点击可视化按钮时重新渲染当前变量。
        view.getVisualizeButton().setOnAction(event -> renderCurrentSelection());

        // 变量选择变化时，刷新变量元信息、层控制和渲染结果。
        view.getVariableList().getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            currentVariable = newValue;
            updateVariableMeta();
            updateDepthControls();
            renderCurrentSelection();
        });

        // 当前变量支持分层时，层滑块变化会触发实时重绘。
        view.getDepthSlider().valueProperty().addListener((obs, oldValue, newValue) -> {
            if (currentVariable != null && currentVariable.layered()) {
                renderCurrentSelection();
            }
        });

        // 切换颜色表时立即重绘。
        view.getColorMapCombo().valueProperty().addListener((obs, oldValue, newValue) -> renderCurrentSelection());
        // 自动范围选项变化时，同步更新控件可用性并重绘。
        view.getAutoRangeCheck().selectedProperty().addListener((obs, oldValue, autoRange) -> {
            view.getApplyRangeButton().setDisable(autoRange);
            view.getMinField().setDisable(autoRange);
            view.getMaxField().setDisable(autoRange);
            renderCurrentSelection();
        });
        // 手动应用范围时重新渲染。
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
            // 弹出文件选择器，让用户选择 NetCDF 文件。
            Path path = SwingFileDialogs.chooseOpenFile(lastDirectory);
            if (path != null) {
                loadFile(path);
            } else {
                // 用户取消时仅更新状态栏。
                setStatus("Open canceled.");
            }
        } catch (Exception exception) {
            // 文件选择器异常时提示用户。
            showError("Open failed", "Could not open the file chooser: " + exception.getMessage());
        }
    }

    private void loadFile(Path path) {
        // 空路径直接忽略。
        if (path == null) {
            return;
        }

        // 记录最近目录，便于下次打开或导出时默认定位。
        lastDirectory = path.toAbsolutePath().getParent();
        // 清空当前数据状态，准备装载新文件。
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
        // 新文件加载前先重置视口。
        viewportState.reset();
        // 显示加载中的占位信息。
        renderPlaceholder("Loading " + path.getFileName() + " ...");
        setStatus("Loading " + path.getFileName() + " ...");
        updateWindowTitle();

        // 使用后台任务读取文件，避免卡住界面线程。
        Task<ParsedDataset> loadTask = new Task<>() {
            @Override
            protected ParsedDataset call() throws Exception {
                return parser.open(path);
            }
        };

        // 读取成功后刷新界面。
        loadTask.setOnSucceeded(event -> applyLoadedDataset(path, loadTask.getValue()));
        // 读取失败时显示错误并回到占位状态。
        loadTask.setOnFailed(event -> {
            Throwable error = loadTask.getException();
            showError("Open failed", "Could not parse " + path.getFileName() + ": " + (error == null ? "unknown error" : error.getMessage()));
            renderPlaceholder("Failed to load " + path.getFileName() + ".");
        });

        // 启动后台读取线程。
        Thread worker = new Thread(loadTask, "netcdf-load-" + path.getFileName());
        worker.setDaemon(true);
        worker.start();
    }

    private void applyLoadedDataset(Path path, ParsedDataset dataset) {
        // 保存当前数据集引用。
        currentDataset = dataset;
        currentVariable = null;
        // 更新摘要、属性和警告面板。
        updateDatasetPanels(dataset);
        // 将所有变量加载到变量列表中。
        view.getVariableList().setItems(FXCollections.observableArrayList(dataset.variables()));
        // 优先选中第一个可绘制变量，提升首次打开体验。
        VariableInfo preferredVariable = dataset.plottableVariables().stream().findFirst().orElse(null);
        if (preferredVariable != null) {
            view.getVariableList().getSelectionModel().select(preferredVariable);
        } else if (!dataset.variables().isEmpty()) {
            // 没有可绘制变量时，退而选中第一个变量供查看信息。
            view.getVariableList().getSelectionModel().select(0);
        } else {
            // 文件中没有任何变量时显示明确提示。
            updateVariableMeta();
            renderPlaceholder("The file contains no variables.");
        }
        // 更新标题与状态栏。
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
        // 更新当前数据集名称标签。
        view.getDatasetLabel().setText(dataset.sourcePath().getFileName().toString());
        // 更新坐标变量标签。
        view.getCoordinateVariableLabel().setText("Coordinates: "
            + Optional.ofNullable(dataset.xVariableName()).orElse("-")
            + " / "
            + Optional.ofNullable(dataset.yVariableName()).orElse("-"));
        // 更新连接变量标签。
        view.getConnectivityVariableLabel().setText("Connectivity: "
            + Optional.ofNullable(dataset.connectivityVariableName()).orElse("-"));
        // 拼接摘要信息文本。
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

        // 更新摘要区域。
        view.getSummaryArea().setText(summary);
        // 更新全局属性区域。
        view.getAttributesArea().setText(dataset.globalAttributes().isEmpty()
            ? "No global attributes."
            : dataset.globalAttributes().entrySet().stream()
            .map(entry -> entry.getKey() + " = " + entry.getValue())
            .collect(Collectors.joining(System.lineSeparator())));
        // 更新警告区域。
        view.getWarningsArea().setText(dataset.warnings().isEmpty()
            ? "No warnings."
            : String.join(System.lineSeparator(), dataset.warnings()));
    }

    private void updateDepthControls() {
        // 变量为空、不可绘制或不分层时，关闭层切换控件。
        if (currentVariable == null || !currentVariable.plottable() || !currentVariable.layered()) {
            view.getDepthSlider().setDisable(true);
            view.getDepthSlider().setValue(0);
            view.getLayerInfoLabel().setText("Layer: single");
            view.getVisualizeButton().setDisable(currentVariable == null || !currentVariable.plottable());
            return;
        }
        // 对分层变量启用滑块并设置合法范围。
        view.getDepthSlider().setDisable(false);
        view.getDepthSlider().setMin(0);
        view.getDepthSlider().setMax(Math.max(0, currentVariable.layerCount() - 1));
        view.getDepthSlider().setValue(Math.min(view.getDepthSlider().getValue(), currentVariable.layerCount() - 1));
        view.getVisualizeButton().setDisable(false);
    }

    private void updateVariableMeta() {
        // 没有变量时显示占位提示。
        if (currentVariable == null) {
            view.getVariableMetaLabel().setText("Variable details: -");
            return;
        }
        // 根据变量是否可绘制、是否分层以及空间位置生成说明文本。
        String mode = currentVariable.plottable()
            ? (currentVariable.layered() ? "Layered " : "Single-layer ")
                + (currentVariable.elementCentered() ? "element field" : "node field")
            : "Info only";
        // 更新变量细节标签。
        view.getVariableMetaLabel().setText("Variable details: "
            + currentVariable.presentableType()
            + " "
            + currentVariable.dimensionSummary()
            + " | "
            + mode);
    }

    private void renderCurrentSelection() {
        // 获取主画布对象。
        Canvas canvas = view.getRenderCanvas();
        // 尚未打开文件时显示默认提示。
        if (currentDataset == null) {
            renderPlaceholder("Open a NetCDF file to begin.");
            return;
        }
        // 文件没有可用三角网时仅展示提示，不执行绘制。
        if (!currentDataset.hasMesh()) {
            renderPlaceholder("This file opened successfully, but it does not contain a plottable triangle mesh.");
            return;
        }
        // 未选择变量时提示用户先选择变量。
        if (currentVariable == null) {
            renderPlaceholder("Select a variable from the list.");
            return;
        }
        // 只读信息变量不能参与平面绘制。
        if (!currentVariable.plottable()) {
            renderPlaceholder("Selected variable is informational only and cannot be rendered as a planar scalar field.");
            return;
        }

        try {
            // 单层变量固定读取第 0 层；分层变量从滑块读取当前层号。
            int layerIndex = currentVariable.layered() ? (int) Math.round(view.getDepthSlider().getValue()) : 0;
            // 读取当前层数值数组。
            double[] values = parser.readLayer(currentDataset, currentVariable, layerIndex);
            // 计算当前层的自动范围。
            RangeStats computedRange = RenderMath.computeRange(values, currentVariable.fillValue());
            // 无有效值时直接显示提示。
            if (computedRange.empty()) {
                renderPlaceholder("Selected layer contains no valid values.");
                return;
            }
            // 根据自动或手动配置得到最终展示范围。
            RangeStats displayRange = resolveRange(computedRange);
            // 读取当前选中的颜色表。
            ColorMap colorMap = colorMaps.getOrDefault(view.getColorMapCombo().getValue(), ColorMaps.viridis());
            // 确保视口已适配当前网格范围。
            viewportState.ensureFitted(currentDataset.mesh(), canvas.getWidth(), canvas.getHeight());
            // 生成新的渲染序号，供后台任务结果校验使用。
            long requestId = ++renderSequence;
            // 显示渲染中提示。
            view.getOverlayLabel().setText("Rendering " + currentVariable.name() + " ...");
            view.getOverlayLabel().setVisible(true);
            setStatus("Rendering " + currentVariable.name() + " ...");
            // 在后台线程中执行真正的图像渲染。
            renderAsync(requestId, layerIndex, values, colorMap, displayRange);
        } catch (Exception exception) {
            // 渲染准备阶段异常时，直接退回占位提示。
            renderPlaceholder("Could not render the selected variable: " + exception.getMessage());
        }
    }

    private void renderAsync(long requestId, int layerIndex, double[] values, ColorMap colorMap, RangeStats displayRange) {
        // 快照当前画布与状态，避免后台线程期间界面对象变化。
        Canvas canvas = view.getRenderCanvas();
        ParsedDataset dataset = currentDataset;
        VariableInfo variable = currentVariable;
        int width = Math.max(1, (int) Math.round(canvas.getWidth()));
        int height = Math.max(1, (int) Math.round(canvas.getHeight()));
        ViewportState.Snapshot snapshot = viewportState.snapshot();

        // 后台任务只负责离屏生成图像。
        Task<WritableImage> renderTask = new Task<>() {
            @Override
            protected WritableImage call() {
                // 先渲染为 BufferedImage，再转成 JavaFX 图像。
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

        // 渲染成功后回到界面线程刷新画布。
        renderTask.setOnSucceeded(event -> {
            // 如果当前结果已经过期，则直接丢弃。
            if (requestId != renderSequence || variable != currentVariable || dataset != currentDataset) {
                return;
            }
            // 清空旧画布内容。
            canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            // 绘制新的离屏结果。
            canvas.getGraphicsContext2D().drawImage(renderTask.getValue(), 0, 0, canvas.getWidth(), canvas.getHeight());
            // 刷新右侧色条。
            view.getColorBarCanvas().render(colorMap, displayRange);
            // 更新当前变量标签。
            view.getCurrentVariableLabel().setText("Variable: " + variable.name() + " " + variable.dimensionSummary());
            // 更新范围标签。
            view.getRangeInfoLabel().setText("Range: " + format(displayRange.min()) + " to " + format(displayRange.max()));
            // 更新层信息标签。
            updateLayerLabel(layerIndex);
            // 更新窗口标题。
            updateWindowTitle();
            // 隐藏渲染中提示。
            view.getOverlayLabel().setVisible(false);
            // 渲染成功后允许导出。
            view.getExportButton().setDisable(false);
            view.getExportPngMenuItem().setDisable(false);
            // 更新状态栏。
            setStatus("Rendered " + variable.name());
        });

        // 渲染失败时退回错误占位信息。
        renderTask.setOnFailed(event -> {
            if (requestId != renderSequence) {
                return;
            }
            Throwable error = renderTask.getException();
            renderPlaceholder("Could not render the selected variable: " + (error == null ? "unknown error" : error.getMessage()));
        });

        // 启动后台渲染线程。
        Thread worker = new Thread(renderTask, "render-" + requestId);
        worker.setDaemon(true);
        worker.start();
    }

    private RangeStats resolveRange(RangeStats computedRange) {
        // 自动范围模式直接采用计算结果，并同步填入文本框。
        if (view.getAutoRangeCheck().isSelected()) {
            view.getMinField().setText(format(computedRange.min()));
            view.getMaxField().setText(format(computedRange.max()));
            return computedRange;
        }
        try {
            // 解析用户输入的手动最小值。
            double manualMin = Double.parseDouble(view.getMinField().getText().trim());
            // 解析用户输入的手动最大值。
            double manualMax = Double.parseDouble(view.getMaxField().getText().trim());
            // 最大值必须大于最小值。
            if (manualMax <= manualMin) {
                throw new IllegalArgumentException("Manual max must be greater than manual min.");
            }
            return new RangeStats(manualMin, manualMax, computedRange.validCount());
        } catch (Exception exception) {
            // 手动输入无效时退回自动范围。
            view.getAutoRangeCheck().setSelected(true);
            setStatus("Invalid manual range. Reverting to automatic range.");
            return computedRange;
        }
    }

    private void updateLayerLabel(int layerIndex) {
        // 单层变量只显示固定文本。
        if (currentVariable == null || !currentVariable.layered()) {
            view.getLayerInfoLabel().setText("Layer: single");
            return;
        }
        // 先显示层号与总层数。
        String label = "Layer: " + (layerIndex + 1) + " / " + currentVariable.layerCount();
        // 如果存在实际层值数组，则附加显示对应的层值。
        double[] axisValues = currentDataset.axisValues(currentVariable.layerDimensionName()).orElse(null);
        if (axisValues != null && layerIndex < axisValues.length) {
            label += " (value=" + format(axisValues[layerIndex]) + ")";
        }
        view.getLayerInfoLabel().setText(label);
    }

    private void renderPlaceholder(String message) {
        // 清空主画布。
        view.getRenderCanvas().getGraphicsContext2D().clearRect(0, 0, view.getRenderCanvas().getWidth(), view.getRenderCanvas().getHeight());
        // 清空色条。
        view.getColorBarCanvas().clear();
        // 更新并显示占位提示。
        view.getOverlayLabel().setText(message);
        view.getOverlayLabel().setVisible(true);
        // 占位状态下不允许导出。
        view.getExportButton().setDisable(true);
        view.getExportPngMenuItem().setDisable(true);
        // 同步更新窗口标题。
        updateWindowTitle();
    }

    private void exportPng() {
        try {
            // 弹出保存对话框让用户选择输出位置。
            Path path = chooseSavePngFile();
            if (path == null) {
                setStatus("Export canceled.");
                return;
            }
            // 更新最近目录，方便下次导出。
            lastDirectory = path.toAbsolutePath().getParent();
            // 对当前可视化区域做快照。
            WritableImage image = view.getVisualizationBox().snapshot(new SnapshotParameters(), null);
            // 使用专用导出工具写出 PNG。
            PngExportSupport.writePng(image, path);
            setStatus("Exported " + path.getFileName());
        } catch (IOException exception) {
            // 导出写文件失败时显示详细错误。
            showError("Export failed", "Could not export PNG: " + exception.getMessage());
        } catch (Exception exception) {
            // 保存对话框或其他异常统一提示。
            showError("Export failed", "Could not open the save dialog: " + exception.getMessage());
        }
    }

    private void setStatus(String text) {
        // 更新状态栏文本。
        view.getStatusLabel().setText(text);
    }

    private void showError(String title, String message) {
        // 状态栏也同步显示错误内容。
        setStatus(message);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showAboutDialog() {
        // 弹出关于对话框，展示项目与作者信息。
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("About " + AppMetadata.APP_NAME);
        alert.setHeaderText(AppMetadata.APP_NAME + " " + AppMetadata.VERSION);
        alert.setContentText(aboutContent());
        alert.showAndWait();
    }

    static String aboutContent() {
        // 统一构造关于对话框正文内容。
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
        // 所有数值显示统一保留四位小数。
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private Path chooseSavePngFile() {
        // 调用 Swing 对话框选择保存路径。
        return SwingFileDialogs.chooseSavePngFile(lastDirectory);
    }

    private void updateWindowTitle() {
        // 从应用名称开始构造标题。
        StringBuilder title = new StringBuilder("NetCDF Viewer");
        if (currentDataset != null) {
            // 追加当前文件名。
            title.append(" - ").append(currentDataset.sourcePath().getFileName());
        }
        if (currentVariable != null) {
            // 追加当前变量名。
            title.append(" - ").append(currentVariable.name());
            if (currentVariable.layered()) {
                // 多层变量时进一步追加层号信息。
                int layerIndex = (int) Math.round(view.getDepthSlider().getValue());
                title.append(" [Layer ").append(layerIndex + 1).append("/").append(currentVariable.layerCount()).append("]");
            }
        }
        stage.setTitle(title.toString());
    }

    private static final class VariableCell extends ListCell<VariableInfo> {
        @Override
        protected void updateItem(VariableInfo item, boolean empty) {
            // 先执行父类标准刷新逻辑。
            super.updateItem(item, empty);
            // 空单元格直接清空内容。
            if (empty || item == null) {
                setText(null);
                return;
            }
            // 根据变量是否可画和是否分层生成前缀标签。
            String marker = item.plottable() ? (item.layered() ? "[Layered] " : "[Planar] ") : "[Info] ";
            // 组合最终显示文字。
            setText(marker + item.name() + " : " + item.presentableType() + " " + item.dimensionSummary());
            // 信息型变量用灰色显示，便于用户区分。
            if (!item.plottable()) {
                setStyle("-fx-text-fill: #6B7280;");
            } else {
                // 可视化变量使用默认样式。
                setStyle("");
            }
        }
    }
}
