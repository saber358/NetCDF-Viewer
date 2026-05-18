package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.AppMetadata;
import com.example.netcdfviewer.model.VariableInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * 主界面视图类。
 * 该类专门负责创建和组织界面控件，不直接处理业务逻辑。
 */
public final class MainView extends BorderPane {
    // 帮助菜单。
    private final Menu helpMenu = new Menu("帮助");
    // 打开文件菜单项。
    private final MenuItem openMenuItem = new MenuItem("打开...");
    // 加载海岸线菜单项。
    private final MenuItem loadCoastlineMenuItem = new MenuItem("加载海岸线...");
    // 使用内置海岸线菜单项。
    private final MenuItem useBuiltInCoastlineMenuItem = new MenuItem("使用内置海岸线");
    // 清空海岸线菜单项。
    private final MenuItem clearCoastlineMenuItem = new MenuItem("清除海岸线");
    // 导出 PNG 菜单项。
    private final MenuItem exportPngMenuItem = new MenuItem("导出 PNG...");
    // 退出菜单项。
    private final MenuItem exitMenuItem = new MenuItem("退出");
    // 关于菜单项。
    private final MenuItem aboutMenuItem = new MenuItem("关于");
    // 打开文件按钮。
    private final Button openButton = new Button("打开");
    // 导出按钮。
    private final Button exportButton = new Button("导出 PNG");
    // 重置视图按钮。
    private final Button resetViewButton = new Button("重置视图");
    // 手动触发渲染按钮。
    private final Button visualizeButton = new Button("渲染");
    // 手动应用范围按钮。
    private final Button applyRangeButton = new Button("应用范围");
    // 删除当前选中数据集按钮。
    private final Button removeDatasetButton = new Button("删除");
    // 已加载数据集列表。
    private final ListView<LoadedDatasetItem> datasetList = new ListView<>();
    // 变量列表。
    private final ListView<VariableInfo> variableList = new ListView<>();
    // 数据摘要区域。
    private final TextArea summaryArea = new TextArea();
    // 全局属性区域。
    private final TextArea attributesArea = new TextArea();
    // 警告信息区域。
    private final TextArea warningsArea = new TextArea();
    // 主图区域中央的覆盖提示文字。
    private final Label overlayLabel = new Label("打开 NetCDF 文件开始。");
    // 当前文件名标签。
    private final Label datasetLabel = new Label("未加载文件");
    // 当前变量标签。
    private final Label currentVariableLabel = new Label("变量：-");
    // 坐标变量标签。
    private final Label coordinateVariableLabel = new Label("坐标：-");
    // 标准格网坐标选择标签。
    private final Label coordinateSelectionLabel = new Label("规则格网坐标");
    // 连接变量标签。
    private final Label connectivityVariableLabel = new Label("连接关系：-");
    // 变量细节标签。
    private final Label variableMetaLabel = new Label("变量详情：-");
    // 图层信息标签。
    private final Label layerInfoLabel = new Label("图层：-");
    // 数值范围标签。
    private final Label rangeInfoLabel = new Label("范围：-");
    // 状态栏主状态标签。
    private final Label statusLabel = new Label("就绪");
    // 状态栏作者信息标签。
    private final Label authorLabel = new Label(AppMetadata.AUTHOR_LABEL);
    // 深度层滑块。
    private final Slider depthSlider = new Slider(0, 0, 0);
    // 颜色表下拉框。
    private final ComboBox<String> colorMapCombo = new ComboBox<>();
    // 结构化网格 X 轴选择框。
    private final ComboBox<String> coordinateXCombo = new ComboBox<>();
    // 结构化网格 Y 轴选择框。
    private final ComboBox<String> coordinateYCombo = new ComboBox<>();
    // 自动范围复选框。
    private final CheckBox autoRangeCheck = new CheckBox("自动范围");
    // 流线叠加开关。
    private final CheckBox flowLineCheck = new CheckBox("海流流线");
    // 波场箭头叠加开关。
    private final CheckBox waveArrowCheck = new CheckBox("波浪箭头");
    // 风羽叠加开关。
    private final CheckBox windBarbCheck = new CheckBox("风场风羽");
    // 底图显示开关。
    private final CheckBox basemapCheck = new CheckBox("显示底图");
    // 底图来源下拉框。
    private final ComboBox<String> basemapCombo = new ComboBox<>();
    // 底图透明度滑块。
    private final Slider basemapOpacitySlider = new Slider(0.0, 1.0, 0.75);
    // 自定义底图按钮。
    private final Button customBasemapButton = new Button("自定义底图");
    // 手动最小值输入框。
    private final TextField minField = new TextField();
    // 手动最大值输入框。
    private final TextField maxField = new TextField();
    // 主渲染画布。
    private final Canvas renderCanvas = new Canvas(900, 720);
    // 色条画布。
    private final ColorBarCanvas colorBarCanvas = new ColorBarCanvas();
    // 主画布宿主容器。
    private final StackPane canvasHost = new StackPane();
    // 主画布和色条组合容器。
    private final HBox visualizationBox = new HBox(12);
    // 结构化网格坐标选择容器。
    private final VBox coordinateSelectionBox = new VBox(6);

