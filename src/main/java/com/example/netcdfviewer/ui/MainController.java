package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.AppMetadata;
import com.example.netcdfviewer.basemap.BasemapLayer;
import com.example.netcdfviewer.basemap.BasemapRenderer;
import com.example.netcdfviewer.basemap.HttpBasemapTileProvider;
import com.example.netcdfviewer.io.LayerDataCache;
import com.example.netcdfviewer.io.NetcdfDatasetParser;
import com.example.netcdfviewer.io.ParsedDataset;
import com.example.netcdfviewer.model.CoordinateBinding;
import com.example.netcdfviewer.model.SpatialDomain;
import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.io.VelocityVariablePairFinder;
import com.example.netcdfviewer.io.WaveVariablePairFinder;
import com.example.netcdfviewer.io.WindVariablePairFinder;
import com.example.netcdfviewer.model.VariableInfo;
import com.example.netcdfviewer.model.VelocityVariablePair;
import com.example.netcdfviewer.model.WaveVariablePair;
import com.example.netcdfviewer.model.WindVariablePair;
import com.example.netcdfviewer.overlay.BuiltInCoastline;
import com.example.netcdfviewer.overlay.CoastlineOverlay;
import com.example.netcdfviewer.overlay.CoastlineOverlayLoader;
import com.example.netcdfviewer.overlay.CoastlineOverlayRenderer;
import com.example.netcdfviewer.render.ColorMap;
import com.example.netcdfviewer.render.ColorMaps;
import com.example.netcdfviewer.render.FlowLineGenerator;
import com.example.netcdfviewer.render.FlowLineOverlayRenderer;
import com.example.netcdfviewer.render.MeshPointQuery;
import com.example.netcdfviewer.render.RangeStats;
import com.example.netcdfviewer.render.RenderMath;
import com.example.netcdfviewer.render.StructuredGridImageRenderer;
import com.example.netcdfviewer.render.StructuredPointQuery;
import com.example.netcdfviewer.render.TriangleImageRenderer;
import com.example.netcdfviewer.render.WaveArrowOverlayRenderer;
import com.example.netcdfviewer.render.WindBarbOverlayRenderer;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.ListCell;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 主界面控制器。
 * 负责把界面、解析器、渲染器和导出逻辑串联起来。
 */