    public MainView() {
        // 构造时立即生成整套界面布局。
        build();
    }

    private void build() {
        // 设置根容器内边距。
        setPadding(new Insets(0));

        // ── 顶部区域 ──
        Menu fileMenu = new Menu("文件");
        fileMenu.getItems().addAll(openMenuItem, loadCoastlineMenuItem, useBuiltInCoastlineMenuItem, clearCoastlineMenuItem, exportPngMenuItem, exitMenuItem);
        helpMenu.getItems().add(aboutMenuItem);
        MenuBar menuBar = new MenuBar(fileMenu, helpMenu);
        menuBar.getStyleClass().add("app-menu-bar");

        ToolBar toolBar = new ToolBar(openButton, new Separator(), exportButton, new Separator(), resetViewButton, new Separator(), datasetLabel);
        toolBar.getStyleClass().add("app-tool-bar");

        VBox topBox = new VBox(menuBar, toolBar);
        setTop(topBox);

        // ── 左侧面板 ──
        configureTextArea(summaryArea);
        configureTextArea(attributesArea);
        configureTextArea(warningsArea);

        datasetList.setPlaceholder(new Label("暂无数据集"));
        datasetList.setPrefHeight(120);
        datasetList.getStyleClass().add("dataset-list-view");
        variableList.setPlaceholder(new Label("暂无变量"));
        variableList.setPrefHeight(260);
        variableList.getStyleClass().add("variable-list-view");

        TabPane leftTabs = new TabPane(
            createTab("摘要", summaryArea),
            createTab("属性", attributesArea),
            createTab("警告", warningsArea)
        );
        leftTabs.getStyleClass().addAll("info-tab-pane", "left-tab-pane");
        VBox.setVgrow(leftTabs, Priority.ALWAYS);

        Label datasetListLabel = new Label("已加载数据");
        datasetListLabel.getStyleClass().add("panel-title");
        HBox datasetHeader = new HBox(8, datasetListLabel, removeDatasetButton);
        datasetHeader.setAlignment(Pos.CENTER_LEFT);
        datasetHeader.getStyleClass().add("panel-header");

        Label variableListLabel = new Label("变量");
        variableListLabel.getStyleClass().add("panel-title");

        VBox leftPanel = new VBox(10, datasetHeader, datasetList, variableListLabel, variableList, leftTabs);
        leftPanel.getStyleClass().add("left-panel");
        leftPanel.setPrefWidth(330);

        // ── 中央画布区域 ──
        overlayLabel.getStyleClass().add("overlay-label");
        overlayLabel.setWrapText(true);
        overlayLabel.setMaxWidth(420);
        overlayLabel.setMouseTransparent(true);

        canvasHost.getStyleClass().add("canvas-card");
        canvasHost.setPrefSize(900, 720);
        renderCanvas.setManaged(false);
        canvasHost.getChildren().addAll(renderCanvas, overlayLabel);
        StackPane.setAlignment(renderCanvas, Pos.TOP_LEFT);
        StackPane.setAlignment(overlayLabel, Pos.CENTER);

        // 画布标题栏：显示当前变量名
        currentVariableLabel.getStyleClass().add("canvas-title");
        HBox canvasHeader = new HBox(currentVariableLabel);
        canvasHeader.setAlignment(Pos.CENTER_LEFT);
        canvasHeader.getStyleClass().add("canvas-header");

        visualizationBox.getChildren().addAll(canvasHost, colorBarCanvas);
        HBox.setHgrow(canvasHost, Priority.ALWAYS);

        VBox centerPanel = new VBox(canvasHeader, visualizationBox);
        centerPanel.getStyleClass().add("center-panel");
        VBox.setVgrow(visualizationBox, Priority.ALWAYS);

        // ── 右侧控制面板 ──
        Label colorMapLabel = new Label("色表");
        colorMapLabel.getStyleClass().add("section-label");
        Label depthLabel = new Label("图层");
        depthLabel.getStyleClass().add("section-label");

        depthSlider.setBlockIncrement(1);
        depthSlider.setMajorTickUnit(1);
        depthSlider.setMinorTickCount(0);
        depthSlider.setShowTickMarks(true);
        depthSlider.setShowTickLabels(false);
        depthSlider.getStyleClass().add("depth-slider");

        coordinateXCombo.setPromptText("横坐标");
        coordinateYCombo.setPromptText("纵坐标");
        coordinateSelectionBox.getStyleClass().add("coordinate-selection-box");
        coordinateSelectionBox.getChildren().addAll(
            coordinateSelectionLabel,
            new Label("横坐标"),
            coordinateXCombo,
            new Label("纵坐标"),
            coordinateYCombo
        );
        coordinateSelectionBox.setVisible(false);
        coordinateSelectionBox.setManaged(false);
        coordinateXCombo.setDisable(true);
        coordinateYCombo.setDisable(true);

        autoRangeCheck.setSelected(true);
        flowLineCheck.setDisable(true);
        waveArrowCheck.setDisable(true);
        windBarbCheck.setDisable(true);
        basemapCombo.getItems().addAll("无底图", "OpenStreetMap 标准地图");
        basemapCombo.getSelectionModel().select("无底图");
        basemapCombo.setMaxWidth(Double.MAX_VALUE);
        basemapOpacitySlider.setBlockIncrement(0.1);
        basemapOpacitySlider.setMajorTickUnit(0.25);
        basemapOpacitySlider.setMinorTickCount(4);
        basemapOpacitySlider.setShowTickMarks(true);
        basemapOpacitySlider.setShowTickLabels(false);
        basemapOpacitySlider.getStyleClass().add("opacity-slider");
        minField.setPromptText("最小值");
        maxField.setPromptText("最大值");
        HBox rangeBox = new HBox(8, minField, maxField, applyRangeButton);
        rangeBox.getStyleClass().add("range-input-row");

        visualizeButton.getStyleClass().addAll("render-button", "accent-button");

        // 坐标信息卡片
        VBox coordInfoCard = makeCard("坐标信息",
            coordinateVariableLabel,
            coordinateSelectionBox,
            connectivityVariableLabel,
            variableMetaLabel
        );

        // 底图卡片
        Label basemapSectionLabel = new Label("底图");
        basemapSectionLabel.getStyleClass().add("section-label");
        Label basemapOpacityLabel = new Label("底图透明度");
        basemapOpacityLabel.getStyleClass().add("section-label");
        VBox basemapCard = makeCard("底图设置",
            basemapCheck,
            basemapCombo,
            customBasemapButton,
            basemapOpacityLabel,
            basemapOpacitySlider
        );

        // 叠加卡片
        VBox overlayCard = makeCard("叠加图层",
            flowLineCheck,
            waveArrowCheck,
            windBarbCheck
        );

        // 颜色与范围卡片
        VBox colorCard = makeCard("颜色与范围",
            colorMapLabel,
            colorMapCombo,
            depthLabel,
            depthSlider,
            layerInfoLabel,
            autoRangeCheck,
            rangeBox,
            rangeInfoLabel
        );

        VBox rightPanel = new VBox(10,
            visualizeButton,
            coordInfoCard,
            basemapCard,
            overlayCard,
            colorCard
        );
        rightPanel.setPadding(new Insets(12));
        rightPanel.setPrefWidth(280);
        rightPanel.getStyleClass().add("right-panel");

        ScrollPane rightScroll = new ScrollPane(rightPanel);
        rightScroll.setFitToWidth(true);
        rightScroll.setFitToHeight(true);
        rightScroll.getStyleClass().add("right-scroll");

        // ── 主分割面板 ──
        SplitPane splitPane = new SplitPane(leftPanel, centerPanel, rightScroll);
        splitPane.setDividerPositions(0.22, 0.8);
        splitPane.getStyleClass().add("main-split-pane");
        setCenter(splitPane);

        // ── 状态栏 ──
        Region statusSpacer = new Region();
        HBox.setHgrow(statusSpacer, Priority.ALWAYS);
        statusLabel.getStyleClass().add("status-text");
        authorLabel.getStyleClass().add("author-text");
        HBox statusBar = new HBox(12, statusLabel, statusSpacer, authorLabel);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        setBottom(statusBar);
    }

    private VBox makeCard(String title, javafx.scene.Node... children) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        VBox card = new VBox(6);
        card.getStyleClass().add("settings-card");
        card.getChildren().add(titleLabel);
        card.getChildren().addAll(children);
        return card;
    }

    private Tab createTab(String title, TextArea area) {
        Tab tab = new Tab(title, area);
        tab.setClosable(false);
        return tab;
    }

    private void configureTextArea(TextArea area) {
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefRowCount(10);
        area.getStyleClass().add("info-text-area");
    }

    public MenuItem getOpenMenuItem() {
        return openMenuItem;
    }

    public MenuItem getExportPngMenuItem() {
        return exportPngMenuItem;
    }

    public MenuItem getLoadCoastlineMenuItem() {
        return loadCoastlineMenuItem;
    }

    public MenuItem getUseBuiltInCoastlineMenuItem() {
        return useBuiltInCoastlineMenuItem;
    }

    public MenuItem getClearCoastlineMenuItem() {
        return clearCoastlineMenuItem;
    }

    public MenuItem getExitMenuItem() {
        return exitMenuItem;
    }

    public Menu getHelpMenu() {
        return helpMenu;
    }

    public MenuItem getAboutMenuItem() {
        return aboutMenuItem;
    }

    public Button getOpenButton() {
        return openButton;
    }

    public Button getExportButton() {
        return exportButton;
    }

    public Button getResetViewButton() {
        return resetViewButton;
    }

    public Button getVisualizeButton() {
        return visualizeButton;
    }

    public Button getApplyRangeButton() {
        return applyRangeButton;
    }

    public Button getRemoveDatasetButton() {
        return removeDatasetButton;
    }

    public ListView<LoadedDatasetItem> getDatasetList() {
        return datasetList;
    }

    public ListView<VariableInfo> getVariableList() {
        return variableList;
    }

    public TextArea getSummaryArea() {
        return summaryArea;
    }

    public TextArea getAttributesArea() {
        return attributesArea;
    }

    public TextArea getWarningsArea() {
        return warningsArea;
    }

    public Label getOverlayLabel() {
        return overlayLabel;
    }

    public Label getDatasetLabel() {
        return datasetLabel;
    }

    public Label getCurrentVariableLabel() {
        return currentVariableLabel;
    }

    public Label getCoordinateVariableLabel() {
        return coordinateVariableLabel;
    }

    public Label getConnectivityVariableLabel() {
        return connectivityVariableLabel;
    }

    public Label getVariableMetaLabel() {
        return variableMetaLabel;
    }

    public Label getLayerInfoLabel() {
        return layerInfoLabel;
    }

    public Label getRangeInfoLabel() {
        return rangeInfoLabel;
    }

    public Label getStatusLabel() {
        return statusLabel;
    }

    public Label getAuthorLabel() {
        return authorLabel;
    }

    public Slider getDepthSlider() {
        return depthSlider;
    }

    public ComboBox<String> getColorMapCombo() {
        return colorMapCombo;
    }

    public ComboBox<String> getCoordinateXCombo() {
        return coordinateXCombo;
    }

    public ComboBox<String> getCoordinateYCombo() {
        return coordinateYCombo;
    }

    public VBox getCoordinateSelectionBox() {
        return coordinateSelectionBox;
    }

    public CheckBox getAutoRangeCheck() {
        return autoRangeCheck;
    }

    public CheckBox getFlowLineCheck() {
        return flowLineCheck;
    }

    public CheckBox getWaveArrowCheck() {
        return waveArrowCheck;
    }

    public CheckBox getWindBarbCheck() {
        return windBarbCheck;
    }

    public CheckBox getBasemapCheck() {
        return basemapCheck;
    }

    public ComboBox<String> getBasemapCombo() {
        return basemapCombo;
    }

    public Slider getBasemapOpacitySlider() {
        return basemapOpacitySlider;
    }

    public Button getCustomBasemapButton() {
        return customBasemapButton;
    }

    public TextField getMinField() {
        return minField;
    }

    public TextField getMaxField() {
        return maxField;
    }

    public Canvas getRenderCanvas() {
        return renderCanvas;
    }

    public ColorBarCanvas getColorBarCanvas() {
        return colorBarCanvas;
    }

    public StackPane getCanvasHost() {
        return canvasHost;
    }

    public HBox getVisualizationBox() {
        return visualizationBox;
    }
}