public final class MainController {
    // 控制器日志对象。
    private static final Logger logger = Logger.getLogger(MainController.class.getName());
    // 当前窗口对象。
    private final Stage stage;
    // 主界面视图对象。
    private final MainView view;
    // NetCDF 数据解析器。
    private final NetcdfDatasetParser parser = new NetcdfDatasetParser();
    // 变量层数据缓存。
    private final LayerDataCache layerDataCache = new LayerDataCache(512L * 1024L * 1024L);
    // 波场变量配对识别器。
    private final WaveVariablePairFinder waveVariablePairFinder = new WaveVariablePairFinder();
    // 流场速度变量配对识别器。
    private final VelocityVariablePairFinder velocityVariablePairFinder = new VelocityVariablePairFinder();
    // 风场变量配对识别器。
    private final WindVariablePairFinder windVariablePairFinder = new WindVariablePairFinder();
    // 后台离屏渲染器。
    private final TriangleImageRenderer imageRenderer = new TriangleImageRenderer();
    // 标准格网离屏渲染器。
    private final StructuredGridImageRenderer structuredImageRenderer = new StructuredGridImageRenderer();
    // 流线采样器。
    private final FlowLineGenerator flowLineGenerator = new FlowLineGenerator();
    // 波场箭头叠加绘制器。
    private final WaveArrowOverlayRenderer waveArrowOverlayRenderer = new WaveArrowOverlayRenderer();
    // 流线叠加绘制器。
    private final FlowLineOverlayRenderer flowLineOverlayRenderer = new FlowLineOverlayRenderer();
    // 风羽叠加绘制器。
    private final WindBarbOverlayRenderer windBarbOverlayRenderer = new WindBarbOverlayRenderer();
    // 海岸线叠加绘制器。
    private final CoastlineOverlayRenderer coastlineOverlayRenderer = new CoastlineOverlayRenderer();
    // 底图瓦片渲染器。
    private final BasemapRenderer basemapRenderer = new BasemapRenderer(new HttpBasemapTileProvider(defaultBasemapCacheDirectory()));
    // 当前视口状态，包含缩放和平移信息。
    private final ViewportState viewportState = new ViewportState();
    // 可用颜色表集合。
    private final Map<String, ColorMap> colorMaps = new LinkedHashMap<>();
    // 可用底图集合。
    private final Map<String, BasemapLayer> basemapLayers = new LinkedHashMap<>();
    // 每个数据集记住的结构化坐标绑定选择。
    private final Map<Path, String> preferredCoordinateBindingIds = new LinkedHashMap<>();
    // 已加载数据集集合。
    private final ObservableList<LoadedDatasetItem> loadedDatasets = FXCollections.observableArrayList();
    // 当前已打开的数据集。
    private ParsedDataset currentDataset;
    // 当前激活的数据空间域；标准格网会随坐标绑定切换。
    private SpatialDomain activeSpatialDomain;
    // 当前激活的结构化网格坐标绑定。
    private CoordinateBinding activeCoordinateBinding;
    // 当前选中的变量。
    private VariableInfo currentVariable;
    // 鼠标拖拽起点。
    private Point2D dragAnchor;
    // 鼠标点击起点，用于区分点击与拖拽。
    private Point2D clickAnchor;
    // Debounces expensive renders after mouse navigation.
    private final PauseTransition navigationRenderDelay = new PauseTransition(Duration.millis(160));
    // 最近一次打开或导出的目录。
    private Path lastDirectory = Paths.get(System.getProperty("user.home", "."));
    // 渲染序号，用于丢弃旧的后台渲染结果。
    private volatile long renderSequence;
    // Increments whenever the viewport or render inputs change.
    private volatile long renderInputSequence;
    // Input revision captured by the next render task.
    private long pendingRenderInputSequence;
    // Whether the next render should keep the current frame visible.
    private boolean pendingKeepCurrentFrameVisible;
    // 最近一次成功渲染的查询上下文。
    private RenderQueryContext latestRenderQueryContext;
    // Query context retained while viewport preview is active.
    private RenderQueryContext navigationPreviewQueryContext;
    // 当前海岸线叠加层。
    private CoastlineOverlay currentOverlay;
    // 当前数据集识别出的波场变量配对。
    private WaveVariablePair activeWavePair;
    // 当前数据集识别出的流场速度变量配对。
    private VelocityVariablePair activeVelocityPair;
    // 当前数据集识别出的风场变量配对。
    private WindVariablePair activeWindPair;
    // 最近一次成功渲染的底图。
    private WritableImage latestBaseImage;
    // Last complete rendered frame used for navigation preview.
    private WritableImage latestCompositeImage;
    // Viewport snapshot that produced the composite preview cache.
    private ViewportState.Snapshot latestCompositeSnapshot;
    // 最近一次成功渲染的波场箭头叠加数据。
    private WaveOverlayFrame latestWaveOverlayFrame;
    // 最近一次成功渲染的流线叠加数据。
    private FlowOverlayFrame latestFlowOverlayFrame;
    // 最近一次成功渲染的风羽叠加数据。
    private WindOverlayFrame latestWindOverlayFrame;
    // True while the canvas shows a transformed preview frame.
    private boolean navigationPreviewActive;
    // 流线亮带动画时间线。
    private Timeline flowAnimationTimeline;
    // 当前流线亮带动画相位。
    private double flowAnimationPhase;
    // 串行调度渲染任务的后台线程池。
    private final ThreadPoolExecutor renderTaskExecutor = new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        namedThreadFactory("render-dispatch")
    );
    // 负责底图和叠加层并行计算的线程池。
    private final ExecutorService renderComputeExecutor = new ThreadPoolExecutor(
        Math.max(2, Runtime.getRuntime().availableProcessors() - 1),
        Math.max(2, Runtime.getRuntime().availableProcessors() - 1),
        30L,
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(),
        namedThreadFactory("render-compute")
    );
    // 是否正在内部刷新坐标控件。
    private boolean updatingCoordinateControls;
    // 是否正在内部刷新底图控件。
    private boolean updatingBasemapControls;
    // 判定点击与拖拽的像素阈值。
    private static final double CLICK_TOLERANCE = 4.0;

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
        // 注册内置底图。
        configureBasemapControls();

        // 初始化颜色表下拉框。
        view.getColorMapCombo().setItems(FXCollections.observableArrayList(colorMaps.keySet()));
        view.getColorMapCombo().getSelectionModel().select("Viridis");
        // 初始化已加载数据集列表。
        view.getDatasetList().setItems(loadedDatasets);
        view.getClearCoastlineMenuItem().setDisable(true);
        view.getUseBuiltInCoastlineMenuItem().setDisable(false);
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
        view.getFlowLineCheck().setDisable(true);
        view.getFlowLineCheck().setSelected(false);
        view.getWaveArrowCheck().setDisable(true);
        view.getWaveArrowCheck().setSelected(false);
        view.getWindBarbCheck().setDisable(true);
        view.getWindBarbCheck().setSelected(false);
        view.getRemoveDatasetButton().setDisable(true);
        // 设置初始状态提示。
        setStatus("准备打开 NetCDF 文件。");
        navigationRenderDelay.setOnFinished(event -> renderCurrentSelection(true));
        // 窗口关闭时释放渲染线程池。
        stage.setOnHidden(event -> shutdownRenderExecutors());

        // 绑定画布尺寸变化监听。
        bindCanvasSize();
        // 绑定菜单、按钮和选项控件。
        wireActions();
        // 绑定鼠标平移和缩放事件。
        wireMouseNavigation();
        // 绑定拖拽打开文件事件。
        wireDragAndDrop();
    }

    /*
     * ========================================================================
     * 步骤1：初始化底图控件
     * ========================================================================
     * 目标：
     *   1) 注册内置 OpenStreetMap 标准底图
     *   2) 将底图下拉框与控制器状态接线
     */
    private void configureBasemapControls() {
        logger.info("开始初始化底图控件...");

        // 1.1 注册内置底图来源。
        BasemapLayer openStreetMapLayer = BasemapLayer.openStreetMapStandard();
        basemapLayers.put(openStreetMapLayer.name(), openStreetMapLayer);

        // 1.2 初始化底图下拉选项。
        updatingBasemapControls = true;
        view.getBasemapCombo().setItems(FXCollections.observableArrayList("无底图", openStreetMapLayer.name()));
        view.getBasemapCombo().getSelectionModel().select("无底图");
        view.getBasemapCheck().setSelected(false);
        updatingBasemapControls = false;

        logger.info("底图控件初始化完成。");
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
        // 海岸线加载与清理菜单。
        view.getLoadCoastlineMenuItem().setOnAction(event -> openCoastlineWithFileChooser());
        view.getUseBuiltInCoastlineMenuItem().setOnAction(event -> useBuiltInCoastline());
        view.getClearCoastlineMenuItem().setOnAction(event -> clearCoastlineOverlay());
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
            updateCoordinateControls();
            updateVariableMeta();
            updateDepthControls();
            renderCurrentSelection();
        });
        // 数据集选择变化时，切换当前活动数据集。
        view.getDatasetList().getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            view.getRemoveDatasetButton().setDisable(newValue == null);
            if (newValue != null) {
                activateDataset(newValue);
            } else if (loadedDatasets.isEmpty()) {
                clearActiveDatasetState();
            }
        });
        // 删除按钮删除当前选中数据集。
        view.getRemoveDatasetButton().setOnAction(event -> removeSelectedDataset());

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
        // 波场箭头开关变化时重绘当前视图。
        view.getWaveArrowCheck().selectedProperty().addListener((obs, oldValue, newValue) -> renderCurrentSelection());
        // 风羽开关开启时重新准备叠加数据，关闭时直接复用缓存底图重绘。
        view.getWindBarbCheck().selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue) {
                renderCurrentSelection();
                return;
            }
            redrawCurrentView();
        });
        // 流线开关开启时重新准备叠加数据，关闭时直接用缓存底图重绘。
        view.getFlowLineCheck().selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue) {
                renderCurrentSelection();
                return;
            }
            stopFlowAnimation();
            redrawCurrentView();
        });
        // 手动应用范围时重新渲染。
        view.getApplyRangeButton().setOnAction(event -> renderCurrentSelection());
        // 底图开关变化时重绘当前视图。
        view.getBasemapCheck().selectedProperty().addListener((obs, oldValue, newValue) -> handleBasemapEnabledChanged(newValue));
        // 底图来源变化时同步开关状态并重绘。
        view.getBasemapCombo().valueProperty().addListener((obs, oldValue, newValue) -> handleBasemapSelectionChanged(newValue));
        // 底图透明度变化时重绘当前视图。
        view.getBasemapOpacitySlider().valueProperty().addListener((obs, oldValue, newValue) -> renderCurrentSelection());
        // 自定义底图按钮打开 XYZ 模板输入框。
        view.getCustomBasemapButton().setOnAction(event -> openCustomBasemapDialog());
        // 结构化网格坐标选择变化时切换活动坐标域并重绘。
        view.getCoordinateXCombo().valueProperty().addListener((obs, oldValue, newValue) -> handleCoordinateSelectionChanged(true));
        view.getCoordinateYCombo().valueProperty().addListener((obs, oldValue, newValue) -> handleCoordinateSelectionChanged(false));
    }

    /*
     * ========================================================================
     * 步骤2：处理底图开关变化
     * ========================================================================
     * 目标：
     *   1) 开启底图时自动选择默认底图
     *   2) 关闭底图时保持下拉选择但不参与渲染
     */
    private void handleBasemapEnabledChanged(boolean enabled) {
        logger.info(() -> "开始处理底图开关变化, enabled=" + enabled);

        // 2.1 内部刷新期间不触发重复渲染。
        if (updatingBasemapControls) {
            logger.info("底图开关变化处理结束, skipped=true");
            return;
        }

        // 2.2 开启底图且当前没有具体来源时，默认选择 OSM 标准地图。
        if (enabled && "无底图".equals(view.getBasemapCombo().getValue())) {
            updatingBasemapControls = true;
            view.getBasemapCombo().getSelectionModel().select("OpenStreetMap 标准地图");
            updatingBasemapControls = false;
        }

        // 2.3 按最新状态重绘当前视图。
        renderCurrentSelection();

        logger.info("底图开关变化处理结束。");
    }

    /*
     * ========================================================================
     * 步骤3：处理底图来源变化
     * ========================================================================
     * 目标：
     *   1) 选择无底图时关闭底图开关
     *   2) 选择具体底图时自动开启底图开关
     */
    private void handleBasemapSelectionChanged(String selectedName) {
        logger.info(() -> "开始处理底图来源变化, selectedName=" + selectedName);

        // 3.1 内部刷新期间不触发重复渲染。
        if (updatingBasemapControls) {
            logger.info("底图来源变化处理结束, skipped=true");
            return;
        }

        // 3.2 同步底图开关状态。
        updatingBasemapControls = true;
        view.getBasemapCheck().setSelected(selectedName != null && basemapLayers.containsKey(selectedName));
        updatingBasemapControls = false;

        // 3.3 按最新状态重绘当前视图。
        renderCurrentSelection();

        logger.info("底图来源变化处理结束。");
    }

    /*
     * ========================================================================
     * 步骤4：打开自定义底图输入框
     * ========================================================================
     * 目标：
     *   1) 读取用户输入的 XYZ URL 模板
     *   2) 校验通过后加入底图列表并立即启用
     */
    private void openCustomBasemapDialog() {
        logger.info("开始打开自定义底图输入框...");

        // 4.1 创建并显示 URL 模板输入框。
        TextInputDialog dialog = new TextInputDialog("https://example.com/tiles/{z}/{x}/{y}.png");
        dialog.setTitle("自定义 XYZ 底图");
        dialog.setHeaderText("输入 XYZ 瓦片 URL 模板");
        dialog.setContentText("URL 模板：");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            setStatus("已取消自定义底图。");
            logger.info("自定义底图输入框关闭, cancelled=true");
            return;
        }

        try {
            // 4.2 校验并注册自定义底图。
            BasemapLayer customLayer = BasemapLayer.custom("自定义底图", result.orElseThrow());
            basemapLayers.put(customLayer.name(), customLayer);
            updatingBasemapControls = true;
            if (!view.getBasemapCombo().getItems().contains(customLayer.name())) {
                view.getBasemapCombo().getItems().add(customLayer.name());
            }
            view.getBasemapCombo().getSelectionModel().select(customLayer.name());
            view.getBasemapCheck().setSelected(true);
            updatingBasemapControls = false;

            // 4.3 立即触发重绘。
            setStatus("已启用自定义底图。");
            renderCurrentSelection();
            logger.info("自定义底图启用完成。");
        } catch (IllegalArgumentException exception) {
            showError("自定义底图无效", exception.getMessage());
            logger.info(() -> "自定义底图启用失败, reason=" + exception.getMessage());
        }
    }

    /*
     * ========================================================================
     * 步骤5：解析当前底图选择
     * ========================================================================
     * 目标：
     *   1) 只在用户启用底图时返回图层
     *   2) 将无底图和未知名称统一视为不绘制底图
     */
    private BasemapLayer selectedBasemapLayer() {
        logger.info("开始解析当前底图选择...");

        // 5.1 底图开关关闭时不绘制底图。
        if (!view.getBasemapCheck().isSelected()) {
            logger.info("底图选择解析完成, selected=false");
            return null;
        }

        // 5.2 按下拉框名称查找底图定义。
        BasemapLayer layer = basemapLayers.get(view.getBasemapCombo().getValue());

        logger.info(() -> "底图选择解析完成, selected=" + (layer != null));
        return layer;
    }

    /*
     * ========================================================================
     * 步骤6：判断空间域是否可叠加在线底图
     * ========================================================================
     * 目标：
     *   1) 第一版仅支持经纬度坐标数据
     *   2) 防止投影坐标误套 Web Mercator 瓦片
     */
    private boolean isGeographicDomain(SpatialDomain spatialDomain) {
        logger.info("开始判断空间域是否为经纬度...");

        // 6.1 空空间域不能叠加底图。
        if (spatialDomain == null) {
            logger.info("空间域经纬度判断完成, geographic=false");
            return false;
        }

        // 6.2 用世界坐标范围判断是否落在经纬度边界内。
        boolean geographic = spatialDomain.minX() >= -180.000001
            && spatialDomain.maxX() <= 180.000001
            && spatialDomain.minY() >= -90.000001
            && spatialDomain.maxY() <= 90.000001;

        logger.info(() -> "空间域经纬度判断完成, geographic=" + geographic);
        return geographic;
    }

    private void wireMouseNavigation() {
        view.getCanvasHost().setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                dragAnchor = new Point2D(event.getX(), event.getY());
                clickAnchor = dragAnchor;
            }
        });
        view.getCanvasHost().setOnMouseDragged(event -> {
            if (dragAnchor == null || activeSpatialDomain == null) {
                return;
            }
            Point2D currentPoint = new Point2D(event.getX(), event.getY());
            Point2D delta = currentPoint.subtract(dragAnchor);
            viewportState.pan(delta.getX(), delta.getY());
            dragAnchor = currentPoint;
            previewCurrentViewport();
        });
        view.getCanvasHost().setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY && clickAnchor != null) {
                Point2D releasePoint = new Point2D(event.getX(), event.getY());
                if (releasePoint.distance(clickAnchor) <= CLICK_TOLERANCE) {
                    queryAtScreenPoint(releasePoint.getX(), releasePoint.getY());
                }
            }
            dragAnchor = null;
            clickAnchor = null;
        });
        view.getCanvasHost().setOnScroll(event -> {
            if (activeSpatialDomain == null) {
                return;
            }
            double factor = event.getDeltaY() >= 0 ? 1.12 : 1.0 / 1.12;
            viewportState.zoom(factor, event.getX(), event.getY());
            previewCurrentViewport();
            event.consume();
        });
    }

    private void previewCurrentViewport() {
        logger.info("Start viewport preview");

        if (currentDataset == null || currentVariable == null || !currentVariable.plottable() || activeSpatialDomain == null) {
            logger.info("Viewport preview skipped: no active plottable selection");
            return;
        }

        renderInputSequence++;
        stopFlowAnimation();
        navigationPreviewActive = true;

        RenderQueryContext context = latestRenderQueryContext;
        if (context != null) {
            latestRenderQueryContext = new RenderQueryContext(
                context.dataset(),
                context.spatialDomain(),
                context.variable(),
                context.layerIndex(),
                context.values(),
                context.cellCentered(),
                context.fillValue(),
                viewportState.snapshot()
            );
        }
        navigationPreviewQueryContext = latestRenderQueryContext;

        boolean previewDrawn = drawViewportPreview();
        navigationRenderDelay.stop();
        navigationRenderDelay.playFromStart();
        logger.info(() -> "Finish viewport preview, drawn=" + previewDrawn);
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
            List<Path> paths = netcdfFilePaths(dragboard.getFiles());
            if (!paths.isEmpty()) {
                loadFiles(paths);
                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }
            event.consume();
        });
    }

    private boolean hasNetcdfFile(Dragboard dragboard) {
        return dragboard.hasFiles() && !netcdfFilePaths(dragboard.getFiles()).isEmpty();
    }

    static List<Path> netcdfFilePaths(List<File> files) {
        return files.stream()
            .filter(file -> file.getName().toLowerCase(Locale.ROOT).endsWith(".nc"))
            .map(File::toPath)
            .collect(Collectors.toList());
    }

    private void openWithFileChooser() {
        try {
            // 弹出文件选择器，让用户选择 NetCDF 文件。
            List<Path> paths = SwingFileDialogs.chooseOpenFiles(lastDirectory);
            if (!paths.isEmpty()) {
                loadFiles(paths);
            } else {
                // 用户取消时仅更新状态栏。
                setStatus("已取消打开文件。");
            }
        } catch (Exception exception) {
            // 文件选择器异常时提示用户。
            showError("打开失败", "无法打开文件选择器：" + exception.getMessage());
        }
    }

    private void openCoastlineWithFileChooser() {
        try {
            Path path = SwingFileDialogs.chooseOpenCoastlineFile(lastDirectory);
            if (path != null) {
                loadCoastline(path);
            } else {
                setStatus("已取消加载海岸线。");
            }
        } catch (Exception exception) {
            showError("加载海岸线失败", "无法打开海岸线文件选择器：" + exception.getMessage());
        }
    }

    private void loadFiles(List<Path> paths) {
        paths.forEach(this::loadFile);
    }

    private void loadFile(Path path) {
        // 空路径直接忽略。
        if (path == null) {
            return;
        }

        Path normalizedPath = normalizePath(path);
        // 同一路径重复添加时直接切换到已存在的数据集。
        LoadedDatasetItem existing = findLoadedDataset(normalizedPath);
        if (existing != null) {
            view.getDatasetList().getSelectionModel().select(existing);
            setStatus("数据已加载，已切换到已有条目。");
            return;
        }

        // 记录最近目录，便于下次打开或导出时默认定位。
        lastDirectory = normalizedPath.getParent();
        // 如果当前还没有任何数据集，则显示加载中的占位提示。
        if (loadedDatasets.isEmpty()) {
            renderPlaceholder("正在加载 " + normalizedPath.getFileName() + " ...");
        }
        setStatus("正在加载 " + normalizedPath.getFileName() + " ...");

        // 使用后台任务读取文件，避免卡住界面线程。
        Task<ParsedDataset> loadTask = new Task<>() {
            @Override
            protected ParsedDataset call() throws Exception {
                return parser.open(normalizedPath);
            }
        };

        // 读取成功后刷新界面。
        loadTask.setOnSucceeded(event -> addLoadedDataset(normalizedPath, loadTask.getValue()));
        // 读取失败时显示错误并回到占位状态。
        loadTask.setOnFailed(event -> {
            Throwable error = loadTask.getException();
            showError("打开失败", "无法解析 " + normalizedPath.getFileName() + "：" + (error == null ? "未知错误" : error.getMessage()));
            if (loadedDatasets.isEmpty()) {
                renderPlaceholder("加载失败：" + normalizedPath.getFileName() + "。");
            }
        });

        // 启动后台读取线程。
        Thread worker = new Thread(loadTask, "netcdf-load-" + normalizedPath.getFileName());
        worker.setDaemon(true);
        worker.start();
    }

    private void addLoadedDataset(Path path, ParsedDataset dataset) {
        // 在后台加载完成前后都要再次检查是否已存在相同路径，避免重复项。
        LoadedDatasetItem existing = findLoadedDataset(path);
        if (existing != null) {
            view.getDatasetList().getSelectionModel().select(existing);
            setStatus("数据已加载，已切换到已有条目。");
            return;
        }
        LoadedDatasetItem item = new LoadedDatasetItem(path, path.getFileName().toString(), dataset);
        loadedDatasets.add(item);
        view.getDatasetList().getSelectionModel().select(item);
        setStatus("已加载 " + path.getFileName());
    }

    private void openFile(Path path) {
        try {
            Path normalizedPath = normalizePath(path);
            LoadedDatasetItem existing = findLoadedDataset(normalizedPath);
            if (existing != null) {
                view.getDatasetList().getSelectionModel().select(existing);
                setStatus("数据已加载，已切换到已有条目。");
                return;
            }
            lastDirectory = normalizedPath.getParent();
            ParsedDataset dataset = parser.open(normalizedPath);
            addLoadedDataset(normalizedPath, dataset);
        } catch (Exception exception) {
            showError("打开失败", "无法解析 " + path.getFileName() + "：" + exception.getMessage());
        }
    }

    private void activateDataset(LoadedDatasetItem item) {
        logger.info(() -> "开始激活数据集, sourcePath=" + item.sourcePath());

        // 切换活动数据集时先重置与当前渲染相关的瞬时状态。
        currentDataset = item.dataset();
        activeSpatialDomain = item.dataset().spatialDomain();
        activeCoordinateBinding = item.dataset().selectedCoordinateBinding().orElse(null);
        currentVariable = null;
        activeWavePair = waveVariablePairFinder.find(item.dataset()).orElse(null);
        activeVelocityPair = velocityVariablePairFinder.find(item.dataset()).orElse(null);
        activeWindPair = windVariablePairFinder.find(item.dataset()).orElse(null);
        latestRenderQueryContext = null;
        latestBaseImage = null;
        latestWaveOverlayFrame = null;
        latestFlowOverlayFrame = null;
        latestWindOverlayFrame = null;
        flowAnimationPhase = 0.0;
        stopFlowAnimation();
        viewportState.reset();
        // 更新摘要、属性和警告面板。
        updateDatasetPanels(item.dataset());
        updateCoordinateControls();
        // 将该数据集的变量加载到变量列表中。
        view.getVariableList().setItems(FXCollections.observableArrayList(item.dataset().variables()));
        view.getCurrentVariableLabel().setText("变量：-");
        view.getRangeInfoLabel().setText("范围：-");
        view.getLayerInfoLabel().setText("图层：-");
        updateFlowLineControls();
        updateWaveArrowControls();
        updateWindBarbControls();
        // 优先选中第一个可绘制变量，提升切换体验。
        VariableInfo preferredVariable = item.dataset().plottableVariables().stream().findFirst().orElse(null);
        if (preferredVariable != null) {
            view.getVariableList().getSelectionModel().select(preferredVariable);
        } else if (!item.dataset().variables().isEmpty()) {
            view.getVariableList().getSelectionModel().select(0);
            renderPlaceholder("This dataset contains no plottable planar variable.");
        } else {
            updateVariableMeta();
            renderPlaceholder("The file contains no variables.");
        }
        updateWindowTitle();

        logger.info(() -> "数据集激活完成, sourcePath="
            + item.sourcePath()
            + ", waveAvailable="
            + (activeWavePair != null)
            + ", flowAvailable="
            + (activeVelocityPair != null)
            + ", windAvailable="
            + (activeWindPair != null));
    }

    private void loadCoastline(Path path) {
        if (path == null) {
            return;
        }
        try {
            Path normalizedPath = path.toAbsolutePath().normalize();
            lastDirectory = normalizedPath.getParent();
            CoastlineOverlay overlay = CoastlineOverlayLoader.load(normalizedPath);
            if (overlay.paths().isEmpty()) {
                throw new IOException("The coastline file does not contain supported line or polygon geometry.");
            }
            currentOverlay = overlay;
            view.getClearCoastlineMenuItem().setDisable(false);
            setStatus("已加载海岸线 " + overlay.displayName() + "（" + overlay.paths().size() + " 条路径）");
            redrawCurrentView();
        } catch (IOException exception) {
            showError("加载海岸线失败", "无法解析海岸线叠加层：" + exception.getMessage());
        }
    }

    private void clearCoastlineOverlay() {
        currentOverlay = null;
        view.getClearCoastlineMenuItem().setDisable(true);
        setStatus("已清除海岸线叠加层。");
        redrawCurrentView();
    }

    private void useBuiltInCoastline() {
        try {
            currentOverlay = BuiltInCoastline.load();
            view.getClearCoastlineMenuItem().setDisable(false);
            setStatus("已使用内置海岸线。");
            redrawCurrentView();
        } catch (IOException exception) {
            showError("内置海岸线加载失败", "无法加载内置海岸线资源：" + exception.getMessage());
        }
    }

    private void removeSelectedDataset() {
        LoadedDatasetItem selected = view.getDatasetList().getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("没有选中要删除的数据。");
            return;
        }

        int selectedIndex = view.getDatasetList().getSelectionModel().getSelectedIndex();
        loadedDatasets.remove(selected);
        preferredCoordinateBindingIds.remove(selected.sourcePath());
        layerDataCache.removeSource(selected.sourcePath());

        if (loadedDatasets.isEmpty()) {
            clearActiveDatasetState();
            setStatus("已删除 " + selected.displayName());
            return;
        }

        int fallbackIndex = Math.min(selectedIndex, loadedDatasets.size() - 1);
        view.getDatasetList().getSelectionModel().select(fallbackIndex);
        setStatus("已删除 " + selected.displayName());
    }

    private void clearActiveDatasetState() {
        logger.info("开始清空活动数据集状态");

        currentDataset = null;
        activeSpatialDomain = null;
        activeCoordinateBinding = null;
        currentVariable = null;
        activeWavePair = null;
        activeVelocityPair = null;
        activeWindPair = null;
        layerDataCache.clear();
        latestRenderQueryContext = null;
        navigationPreviewQueryContext = null;
        latestBaseImage = null;
        latestCompositeImage = null;
        latestCompositeSnapshot = null;
        latestWaveOverlayFrame = null;
        latestFlowOverlayFrame = null;
        latestWindOverlayFrame = null;
        navigationPreviewActive = false;
        flowAnimationPhase = 0.0;
        navigationRenderDelay.stop();
        stopFlowAnimation();
        view.getVariableList().getItems().clear();
        view.getDatasetLabel().setText("未加载文件");
        view.getCoordinateVariableLabel().setText("坐标：-");
        view.getCoordinateSelectionBox().setVisible(false);
        view.getCoordinateSelectionBox().setManaged(false);
        view.getCoordinateXCombo().getItems().clear();
        view.getCoordinateYCombo().getItems().clear();
        view.getCoordinateXCombo().setDisable(true);
        view.getCoordinateYCombo().setDisable(true);
        view.getConnectivityVariableLabel().setText("连接关系：-");
        view.getVariableMetaLabel().setText("变量详情：-");
        view.getCurrentVariableLabel().setText("变量：-");
        view.getLayerInfoLabel().setText("图层：-");
        view.getRangeInfoLabel().setText("范围：-");
        view.getSummaryArea().clear();
        view.getAttributesArea().clear();
        view.getWarningsArea().clear();
        view.getDepthSlider().setDisable(true);
        view.getDepthSlider().setValue(0);
        view.getVisualizeButton().setDisable(true);
        view.getApplyRangeButton().setDisable(true);
        view.getMinField().setDisable(true);
        view.getMaxField().setDisable(true);
        view.getFlowLineCheck().setSelected(false);
        view.getFlowLineCheck().setDisable(true);
        view.getWaveArrowCheck().setSelected(false);
        view.getWaveArrowCheck().setDisable(true);
        view.getWindBarbCheck().setSelected(false);
        view.getWindBarbCheck().setDisable(true);
        renderPlaceholder("打开 NetCDF 文件开始。");
        updateWindowTitle();

        logger.info("活动数据集状态清空完成");
    }

    private LoadedDatasetItem findLoadedDataset(Path path) {
        return loadedDatasets.stream()
            .filter(item -> item.sourcePath().equals(path))
            .findFirst()
            .orElse(null);
    }

    private Path normalizePath(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /*
     * ========================================================================
     * 步骤1：刷新结构化网格坐标控件
     * ========================================================================
     * 目标：
     *   1) 让标准格网数据按当前变量自动匹配坐标基准
     *   2) 在有多个候选时开放横纵坐标切换
     * 操作要点：
     *   1) 三角网直接隐藏控件
     *   2) 标准格网优先选当前变量兼容的绑定
     */
    private void updateCoordinateControls() {
        // 1.1 无数据集时直接隐藏控件并清空活动绑定。
        if (currentDataset == null) {
            activeSpatialDomain = null;
            activeCoordinateBinding = null;
            updateCoordinateControlState(List.of(), null);
            return;
        }

        // 1.2 三角网或无坐标候选时沿用原始空间域，并隐藏控件。
        if (!currentDataset.hasSpatialDomain()
            || currentDataset.spatialDomain().kind() != SpatialDomain.Kind.STRUCTURED_GRID
            || currentDataset.coordinateBindings().isEmpty()) {
            activeSpatialDomain = currentDataset.spatialDomain();
            activeCoordinateBinding = null;
            updateCoordinateControlState(List.of(), null);
            return;
        }

        // 1.3 先按当前变量筛出兼容的坐标绑定，再决定激活哪一个。
        List<CoordinateBinding> compatibleBindings = resolveCompatibleCoordinateBindings();
        CoordinateBinding binding = resolvePreferredCoordinateBinding(compatibleBindings);
        applyActiveCoordinateBinding(binding, compatibleBindings, false);
    }

    /*
     * ========================================================================
     * 步骤2：响应结构化网格坐标切换
     * ========================================================================
     * 目标：
     *   1) 把用户在 X/Y 下拉框中的选择收敛成一个有效绑定
     *   2) 切换活动空间域后立即触发重绘
     * 操作要点：
     *   1) 内部刷新控件时不重复处理
     *   2) 选不到精确组合时按用户刚改的轴兜底
     */
    private void handleCoordinateSelectionChanged(boolean xAxisChanged) {
        // 2.1 没有结构化网格上下文或当前正在内部刷新时直接返回。
        if (updatingCoordinateControls
            || currentDataset == null
            || activeSpatialDomain == null
            || activeSpatialDomain.kind() != SpatialDomain.Kind.STRUCTURED_GRID) {
            return;
        }

        // 2.2 用当前变量可接受的候选集合解析用户选择的坐标绑定。
        List<CoordinateBinding> compatibleBindings = resolveCompatibleCoordinateBindings();
        CoordinateBinding binding = resolveBindingFromSelection(
            compatibleBindings,
            view.getCoordinateXCombo().getValue(),
            view.getCoordinateYCombo().getValue(),
            xAxisChanged
        );
        if (binding == null) {
            return;
        }

        // 2.3 切换活动绑定并重绘当前变量。
        applyActiveCoordinateBinding(binding, compatibleBindings, true);
        renderCurrentSelection();
    }

    /*
     * ========================================================================
     * 步骤3：筛选当前变量兼容的坐标绑定
     * ========================================================================
     * 目标：
     *   1) 避免把不匹配当前变量维度的横纵坐标暴露到界面
     * 操作要点：
     *   1) 优先用 basisId 精确匹配
     *   2) 再回退到维度包含关系匹配
     */
    private List<CoordinateBinding> resolveCompatibleCoordinateBindings() {
        // 3.1 当前数据集没有结构化坐标候选时直接返回空集合。
        if (currentDataset == null || currentDataset.coordinateBindings().isEmpty()) {
            return List.of();
        }

        // 3.2 没选变量或当前变量不是结构化可绘制字段时，直接暴露全部候选。
        if (currentVariable == null
            || !currentVariable.plottable()
            || currentVariable.geometryKind() != SpatialDomain.Kind.STRUCTURED_GRID) {
            return currentDataset.coordinateBindings();
        }

        // 3.3 先按 basisId 精确匹配；命中后直接返回该组候选。
        if (currentVariable.basisId() != null) {
            List<CoordinateBinding> matchedByBasis = currentDataset.coordinateBindings().stream()
                .filter(binding -> currentVariable.basisId().equals(binding.id()))
                .collect(Collectors.toList());
            if (!matchedByBasis.isEmpty()) {
                return matchedByBasis;
            }
        }

        // 3.4 再按变量维度是否包含绑定维度做回退匹配。
        List<CoordinateBinding> matchedByDimensions = currentDataset.coordinateBindings().stream()
            .filter(binding -> currentVariable.dimensionNames().containsAll(binding.horizontalDimensions()))
            .collect(Collectors.toList());
        return matchedByDimensions.isEmpty() ? currentDataset.coordinateBindings() : matchedByDimensions;
    }

    /*
     * ========================================================================
     * 步骤4：决定当前应激活的结构化坐标绑定
     * ========================================================================
     * 目标：
     *   1) 在记忆用户选择、变量基准和默认绑定之间选出当前绑定
     * 操作要点：
     *   1) 先尝试用户已记住的绑定
     *   2) 再回到变量基准和数据集默认绑定
     */
    private CoordinateBinding resolvePreferredCoordinateBinding(List<CoordinateBinding> compatibleBindings) {
        // 4.1 没有候选时直接返回空。
        if (compatibleBindings.isEmpty()) {
            return null;
        }

        // 4.2 先恢复当前数据集上一次成功选择的绑定。
        String rememberedBindingId = preferredCoordinateBindingIds.get(currentDataset.sourcePath());
        CoordinateBinding rememberedBinding = findBindingById(compatibleBindings, rememberedBindingId);
        if (rememberedBinding != null) {
            return rememberedBinding;
        }

        // 4.3 再优先对齐当前变量自己的水平基准。
        CoordinateBinding variableBinding = currentVariable == null ? null : findBindingById(compatibleBindings, currentVariable.basisId());
        if (variableBinding != null) {
            return variableBinding;
        }

        // 4.4 保留当前仍然兼容的活动绑定，避免无意义跳变。
        CoordinateBinding currentBinding = findBindingById(compatibleBindings, activeCoordinateBinding == null ? null : activeCoordinateBinding.id());
        if (currentBinding != null) {
            return currentBinding;
        }

        // 4.5 最后回落到数据集默认绑定或首个候选。
        CoordinateBinding datasetBinding = findBindingById(
            compatibleBindings,
            currentDataset.selectedCoordinateBinding().map(CoordinateBinding::id).orElse(null)
        );
        return datasetBinding == null ? compatibleBindings.get(0) : datasetBinding;
    }

    /*
     * ========================================================================
     * 步骤5：把下拉框选择解析成有效绑定
     * ========================================================================
     * 目标：
     *   1) 从 X/Y 选择值中恢复出真正的坐标绑定对象
     * 操作要点：
     *   1) 先匹配精确组合
     *   2) 失败时按刚变更的轴做最小兜底
     */
    private CoordinateBinding resolveBindingFromSelection(
        List<CoordinateBinding> compatibleBindings,
        String selectedXName,
        String selectedYName,
        boolean xAxisChanged
    ) {
        // 5.1 没有候选时直接返回空。
        if (compatibleBindings.isEmpty()) {
            return null;
        }

        // 5.2 先找完全匹配当前 X/Y 组合的绑定。
        CoordinateBinding exactBinding = compatibleBindings.stream()
            .filter(binding -> binding.xName().equals(selectedXName) && binding.yName().equals(selectedYName))
            .findFirst()
            .orElse(null);
        if (exactBinding != null) {
            return exactBinding;
        }

        // 5.3 若精确组合不存在，则按用户刚修改的轴找首个兼容绑定。
        if (xAxisChanged && selectedXName != null) {
            CoordinateBinding xBinding = compatibleBindings.stream()
                .filter(binding -> binding.xName().equals(selectedXName))
                .findFirst()
                .orElse(null);
            if (xBinding != null) {
                return xBinding;
            }
        }
        if (!xAxisChanged && selectedYName != null) {
            CoordinateBinding yBinding = compatibleBindings.stream()
                .filter(binding -> binding.yName().equals(selectedYName))
                .findFirst()
                .orElse(null);
            if (yBinding != null) {
                return yBinding;
            }
        }

        return compatibleBindings.get(0);
    }

    /*
     * ========================================================================
     * 步骤6：应用活动结构化坐标绑定
     * ========================================================================
     * 目标：
     *   1) 同步活动空间域、界面下拉框和摘要信息
     * 操作要点：
     *   1) 先构造新的结构化空间域
     *   2) 再刷新坐标控件和摘要文本
     */
    private void applyActiveCoordinateBinding(
        CoordinateBinding binding,
        List<CoordinateBinding> compatibleBindings,
        boolean rememberSelection
    ) {
        // 6.1 无绑定时回退到数据集原始空间域。
        if (binding == null || currentDataset == null) {
            activeCoordinateBinding = null;
            activeSpatialDomain = currentDataset == null ? null : currentDataset.spatialDomain();
            updateCoordinateControlState(List.of(), null);
            updateDatasetPanels(currentDataset);
            return;
        }

        // 6.2 构造当前绑定对应的结构化空间域。
        activeCoordinateBinding = binding;
        activeSpatialDomain = buildStructuredDomain(currentDataset, binding);
        if (activeSpatialDomain == null) {
            activeSpatialDomain = currentDataset.spatialDomain();
        }

        // 6.3 刷新坐标控件展示状态。
        updateCoordinateControlState(compatibleBindings, binding);

        // 6.4 需要记住用户选择时，写回当前数据集的记忆表。
        if (rememberSelection) {
            preferredCoordinateBindingIds.put(currentDataset.sourcePath(), binding.id());
        }

        // 6.5 坐标绑定改变后同步刷新摘要面板和坐标标签。
        updateDatasetPanels(currentDataset);
    }

    /*
     * ========================================================================
     * 步骤7：刷新坐标控件展示状态
     * ========================================================================
     * 目标：
     *   1) 让坐标下拉框只显示当前可选项
     * 操作要点：
     *   1) 内部刷新时临时关闭监听
     *   2) 没有候选时完全隐藏控件
     */
    private void updateCoordinateControlState(List<CoordinateBinding> compatibleBindings, CoordinateBinding selectedBinding) {
        // 7.1 内部刷新控件时先拉起保护开关，避免重复触发监听。
        updatingCoordinateControls = true;
        try {
            if (compatibleBindings.isEmpty() || selectedBinding == null) {
                view.getCoordinateSelectionBox().setVisible(false);
                view.getCoordinateSelectionBox().setManaged(false);
                view.getCoordinateXCombo().getItems().clear();
                view.getCoordinateYCombo().getItems().clear();
                view.getCoordinateXCombo().setDisable(true);
                view.getCoordinateYCombo().setDisable(true);
                return;
            }

            // 7.2 用去重后的 X/Y 名称刷新下拉框内容。
            view.getCoordinateSelectionBox().setVisible(true);
            view.getCoordinateSelectionBox().setManaged(true);
            view.getCoordinateXCombo().setItems(FXCollections.observableArrayList(
                compatibleBindings.stream().map(CoordinateBinding::xName).distinct().toList()
            ));
            view.getCoordinateYCombo().setItems(FXCollections.observableArrayList(
                compatibleBindings.stream().map(CoordinateBinding::yName).distinct().toList()
            ));
            view.getCoordinateXCombo().getSelectionModel().select(selectedBinding.xName());
            view.getCoordinateYCombo().getSelectionModel().select(selectedBinding.yName());
            view.getCoordinateXCombo().setDisable(compatibleBindings.size() <= 1);
            view.getCoordinateYCombo().setDisable(compatibleBindings.size() <= 1);
        } finally {
            updatingCoordinateControls = false;
        }
    }

    private CoordinateBinding findBindingById(List<CoordinateBinding> bindings, String bindingId) {
        if (bindingId == null) {
            return null;
        }
        return bindings.stream()
            .filter(binding -> binding.id().equals(bindingId))
            .findFirst()
            .orElse(null);
    }

    private StructuredGridDomain buildStructuredDomain(ParsedDataset dataset, CoordinateBinding binding) {
        if (dataset == null || binding == null) {
            return null;
        }
        double[] xAxis = dataset.axisValues(binding.xName())
            .or(() -> dataset.axisValues(binding.horizontalDimensions().get(0)))
            .orElse(null);
        double[] yAxis = dataset.axisValues(binding.yName())
            .or(() -> dataset.axisValues(binding.horizontalDimensions().get(1)))
            .orElse(null);
        if (xAxis == null || yAxis == null || xAxis.length == 0 || yAxis.length == 0) {
            return null;
        }
        return new StructuredGridDomain(
            new com.example.netcdfviewer.model.StructuredGridData(
                binding,
                xAxis,
                yAxis,
                null,
                null,
                xAxis.length,
                yAxis.length
            ),
            binding
        );
    }

    private StructuredGridDomain resolveStructuredDomainForVariable(ParsedDataset dataset, VariableInfo variable) {
        if (dataset == null
            || variable == null
            || variable.geometryKind() != SpatialDomain.Kind.STRUCTURED_GRID
            || dataset.coordinateBindings().isEmpty()) {
            return null;
        }

        CoordinateBinding binding = findBindingById(dataset.coordinateBindings(), variable.basisId());
        if (binding == null) {
            binding = dataset.coordinateBindings().stream()
                .filter(candidate -> variable.dimensionNames().containsAll(candidate.horizontalDimensions()))
                .findFirst()
                .orElse(null);
        }
        return buildStructuredDomain(dataset, binding);
    }

    private void updateDatasetPanels(ParsedDataset dataset) {
        if (dataset == null) {
            return;
        }
        // 更新当前数据集名称标签。
        view.getDatasetLabel().setText(dataset.sourcePath().getFileName().toString());
        // 更新坐标变量标签。
        view.getCoordinateVariableLabel().setText("坐标："
            + Optional.ofNullable(activeCoordinateBinding == null ? dataset.xVariableName() : activeCoordinateBinding.xName()).orElse("-")
            + " / "
            + Optional.ofNullable(activeCoordinateBinding == null ? dataset.yVariableName() : activeCoordinateBinding.yName()).orElse("-"));
        // 更新连接变量标签。
        view.getConnectivityVariableLabel().setText("连接关系："
            + Optional.ofNullable(dataset.connectivityVariableName()).orElse("-"));
        // 拼接摘要信息文本。
        String summary = "文件：" + dataset.sourcePath().toAbsolutePath() + System.lineSeparator()
            + "坐标：" + Optional.ofNullable(activeCoordinateBinding == null ? dataset.xVariableName() : activeCoordinateBinding.xName()).orElse("-")
            + " / " + Optional.ofNullable(activeCoordinateBinding == null ? dataset.yVariableName() : activeCoordinateBinding.yName()).orElse("-") + System.lineSeparator()
            + "连接关系：" + Optional.ofNullable(dataset.connectivityVariableName()).orElse("-") + System.lineSeparator()
            + "几何信息：" + geometrySummary(dataset, activeSpatialDomain == null ? dataset.spatialDomain() : activeSpatialDomain) + System.lineSeparator()
            + System.lineSeparator()
            + "维度：" + System.lineSeparator()
            + dataset.dimensions().entrySet().stream()
                .map(entry -> "  - " + entry.getKey() + " = " + entry.getValue())
                .collect(Collectors.joining(System.lineSeparator()));

        // 更新摘要区域。
        view.getSummaryArea().setText(summary);
        // 更新全局属性区域。
        view.getAttributesArea().setText(dataset.globalAttributes().isEmpty()
            ? "没有全局属性。"
            : dataset.globalAttributes().entrySet().stream()
                .map(entry -> entry.getKey() + " = " + entry.getValue())
                .collect(Collectors.joining(System.lineSeparator())));
        // 更新警告区域。
        view.getWarningsArea().setText(dataset.warnings().isEmpty()
            ? "没有警告。"
            : String.join(System.lineSeparator(), dataset.warnings()));
    }

    private void updateDepthControls() {
        // 变量为空、不可绘制或不分层时，关闭层切换控件。
        if (currentVariable == null || !currentVariable.plottable() || !currentVariable.layered()) {
            view.getDepthSlider().setDisable(true);
            view.getDepthSlider().setValue(0);
            view.getLayerInfoLabel().setText("图层：单层");
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
            view.getVariableMetaLabel().setText("变量详情：-");
            return;
        }
        // 根据变量是否可绘制、是否分层以及空间位置生成说明文本。
        String mode = currentVariable.plottable()
            ? (currentVariable.layered() ? "分层" : "单层")
                + (currentVariable.geometryKind() == SpatialDomain.Kind.STRUCTURED_GRID
                    ? (currentVariable.cellCentered() ? "规则格网单元场" : "规则格网节点场")
                    : (currentVariable.elementCentered() ? "单元场" : "节点场"))
            : "仅信息";
        // 更新变量细节标签。
        view.getVariableMetaLabel().setText("变量详情："
            + currentVariable.presentableType()
            + " "
            + currentVariable.dimensionSummary()
            + " | "
            + mode);
    }

    /*
     * ========================================================================
     * 步骤1：刷新波场箭头控件状态
     * ========================================================================
     * 目标：
     *   1) 让界面只在数据集存在兼容的 wdir/wlen 时开放箭头开关
     * 操作要点：
     *   1) 无配对时强制取消勾选
     *   2) 有配对时仅解除禁用，不改用户勾选状态
     */
    private void updateWaveArrowControls() {
        // 1.1 根据当前数据集是否识别出波场变量配对决定控件可用性。
        boolean available = activeWavePair != null;

        // 1.2 没有波场变量配对时强制取消勾选，避免旧状态串到新数据集。
        if (!available) {
            view.getWaveArrowCheck().setSelected(false);
        }

        // 1.3 同步更新勾选框禁用状态。
        view.getWaveArrowCheck().setDisable(!available);
    }

    /*
     * ========================================================================
     * 步骤2：刷新流线控件状态
     * ========================================================================
     * 目标：
     *   1) 让界面只在数据集存在兼容 u/v 或 ua/va 时开放流线开关
     * 操作要点：
     *   1) 无配对时强制取消勾选并停止动画
     *   2) 有配对时仅解除禁用，不改用户勾选状态
     */
    private void updateFlowLineControls() {
        // 2.1 根据当前数据集是否识别出速度变量配对决定控件可用性。
        boolean available = activeVelocityPair != null;

        // 2.2 没有速度变量配对时强制取消勾选并停止动画。
        if (!available) {
            view.getFlowLineCheck().setSelected(false);
            stopFlowAnimation();
        }

        // 2.3 同步更新勾选框禁用状态。
        view.getFlowLineCheck().setDisable(!available);
    }

    /*
     * ========================================================================
     * 步骤3：刷新风羽控件状态
     * ========================================================================
     * 目标：
     *   1) 让界面只在数据集存在兼容风场变量配对时开放风羽开关
     * 操作要点：
     *   1) 无配对时强制取消勾选
     *   2) 有配对时仅解除禁用，不改用户勾选状态
     */
    private void updateWindBarbControls() {
        logger.info(() -> "开始刷新风羽控件状态, windAvailable=" + (activeWindPair != null));

        // 3.1 根据当前数据集是否识别出风场变量配对决定控件可用性。
        boolean available = activeWindPair != null;

        // 3.2 没有风场变量配对时强制取消勾选，避免旧状态串到新数据集。
        if (!available) {
            view.getWindBarbCheck().setSelected(false);
        }

        // 3.3 同步更新勾选框禁用状态。
        view.getWindBarbCheck().setDisable(!available);

        logger.info(() -> "风羽控件状态刷新完成, windAvailable=" + available);
    }

    private void renderCurrentSelection() {
        renderCurrentSelection(false);
    }

    private void renderCurrentSelection(boolean keepCurrentFrameVisible) {
        // 获取主画布对象。
        Canvas canvas = view.getRenderCanvas();
        // 尚未打开文件时显示默认提示。
        if (currentDataset == null) {
            renderPlaceholder("打开 NetCDF 文件开始。");
            return;
        }
        // 文件没有可用三角网时仅展示提示，不执行绘制。
        if (activeSpatialDomain == null) {
            renderPlaceholder("文件已打开，但不包含可绘制的空间域。");
            return;
        }
        // 未选择变量时提示用户先选择变量。
        if (currentVariable == null) {
            renderPlaceholder("请先从列表中选择变量。");
            return;
        }
        // 只读信息变量不能参与平面绘制。
        if (!currentVariable.plottable()) {
            renderPlaceholder("当前变量仅用于信息展示，不能绘制为二维平面标量场。");
            return;
        }

        try {
            // 单层变量固定读取第 0 层；分层变量从滑块读取当前层号。
            int layerIndex = currentVariable.layered() ? (int) Math.round(view.getDepthSlider().getValue()) : 0;
            // 读取当前层数值数组。
            double[] values = readLayerCached(currentDataset, currentVariable, layerIndex);
            // 计算当前层的自动范围。
            RangeStats computedRange = RenderMath.computeRange(values, currentVariable.fillValue());
            // 无有效值时直接显示提示。
            if (computedRange.empty()) {
                renderPlaceholder("当前图层没有有效值。");
                return;
            }
            // 根据自动或手动配置得到最终展示范围。
            RangeStats displayRange = resolveRange(computedRange);
            // 读取当前选中的颜色表。
            ColorMap colorMap = colorMaps.getOrDefault(view.getColorMapCombo().getValue(), ColorMaps.viridis());
            // 在当前状态下决定是否启用波场箭头叠加层。
            WaveVariablePair wavePair = view.getWaveArrowCheck().isSelected() ? activeWavePair : null;
            // 在当前状态下决定是否启用流线叠加层。
            VelocityVariablePair flowPair = view.getFlowLineCheck().isSelected() ? activeVelocityPair : null;
            // 在当前状态下决定是否启用风羽叠加层。
            WindVariablePair windPair = view.getWindBarbCheck().isSelected() ? activeWindPair : null;
            // 在当前状态下决定是否启用底图。
            BasemapLayer basemapLayer = selectedBasemapLayer();
            // 读取底图透明度。
            double basemapOpacity = view.getBasemapOpacitySlider().getValue();
            // 确保视口已适配当前网格范围。
            viewportState.ensureFitted(activeSpatialDomain, canvas.getWidth(), canvas.getHeight());
            // 生成新的渲染序号，供后台任务结果校验使用。
            long requestId = ++renderSequence;
            // 新一轮渲染开始时先清空旧的查询上下文，避免点击命中过期结果。
            latestRenderQueryContext = null;
            long inputSequence = ++renderInputSequence;
            WritableImage preservedBaseImage = latestBaseImage;
            WritableImage preservedCompositeImage = latestCompositeImage;
            ViewportState.Snapshot preservedCompositeSnapshot = latestCompositeSnapshot;
            WaveOverlayFrame preservedWaveOverlayFrame = latestWaveOverlayFrame;
            FlowOverlayFrame preservedFlowOverlayFrame = latestFlowOverlayFrame;
            WindOverlayFrame preservedWindOverlayFrame = latestWindOverlayFrame;
            if (!keepCurrentFrameVisible) {
                navigationRenderDelay.stop();
                navigationPreviewActive = false;
            }
            latestWaveOverlayFrame = null;
            latestFlowOverlayFrame = null;
            latestWindOverlayFrame = null;
            stopFlowAnimation();
            // 显示渲染中提示。
            view.getOverlayLabel().setText("正在渲染 " + currentVariable.name() + " ...");
            view.getOverlayLabel().setVisible(true);
            setStatus("正在渲染 " + currentVariable.name() + " ...");
            if (keepCurrentFrameVisible) {
                latestRenderQueryContext = navigationPreviewQueryContext;
                latestBaseImage = preservedBaseImage;
                latestCompositeImage = preservedCompositeImage;
                latestCompositeSnapshot = preservedCompositeSnapshot;
                latestWaveOverlayFrame = preservedWaveOverlayFrame;
                latestFlowOverlayFrame = preservedFlowOverlayFrame;
                latestWindOverlayFrame = preservedWindOverlayFrame;
                view.getOverlayLabel().setVisible(false);
            } else {
                latestBaseImage = null;
                latestCompositeImage = null;
                latestCompositeSnapshot = null;
            }
            pendingRenderInputSequence = inputSequence;
            pendingKeepCurrentFrameVisible = keepCurrentFrameVisible;
            // 在后台线程中执行真正的图像渲染。
            renderAsync(requestId, layerIndex, values, colorMap, displayRange, wavePair, flowPair, windPair, basemapLayer, basemapOpacity);
        } catch (Exception exception) {
            // 渲染准备阶段异常时，直接退回占位提示。
            renderPlaceholder("无法渲染当前变量：" + exception.getMessage());
        }
    }

    /*
     * ========================================================================
     * 步骤1：读取当前变量层数据
     * ========================================================================
     * 目标：
     *   1) 优先复用已缓存的变量层数组
     *   2) 缓存未命中时委托 NetCDF 解析器读取
     * 操作要点：
     *   1) 缓存键由数据源、变量名和层号组成
     *   2) 读取异常继续向上交给渲染准备逻辑处理
     */
    private double[] readLayerCached(ParsedDataset dataset, VariableInfo variableInfo, int layerIndex) throws IOException {
        logger.info(() -> "开始读取变量层数据, variable=" + variableInfo.name() + ", layerIndex=" + layerIndex);

        // 1.1 使用层数据缓存包装真实读取动作。
        double[] values = layerDataCache.getOrLoad(
            dataset,
            variableInfo,
            layerIndex,
            () -> parser.readLayer(dataset, variableInfo, layerIndex)
        );

        logger.info(() -> "变量层数据读取完成, variable=" + variableInfo.name() + ", valueCount=" + values.length);
        return values;
    }

    private void renderAsync(
        long requestId,
        int layerIndex,
        double[] values,
        ColorMap colorMap,
        RangeStats displayRange,
        WaveVariablePair wavePair,
        VelocityVariablePair flowPair,
        WindVariablePair windPair,
        BasemapLayer basemapLayer,
        double basemapOpacity
    ) {
        // 快照当前画布与状态，避免后台线程期间界面对象变化。
        Canvas canvas = view.getRenderCanvas();
        ParsedDataset dataset = currentDataset;
        VariableInfo variable = currentVariable;
        SpatialDomain spatialDomain = activeSpatialDomain;
        int width = Math.max(1, (int) Math.round(canvas.getWidth()));
        int height = Math.max(1, (int) Math.round(canvas.getHeight()));
        ViewportState.Snapshot snapshot = viewportState.snapshot();
        long inputSequence = pendingRenderInputSequence;
        boolean keepCurrentFrameVisible = pendingKeepCurrentFrameVisible;
        String basemapMessage = basemapLayer != null && !isGeographicDomain(spatialDomain)
            ? "已跳过底图：当前数据坐标不是经纬度。"
            : null;
        BasemapLayer renderBasemapLayer = basemapMessage == null ? basemapLayer : null;

        /*
         * ========================================================================
         * 步骤1：调度后台渲染任务
         * ========================================================================
         * 目标：
         *   1) 使用固定线程池复用渲染线程
         *   2) 将底图、波浪、海流、风场拆成并行子任务
         * 操作要点：
         *   1) 渲染任务仍由 JavaFX Task 承载回调
         *   2) 计算密集部分统一分发到共享计算线程池
         */
        Task<RenderFrame> renderTask = new Task<>() {
            @Override
            protected RenderFrame call() throws Exception {
                // 1.1 底图和各类叠加层并行构建，最后统一汇总结果。
                CompletableFuture<BufferedImage> baseImageFuture = CompletableFuture.supplyAsync(
                    () -> buildBaseImage(
                        spatialDomain,
                        dataset,
                        variable,
                        values,
                        colorMap,
                        displayRange,
                        snapshot,
                        width,
                        height,
                        renderBasemapLayer,
                        basemapOpacity
                    ),
                    renderComputeExecutor
                );
                CompletableFuture<OverlayBuildResult<WaveOverlayFrame>> waveFuture = scheduleWaveOverlayBuild(
                    dataset,
                    wavePair,
                    layerIndex,
                    snapshot
                );
                CompletableFuture<OverlayBuildResult<FlowOverlayFrame>> flowFuture = scheduleFlowOverlayBuild(
                    dataset,
                    flowPair,
                    layerIndex,
                    snapshot,
                    width,
                    height
                );
                CompletableFuture<OverlayBuildResult<WindOverlayFrame>> windFuture = scheduleWindOverlayBuild(
                    dataset,
                    windPair,
                    layerIndex,
                    snapshot,
                    width,
                    height
                );

                BufferedImage bufferedImage = baseImageFuture.join();
                OverlayBuildResult<WaveOverlayFrame> waveResult = waveFuture.join();
                OverlayBuildResult<FlowOverlayFrame> flowResult = flowFuture.join();
                OverlayBuildResult<WindOverlayFrame> windResult = windFuture.join();
                return new RenderFrame(
                    SwingFXUtils.toFXImage(bufferedImage, null),
                    waveResult.frame(),
                    flowResult.frame(),
                    windResult.frame(),
                    mergeOverlayMessages(basemapMessage, waveResult.message(), flowResult.message(), windResult.message())
                );
            }
        };

        // 渲染成功后回到界面线程刷新画布。
        renderTask.setOnSucceeded(event -> {
            // 如果当前结果已经过期，则直接丢弃。
            if (requestId != renderSequence
                || inputSequence != renderInputSequence
                || variable != currentVariable
                || dataset != currentDataset) {
                return;
            }
            RenderFrame frame = renderTask.getValue();
            if (frame == null) {
                return;
            }
            navigationPreviewActive = false;
            navigationPreviewQueryContext = null;
            latestBaseImage = frame.image();
            latestWaveOverlayFrame = frame.waveOverlayFrame();
            latestFlowOverlayFrame = frame.flowOverlayFrame();
            latestWindOverlayFrame = frame.windOverlayFrame();
            // 刷新右侧色条。
            view.getColorBarCanvas().render(colorMap, displayRange);
            // 缓存当前成功渲染的查询上下文，供点击单点查询复用。
            latestRenderQueryContext = new RenderQueryContext(
                dataset,
                spatialDomain,
                variable,
                layerIndex,
                values.clone(),
                variable.cellCentered(),
                variable.fillValue(),
                snapshot
            );
            // 用缓存底图和叠加层刷新主画布。
            drawLatestFrame(true);
            // 更新当前变量标签。
            view.getCurrentVariableLabel().setText("变量：" + variable.name() + " " + variable.dimensionSummary());
            // 更新范围标签。
            view.getRangeInfoLabel().setText("范围：" + format(displayRange.min()) + " 到 " + format(displayRange.max()));
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
            setStatus(frame.overlayMessage() == null || frame.overlayMessage().isBlank()
                ? "已渲染 " + variable.name()
                : frame.overlayMessage());
        });

        // 渲染失败时退回错误占位信息。
        renderTask.setOnFailed(event -> {
            if (requestId != renderSequence || inputSequence != renderInputSequence) {
                return;
            }
            Throwable error = renderTask.getException();
            if (keepCurrentFrameVisible && latestRenderQueryContext != null) {
                view.getOverlayLabel().setVisible(false);
                setStatus("无法刷新当前视图：" + (error == null ? "未知错误" : error.getMessage()));
                return;
            }
            stopFlowAnimation();
            navigationPreviewActive = false;
            navigationPreviewQueryContext = null;
            renderPlaceholder("无法渲染当前变量：" + (error == null ? "未知错误" : error.getMessage()));
        });

        // 1.2 复用固定线程池执行渲染任务，并清空过期排队任务。
        renderTaskExecutor.getQueue().clear();
        renderTaskExecutor.execute(renderTask);
    }

    /*
     * ========================================================================
     * 步骤2：构建标量底图
     * ========================================================================
     * 目标：
     *   1) 在后台线程中生成标量底图
     *   2) 规则格网场景复用共享计算线程池做并行栅格化
     * 操作要点：
     *   1) 三角网继续走原有三角形离屏渲染
     *   2) 规则格网改走像素缓冲并行写入
     */
    private BufferedImage buildBaseImage(
        SpatialDomain spatialDomain,
        ParsedDataset dataset,
        VariableInfo variable,
        double[] values,
        ColorMap colorMap,
        RangeStats displayRange,
        ViewportState.Snapshot snapshot,
        int width,
        int height,
        BasemapLayer basemapLayer,
        double basemapOpacity
    ) {
        logger.info(() -> "开始构建标量底图, variable=" + variable.name());

        BufferedImage basemapImage = basemapLayer == null
            ? null
            : basemapRenderer.render(width, height, snapshot, basemapLayer, basemapOpacity);
        BufferedImage scalarImage = spatialDomain.kind() == SpatialDomain.Kind.STRUCTURED_GRID
            ? structuredImageRenderer.render(
                width,
                height,
                (StructuredGridDomain) spatialDomain,
                values,
                colorMap,
                displayRange,
                snapshot,
                variable.cellCentered(),
                variable.fillValue(),
                basemapImage == null ? 0xFFF8FBFD : 0x00000000
            )
            : imageRenderer.render(
                width,
                height,
                dataset.mesh(),
                values,
                colorMap,
                displayRange,
                snapshot,
                variable.elementCentered(),
                variable.fillValue(),
                basemapImage == null ? Color.decode("#F8FBFD") : new Color(0, 0, 0, 0)
            );

        BufferedImage image = basemapImage == null ? scalarImage : composeBasemapAndScalar(width, height, basemapImage, scalarImage);

        logger.info(() -> "标量底图构建结束, variable=" + variable.name());
        return image;
    }

    /*
     * ========================================================================
     * 步骤3：组合底图与标量图
     * ========================================================================
     * 目标：
     *   1) 确保底图绘制在最下层
     *   2) 将透明背景的 NetCDF 标量图覆盖到底图上方
     */
    private BufferedImage composeBasemapAndScalar(
        int width,
        int height,
        BufferedImage basemapImage,
        BufferedImage scalarImage
    ) {
        logger.info("开始组合底图与标量图...");

        // 3.1 创建最终图像。
        BufferedImage composedImage = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = composedImage.createGraphics();
        try {
            // 3.2 先绘制底图，再绘制 NetCDF 标量图。
            graphics.drawImage(basemapImage, 0, 0, null);
            graphics.drawImage(scalarImage, 0, 0, null);
        } finally {
            // 3.3 释放绘图上下文。
            graphics.dispose();
        }

        logger.info("底图与标量图组合完成。");
        return composedImage;
    }

    /*
     * ========================================================================
     * 步骤3：调度波浪叠加层构建
     * ========================================================================
     * 目标：
     *   1) 根据当前是否启用波浪叠加决定是否提交任务
     * 操作要点：
     *   1) 无波浪配对时直接返回空结果
     *   2) 有配对时分发到共享计算线程池
     */
    private CompletableFuture<OverlayBuildResult<WaveOverlayFrame>> scheduleWaveOverlayBuild(
        ParsedDataset dataset,
        WaveVariablePair wavePair,
        int layerIndex,
        ViewportState.Snapshot snapshot
    ) {
        if (wavePair == null) {
            return CompletableFuture.completedFuture(new OverlayBuildResult<>(null, null));
        }
        return CompletableFuture.supplyAsync(
            () -> buildWaveOverlayFrame(dataset, wavePair, layerIndex, snapshot),
            renderComputeExecutor
        );
    }

    /*
     * ========================================================================
     * 步骤4：调度海流叠加层构建
     * ========================================================================
     * 目标：
     *   1) 根据当前是否启用海流叠加决定是否提交任务
     * 操作要点：
     *   1) 无速度变量配对时直接返回空结果
     *   2) 有配对时分发到共享计算线程池
     */
    private CompletableFuture<OverlayBuildResult<FlowOverlayFrame>> scheduleFlowOverlayBuild(
        ParsedDataset dataset,
        VelocityVariablePair flowPair,
        int layerIndex,
        ViewportState.Snapshot snapshot,
        int width,
        int height
    ) {
        if (flowPair == null) {
            return CompletableFuture.completedFuture(new OverlayBuildResult<>(null, null));
        }
        return CompletableFuture.supplyAsync(
            () -> buildFlowOverlayFrame(dataset, flowPair, layerIndex, snapshot, width, height),
            renderComputeExecutor
        );
    }

    /*
     * ========================================================================
     * 步骤5：调度风场叠加层构建
     * ========================================================================
     * 目标：
     *   1) 根据当前是否启用风场叠加决定是否提交任务
     * 操作要点：
     *   1) 无风场变量配对时直接返回空结果
     *   2) 有配对时分发到共享计算线程池
     */
    private CompletableFuture<OverlayBuildResult<WindOverlayFrame>> scheduleWindOverlayBuild(
        ParsedDataset dataset,
        WindVariablePair windPair,
        int layerIndex,
        ViewportState.Snapshot snapshot,
        int width,
        int height
    ) {
        if (windPair == null) {
            return CompletableFuture.completedFuture(new OverlayBuildResult<>(null, null));
        }
        return CompletableFuture.supplyAsync(
            () -> buildWindOverlayFrame(dataset, windPair, layerIndex, snapshot, width, height),
            renderComputeExecutor
        );
    }

    /*
     * ========================================================================
     * 步骤6：构建波浪叠加层
     * ========================================================================
     * 目标：
     *   1) 读取波浪变量层并构造缓存帧
     * 操作要点：
     *   1) 兼容极坐标波浪和向量波浪两种模式
     *   2) 出错时不打断整次渲染，只返回提示消息
     */
    private OverlayBuildResult<WaveOverlayFrame> buildWaveOverlayFrame(
        ParsedDataset dataset,
        WaveVariablePair wavePair,
        int layerIndex,
        ViewportState.Snapshot snapshot
    ) {
        logger.info(() -> "开始构建波浪叠加层, directionVariable=" + wavePair.directionVariable().name());

        try {
            int waveLayerIndex = wavePair.resolveLayerIndex(layerIndex);
            double[] directionValues = readLayerCached(dataset, wavePair.directionVariable(), waveLayerIndex);
            double[] wavelengthValues = readLayerCached(dataset, wavePair.wavelengthVariable(), waveLayerIndex);
            double[] waveHeightValues = wavePair.optionalWaveHeightVariable().isPresent()
                ? readLayerCached(dataset, wavePair.optionalWaveHeightVariable().orElseThrow(), waveLayerIndex)
                : null;
            RangeStats wavelengthRange = wavePair.vectorMode()
                ? (wavePair.optionalWaveHeightVariable().isPresent()
                    ? RenderMath.computeRange(
                        waveHeightValues,
                        wavePair.optionalWaveHeightVariable().orElseThrow().fillValue()
                    )
                    : null)
                : RenderMath.computeRange(
                    wavelengthValues,
                    wavePair.wavelengthVariable().fillValue()
                );
            if ((wavelengthRange == null || wavelengthRange.empty()) && !wavePair.vectorMode()) {
                logger.info("波浪叠加层构建结束, framePresent=false");
                return new OverlayBuildResult<>(null, null);
            }

            WaveOverlayFrame frame = new WaveOverlayFrame(
                wavePair,
                waveLayerIndex,
                directionValues,
                wavelengthValues,
                waveHeightValues,
                wavelengthRange,
                wavePair.directionVariable().geometryKind() == SpatialDomain.Kind.STRUCTURED_GRID
                    ? resolveStructuredDomainForVariable(dataset, wavePair.directionVariable())
                    : null,
                wavePair.wavelengthVariable().geometryKind() == SpatialDomain.Kind.STRUCTURED_GRID
                    ? resolveStructuredDomainForVariable(dataset, wavePair.wavelengthVariable())
                    : null,
                wavePair.optionalWaveHeightVariable().isPresent()
                    ? resolveStructuredDomainForVariable(dataset, wavePair.optionalWaveHeightVariable().orElseThrow())
                    : null,
                snapshot
            );
            logger.info("波浪叠加层构建结束, framePresent=true");
            return new OverlayBuildResult<>(frame, null);
        } catch (Exception exception) {
            logger.info(() -> "波浪叠加层构建结束, framePresent=false, reason=" + exception.getMessage());
            return new OverlayBuildResult<>(null, "已跳过波浪箭头叠加：" + exception.getMessage());
        }
    }

    /*
     * ========================================================================
     * 步骤7：构建海流叠加层
     * ========================================================================
     * 目标：
     *   1) 读取速度变量层并生成流线缓存帧
     * 操作要点：
     *   1) 三角网和规则格网共用统一入口
     *   2) 出错时不打断整次渲染，只返回提示消息
     */
    private OverlayBuildResult<FlowOverlayFrame> buildFlowOverlayFrame(
        ParsedDataset dataset,
        VelocityVariablePair flowPair,
        int layerIndex,
        ViewportState.Snapshot snapshot,
        int width,
        int height
    ) {
        logger.info(() -> "开始构建海流叠加层, eastwardVariable=" + flowPair.eastwardVariable().name());

        try {
            int flowLayerIndex = flowPair.resolveLayerIndex(layerIndex);
            double[] uValues = readLayerCached(dataset, flowPair.eastwardVariable(), flowLayerIndex);
            double[] vValues = readLayerCached(dataset, flowPair.northwardVariable(), flowLayerIndex);
            List<FlowLineGenerator.FlowLine> lines = flowPair.eastwardVariable().geometryKind() == SpatialDomain.Kind.STRUCTURED_GRID
                ? flowLineGenerator.generateStructured(
                    resolveStructuredDomainForVariable(dataset, flowPair.eastwardVariable()),
                    resolveStructuredDomainForVariable(dataset, flowPair.northwardVariable()),
                    uValues,
                    vValues,
                    snapshot,
                    width,
                    height,
                    flowPair.eastwardVariable().cellCentered(),
                    flowPair.northwardVariable().cellCentered(),
                    flowPair.eastwardVariable().fillValue(),
                    flowPair.northwardVariable().fillValue(),
                    flowLayerIndex
                )
                : flowLineGenerator.generate(
                    dataset.mesh(),
                    uValues,
                    vValues,
                    snapshot,
                    width,
                    height,
                    flowPair.elementCentered(),
                    flowPair.eastwardVariable().fillValue(),
                    flowPair.northwardVariable().fillValue(),
                    flowLayerIndex
                );
            if (lines.isEmpty()) {
                logger.info("海流叠加层构建结束, framePresent=false");
                return new OverlayBuildResult<>(null, null);
            }

            FlowOverlayFrame frame = new FlowOverlayFrame(
                flowPair,
                flowLayerIndex,
                lines,
                snapshot
            );
            logger.info("海流叠加层构建结束, framePresent=true");
            return new OverlayBuildResult<>(frame, null);
        } catch (Exception exception) {
            logger.info(() -> "海流叠加层构建结束, framePresent=false, reason=" + exception.getMessage());
            return new OverlayBuildResult<>(null, "已跳过海流流线叠加：" + exception.getMessage());
        }
    }

    /*
     * ========================================================================
     * 步骤8：构建风场叠加层
     * ========================================================================
     * 目标：
     *   1) 读取风场变量层并生成风羽缓存帧
     * 操作要点：
     *   1) 三角网和规则格网共用统一入口
     *   2) 出错时不打断整次渲染，只返回提示消息
     */
    private OverlayBuildResult<WindOverlayFrame> buildWindOverlayFrame(
        ParsedDataset dataset,
        WindVariablePair windPair,
        int layerIndex,
        ViewportState.Snapshot snapshot,
        int width,
        int height
    ) {
        logger.info(() -> "开始构建风场叠加层, eastwardVariable=" + windPair.eastwardVariable().name());

        try {
            int windLayerIndex = windPair.resolveLayerIndex(layerIndex);
            double[] uValues = readLayerCached(dataset, windPair.eastwardVariable(), windLayerIndex);
            double[] vValues = readLayerCached(dataset, windPair.northwardVariable(), windLayerIndex);
            List<WindBarbOverlayRenderer.WindBarbGlyph> glyphs = windPair.eastwardVariable().geometryKind() == SpatialDomain.Kind.STRUCTURED_GRID
                ? windBarbOverlayRenderer.sampleStructuredBarbs(
                    resolveStructuredDomainForVariable(dataset, windPair.eastwardVariable()),
                    resolveStructuredDomainForVariable(dataset, windPair.northwardVariable()),
                    uValues,
                    vValues,
                    snapshot,
                    width,
                    height,
                    windPair.eastwardVariable().cellCentered(),
                    windPair.northwardVariable().cellCentered(),
                    windPair.eastwardVariable().fillValue(),
                    windPair.northwardVariable().fillValue(),
                    windLayerIndex
                )
                : windBarbOverlayRenderer.sampleBarbs(
                    dataset.mesh(),
                    uValues,
                    vValues,
                    snapshot,
                    width,
                    height,
                    windPair.elementCentered(),
                    windPair.eastwardVariable().fillValue(),
                    windPair.northwardVariable().fillValue(),
                    windLayerIndex
                );
            if (glyphs.isEmpty()) {
                logger.info("风场叠加层构建结束, framePresent=false");
                return new OverlayBuildResult<>(null, null);
            }

            WindOverlayFrame frame = new WindOverlayFrame(
                windPair,
                windLayerIndex,
                glyphs
            );
            logger.info("风场叠加层构建结束, framePresent=true");
            return new OverlayBuildResult<>(frame, null);
        } catch (Exception exception) {
            logger.info(() -> "风场叠加层构建结束, framePresent=false, reason=" + exception.getMessage());
            return new OverlayBuildResult<>(null, "已跳过风场风羽叠加：" + exception.getMessage());
        }
    }

    /*
     * ========================================================================
     * 步骤9：合并叠加层提示消息
     * ========================================================================
     * 目标：
     *   1) 将多个叠加层异常提示收敛成一条状态栏消息
     * 操作要点：
     *   1) 过滤空消息
     *   2) 用分号连接多条提示
     */
    private String mergeOverlayMessages(String... messages) {
        return Arrays.stream(messages)
            .filter(message -> message != null && !message.isBlank())
            .collect(Collectors.joining("; "));
    }

    /*
     * ========================================================================
     * 步骤3：使用缓存底图重绘当前视图
     * ========================================================================
     * 目标：
     *   1) 用最近一次成功渲染的底图和叠加层快速重绘
     *   2) 让流线动画只重绘亮带，不重复读 nc 和离屏渲染
     * 操作要点：
     *   1) 先画底图
     *   2) 再按当前勾选状态补流线、波场箭头和海岸线
     */
    private void drawLatestFrame() {
        drawLatestFrame(false);
    }

    private void drawLatestFrame(boolean refreshCompositeCache) {
        // 3.1 没有缓存底图或查询上下文时直接返回。
        if (latestBaseImage == null || latestRenderQueryContext == null) {
            return;
        }

        // 3.2 先清空画布并绘制底图。
        Canvas canvas = view.getRenderCanvas();
        canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        canvas.getGraphicsContext2D().drawImage(latestBaseImage, 0, 0, canvas.getWidth(), canvas.getHeight());

        // 3.3 再按当前勾选状态绘制流线亮带、本体、波场箭头和海岸线。
        if (view.getFlowLineCheck().isSelected() && latestFlowOverlayFrame != null) {
            flowLineOverlayRenderer.render(
                canvas.getGraphicsContext2D(),
                latestFlowOverlayFrame.lines(),
                flowAnimationPhase
            );
            startFlowAnimation();
        } else {
            stopFlowAnimation();
        }

        if (view.getWaveArrowCheck().isSelected() && latestWaveOverlayFrame != null) {
            if (latestWaveOverlayFrame.pair().vectorMode()
                && latestWaveOverlayFrame.directionDomain() != null
                && latestWaveOverlayFrame.wavelengthDomain() != null) {
                waveArrowOverlayRenderer.renderStructuredVector(
                    canvas.getGraphicsContext2D(),
                    latestWaveOverlayFrame.directionDomain(),
                    latestWaveOverlayFrame.wavelengthDomain(),
                    latestWaveOverlayFrame.waveHeightDomain(),
                    latestWaveOverlayFrame.directionValues(),
                    latestWaveOverlayFrame.wavelengthValues(),
                    latestWaveOverlayFrame.waveHeightValues(),
                    latestWaveOverlayFrame.snapshot(),
                    Math.max(1, (int) Math.round(canvas.getWidth())),
                    Math.max(1, (int) Math.round(canvas.getHeight())),
                    latestWaveOverlayFrame.pair().directionVariable().cellCentered(),
                    latestWaveOverlayFrame.pair().wavelengthVariable().cellCentered(),
                    latestWaveOverlayFrame.waveHeightDomain() != null
                        && latestWaveOverlayFrame.pair().optionalWaveHeightVariable().isPresent()
                        && latestWaveOverlayFrame.pair().optionalWaveHeightVariable().orElseThrow().cellCentered(),
                    latestWaveOverlayFrame.pair().directionVariable().fillValue(),
                    latestWaveOverlayFrame.pair().wavelengthVariable().fillValue(),
                    latestWaveOverlayFrame.pair().optionalWaveHeightVariable().map(VariableInfo::fillValue).orElse(null),
                    latestWaveOverlayFrame.layerIndex(),
                    latestWaveOverlayFrame.wavelengthRange()
                );
            } else if (latestWaveOverlayFrame.directionDomain() != null && latestWaveOverlayFrame.wavelengthDomain() != null) {
                waveArrowOverlayRenderer.renderStructured(
                    canvas.getGraphicsContext2D(),
                    latestWaveOverlayFrame.directionDomain(),
                    latestWaveOverlayFrame.wavelengthDomain(),
                    latestWaveOverlayFrame.directionValues(),
                    latestWaveOverlayFrame.wavelengthValues(),
                    latestWaveOverlayFrame.snapshot(),
                    Math.max(1, (int) Math.round(canvas.getWidth())),
                    Math.max(1, (int) Math.round(canvas.getHeight())),
                    latestWaveOverlayFrame.pair().directionVariable().cellCentered(),
                    latestWaveOverlayFrame.pair().wavelengthVariable().cellCentered(),
                    latestWaveOverlayFrame.pair().directionVariable().fillValue(),
                    latestWaveOverlayFrame.pair().wavelengthVariable().fillValue(),
                    latestWaveOverlayFrame.layerIndex(),
                    latestWaveOverlayFrame.wavelengthRange()
                );
            } else {
                waveArrowOverlayRenderer.render(
                    canvas.getGraphicsContext2D(),
                    latestRenderQueryContext.dataset().mesh(),
                    latestWaveOverlayFrame.directionValues(),
                    latestWaveOverlayFrame.wavelengthValues(),
                    latestWaveOverlayFrame.snapshot(),
                    Math.max(1, (int) Math.round(canvas.getWidth())),
                    Math.max(1, (int) Math.round(canvas.getHeight())),
                    latestWaveOverlayFrame.pair().elementCentered(),
                    latestWaveOverlayFrame.pair().directionVariable().fillValue(),
                    latestWaveOverlayFrame.pair().wavelengthVariable().fillValue(),
                    latestWaveOverlayFrame.layerIndex(),
                    latestWaveOverlayFrame.wavelengthRange()
                );
            }
        }

        if (view.getWindBarbCheck().isSelected() && latestWindOverlayFrame != null) {
            windBarbOverlayRenderer.render(
                canvas.getGraphicsContext2D(),
                latestWindOverlayFrame.glyphs()
            );
        }

        coastlineOverlayRenderer.render(
            canvas.getGraphicsContext2D(),
            currentOverlay,
            latestRenderQueryContext.snapshot()
        );
        if (refreshCompositeCache) {
            cacheLatestCompositeFrame();
        }
    }

    private boolean drawViewportPreview() {
        logger.info("Start drawing viewport preview");

        if (latestCompositeImage == null || latestCompositeSnapshot == null || latestRenderQueryContext == null) {
            logger.info("Finish drawing viewport preview, drawn=false");
            return false;
        }

        ViewportState.Snapshot currentSnapshot = latestRenderQueryContext.snapshot();
        double sourceScale = latestCompositeSnapshot.scale();
        if (!Double.isFinite(sourceScale) || sourceScale <= 0.0 || !Double.isFinite(currentSnapshot.scale())) {
            logger.info("Finish drawing viewport preview, drawn=false");
            return false;
        }

        double scaleRatio = currentSnapshot.scale() / sourceScale;
        double translateX = currentSnapshot.translateX() - scaleRatio * latestCompositeSnapshot.translateX();
        double translateY = currentSnapshot.translateY() - scaleRatio * latestCompositeSnapshot.translateY();
        if (!Double.isFinite(scaleRatio) || !Double.isFinite(translateX) || !Double.isFinite(translateY) || scaleRatio <= 0.0) {
            logger.info("Finish drawing viewport preview, drawn=false");
            return false;
        }

        Canvas canvas = view.getRenderCanvas();
        canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        canvas.getGraphicsContext2D().save();
        canvas.getGraphicsContext2D().translate(translateX, translateY);
        canvas.getGraphicsContext2D().scale(scaleRatio, scaleRatio);
        canvas.getGraphicsContext2D().drawImage(latestCompositeImage, 0, 0);
        canvas.getGraphicsContext2D().restore();
        view.getOverlayLabel().setVisible(false);

        logger.info("Finish drawing viewport preview, drawn=true");
        return true;
    }

    private void cacheLatestCompositeFrame() {
        logger.info("Start caching composite frame");

        if (latestRenderQueryContext == null) {
            latestCompositeImage = null;
            latestCompositeSnapshot = null;
            logger.info("Finish caching composite frame, cached=false");
            return;
        }

        Canvas canvas = view.getRenderCanvas();
        int width = Math.max(1, (int) Math.round(canvas.getWidth()));
        int height = Math.max(1, (int) Math.round(canvas.getHeight()));
        if (latestCompositeImage == null
            || Math.round(latestCompositeImage.getWidth()) != width
            || Math.round(latestCompositeImage.getHeight()) != height) {
            latestCompositeImage = new WritableImage(width, height);
        }
        latestCompositeImage = canvas.snapshot(new SnapshotParameters(), latestCompositeImage);
        latestCompositeSnapshot = latestRenderQueryContext.snapshot();

        logger.info("Finish caching composite frame, cached=true");
    }

    /*
     * ========================================================================
     * 步骤4：启动流线亮带动画
     * ========================================================================
     * 目标：
     *   1) 让流线亮带沿线循环移动
     * 操作要点：
     *   1) 时间线只更新时间相位
     *   2) 每一帧复用缓存底图和流线
     */
    private void startFlowAnimation() {
        // 4.1 只有流线叠加开启且当前有流线缓存时才启动动画。
        if (!view.getFlowLineCheck().isSelected() || latestFlowOverlayFrame == null) {
            return;
        }

        // 4.2 首次启动时构造时间线，后续复用同一对象。
        if (flowAnimationTimeline == null) {
            flowAnimationTimeline = new Timeline(new KeyFrame(Duration.millis(80), event -> {
                flowAnimationPhase += 0.06;
                drawLatestFrame(false);
            }));
            flowAnimationTimeline.setCycleCount(Animation.INDEFINITE);
        }

        // 4.3 若当前尚未运行则启动动画。
        if (flowAnimationTimeline.getStatus() != Animation.Status.RUNNING) {
            flowAnimationTimeline.play();
        }
    }

    /*
     * ========================================================================
     * 步骤5：停止流线亮带动画
     * ========================================================================
     * 目标：
     *   1) 在关闭流线叠加、切数据集或渲染失败时及时停表
     * 操作要点：
     *   1) 仅停止时间线，不清空底图缓存
     */
    private void stopFlowAnimation() {
        // 5.1 已创建时间线时停止播放。
        if (flowAnimationTimeline != null) {
            flowAnimationTimeline.stop();
        }
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
            view.getLayerInfoLabel().setText("图层：单层");
            return;
        }
        // 先显示层号与总层数。
        String label = "图层：" + (layerIndex + 1) + " / " + currentVariable.layerCount();
        // 如果存在实际层值数组，则附加显示对应的层值。
        double[] axisValues = currentDataset.axisValues(currentVariable.layerDimensionName()).orElse(null);
        if (axisValues != null && layerIndex < axisValues.length) {
            label += "（值=" + format(axisValues[layerIndex]) + "）";
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
        // 占位状态下清空最近一次可查询渲染上下文。
        latestRenderQueryContext = null;
        navigationPreviewQueryContext = null;
        latestBaseImage = null;
        latestCompositeImage = null;
        latestCompositeSnapshot = null;
        latestWaveOverlayFrame = null;
        latestFlowOverlayFrame = null;
        latestWindOverlayFrame = null;
        navigationPreviewActive = false;
        navigationRenderDelay.stop();
        stopFlowAnimation();
        // 占位状态下不允许导出。
        view.getExportButton().setDisable(true);
        view.getExportPngMenuItem().setDisable(true);
        // 同步更新窗口标题。
        updateWindowTitle();
    }

    private void queryAtScreenPoint(double screenX, double screenY) {
        // 没有完成过可视化渲染时，不允许单点查询。
        RenderQueryContext context = latestRenderQueryContext;
        if (context == null) {
            setStatus("渲染完成后才能使用单点查询。");
            return;
        }

        // 标准格网走规则网格单点查询分支。
        if (context.spatialDomain().kind() == SpatialDomain.Kind.STRUCTURED_GRID) {
            StructuredPointQuery.Result result = StructuredPointQuery.query(
                (StructuredGridDomain) context.spatialDomain(),
                context.values(),
                context.snapshot(),
                screenX,
                screenY,
                context.cellCentered(),
                context.fillValue(),
                context.layerIndex()
            );
            if (!result.hit() || result.reason() == StructuredPointQuery.Reason.NO_HIT || result.reason() == StructuredPointQuery.Reason.UNSUPPORTED) {
                setStatus("点击位置没有规则格网值。");
                return;
            }
            if (!result.hasValue()) {
                setStatus("点击到的规则格网样本没有有效值。");
                return;
            }

            StringBuilder text = new StringBuilder()
                .append("查询 ")
                .append(context.variable().name());
            if (context.variable().layered()) {
                text.append(" 第 ").append(result.layerIndex() + 1).append(" 层");
            }
            text.append("，位置 (")
                .append(format(result.worldX()))
                .append(", ")
                .append(format(result.worldY()))
                .append(")：")
                .append(result.sampleType(context.cellCentered()))
                .append(" [行=")
                .append(result.rowIndex())
                .append(", 列=")
                .append(result.columnIndex())
                .append("]，值=")
                .append(format(result.value()));
            setStatus(text.toString());
            return;
        }

        // 使用最近一次成功渲染的数据上下文执行点查询。
        MeshPointQuery.Result result = MeshPointQuery.query(
            context.dataset().mesh(),
            context.values(),
            context.snapshot(),
            screenX,
            screenY,
            context.cellCentered(),
            context.fillValue(),
            context.layerIndex()
        );

        // 未命中网格时给出明确提示。
        if (!result.hit() || result.reason() == MeshPointQuery.Reason.NO_HIT) {
            setStatus("点击位置没有三角网值。");
            return;
        }
        // 命中但当前值不可用时给出明确提示。
        if (!result.hasValue()) {
            setStatus("点击到的三角形没有有效值。");
            return;
        }

        // 构造单点查询结果文本。
        StringBuilder text = new StringBuilder()
            .append("查询 ")
            .append(context.variable().name());
        if (context.variable().layered()) {
            text.append(" 第 ").append(result.layerIndex() + 1).append(" 层");
        }
        text.append("，位置 (")
            .append(format(result.worldX()))
            .append(", ")
            .append(format(result.worldY()))
            .append(")：三角形 #")
            .append(result.triangleIndex())
            .append("，值=")
            .append(format(result.value()));
        setStatus(text.toString());
    }

    private void exportPng() {
        try {
            // 弹出保存对话框让用户选择输出位置。
            Path path = chooseSavePngFile();
            if (path == null) {
                setStatus("已取消导出。");
                return;
            }
            // 更新最近目录，方便下次导出。
            lastDirectory = path.toAbsolutePath().getParent();
            // 对当前可视化区域做快照。
            WritableImage image = view.getVisualizationBox().snapshot(new SnapshotParameters(), null);
            // 使用专用导出工具写出 PNG。
            PngExportSupport.writePng(image, path);
            setStatus("已导出 " + path.getFileName());
        } catch (IOException exception) {
            // 导出写文件失败时显示详细错误。
            showError("导出失败", "无法导出 PNG：" + exception.getMessage());
        } catch (Exception exception) {
            // 保存对话框或其他异常统一提示。
            showError("导出失败", "无法打开保存对话框：" + exception.getMessage());
        }
    }

    private void redrawCurrentView() {
        if (navigationPreviewActive && drawViewportPreview()) {
            return;
        }
        if (latestBaseImage != null && latestRenderQueryContext != null) {
            drawLatestFrame(true);
            return;
        }
        if (currentDataset != null && currentVariable != null && currentVariable.plottable()) {
            renderCurrentSelection();
        }
    }

    /*
     * ========================================================================
     * 步骤10：关闭渲染线程池
     * ========================================================================
     * 目标：
     *   1) 在窗口关闭时释放渲染相关线程资源
     * 操作要点：
     *   1) 串行调度池和计算池一起关闭
     *   2) 仅做幂等关闭，不抛额外异常
     */
    private void shutdownRenderExecutors() {
        logger.info("开始关闭渲染线程池...");
        navigationRenderDelay.stop();
        renderTaskExecutor.shutdownNow();
        renderComputeExecutor.shutdownNow();
        logger.info("渲染线程池关闭完成");
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
        alert.setTitle("关于 " + AppMetadata.APP_NAME);
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
            + "版本：" + AppMetadata.VERSION
            + System.lineSeparator()
            + "作者：" + AppMetadata.AUTHOR_NAME
            + System.lineSeparator()
            + "邮箱：" + AppMetadata.AUTHOR_EMAIL;
    }

    private String format(double value) {
        // 所有数值显示统一保留四位小数。
        return String.format(Locale.ROOT, "%.4f", value);
    }

    /*
     * ========================================================================
     * 步骤10：解析底图缓存目录
     * ========================================================================
     * 目标：
     *   1) 将在线瓦片缓存到用户目录
     *   2) 避免缓存文件进入项目仓库
     */
    private static Path defaultBasemapCacheDirectory() {
        logger.info("开始解析底图缓存目录...");

        // 10.1 使用用户主目录下的应用缓存目录。
        Path directory = Paths.get(
            System.getProperty("user.home", "."),
            ".netcdf-viewer",
            "tile-cache"
        );

        logger.info(() -> "底图缓存目录解析完成, directory=" + directory);
        return directory;
    }

    /*
     * ========================================================================
     * 步骤11：创建命名线程工厂
     * ========================================================================
     * 目标：
     *   1) 为渲染线程池生成可识别的守护线程
     * 操作要点：
     *   1) 统一线程名前缀
     *   2) 全部线程设置为 daemon
     */
    private static ThreadFactory namedThreadFactory(String prefix) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + System.nanoTime());
            thread.setDaemon(true);
            return thread;
        };
    }

    private Path chooseSavePngFile() {
        // 调用 Swing 对话框选择保存路径。
        return SwingFileDialogs.chooseSavePngFile(lastDirectory);
    }

    private void updateWindowTitle() {
        // 从应用名称开始构造标题。
        StringBuilder title = new StringBuilder(AppMetadata.APP_NAME);
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
                title.append(" [第 ").append(layerIndex + 1).append("/").append(currentVariable.layerCount()).append(" 层]");
            }
        }
        stage.setTitle(title.toString());
    }

    private String geometrySummary(ParsedDataset dataset, SpatialDomain spatialDomain) {
        if (spatialDomain == null) {
            return "不可用";
        }
        if (spatialDomain.kind() == SpatialDomain.Kind.TRIANGLE_MESH && dataset.hasMesh()) {
            return dataset.mesh().nodeCount() + " 个节点，" + dataset.mesh().triangleCount() + " 个三角形";
        }
        if (spatialDomain instanceof StructuredGridDomain structuredGridDomain) {
            return "规则格网 " + structuredGridDomain.grid().width() + " x " + structuredGridDomain.grid().height();
        }
        return spatialDomain.kind().name();
    }

    private record RenderQueryContext(
        ParsedDataset dataset,
        SpatialDomain spatialDomain,
        VariableInfo variable,
        int layerIndex,
        double[] values,
        boolean cellCentered,
        Double fillValue,
        ViewportState.Snapshot snapshot
    ) {
    }

    private record RenderFrame(
        WritableImage image,
        WaveOverlayFrame waveOverlayFrame,
        FlowOverlayFrame flowOverlayFrame,
        WindOverlayFrame windOverlayFrame,
        String overlayMessage
    ) {
    }

    private record WaveOverlayFrame(
        WaveVariablePair pair,
        int layerIndex,
        double[] directionValues,
        double[] wavelengthValues,
        double[] waveHeightValues,
        RangeStats wavelengthRange,
        StructuredGridDomain directionDomain,
        StructuredGridDomain wavelengthDomain,
        StructuredGridDomain waveHeightDomain,
        ViewportState.Snapshot snapshot
    ) {
    }

    private record FlowOverlayFrame(
        VelocityVariablePair pair,
        int layerIndex,
        List<FlowLineGenerator.FlowLine> lines,
        ViewportState.Snapshot snapshot
    ) {
    }

    private record WindOverlayFrame(
        WindVariablePair pair,
        int layerIndex,
        List<WindBarbOverlayRenderer.WindBarbGlyph> glyphs
    ) {
    }

    private record OverlayBuildResult<T>(T frame, String message) {
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
            String marker = item.plottable() ? (item.layered() ? "[分层] " : "[平面] ") : "[信息] ";
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
