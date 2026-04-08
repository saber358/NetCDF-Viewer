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

public final class MainView extends BorderPane {
    private final Menu helpMenu = new Menu("Help");
    private final MenuItem openMenuItem = new MenuItem("Open...");
    private final MenuItem exportPngMenuItem = new MenuItem("Export PNG...");
    private final MenuItem exitMenuItem = new MenuItem("Exit");
    private final MenuItem aboutMenuItem = new MenuItem("About");
    private final Button openButton = new Button("Open");
    private final Button exportButton = new Button("Export PNG");
    private final Button resetViewButton = new Button("Reset View");
    private final Button visualizeButton = new Button("Visualize");
    private final Button applyRangeButton = new Button("Apply Range");
    private final ListView<VariableInfo> variableList = new ListView<>();
    private final TextArea summaryArea = new TextArea();
    private final TextArea attributesArea = new TextArea();
    private final TextArea warningsArea = new TextArea();
    private final Label overlayLabel = new Label("Open a NetCDF file to begin.");
    private final Label datasetLabel = new Label("No file loaded");
    private final Label currentVariableLabel = new Label("Variable: -");
    private final Label coordinateVariableLabel = new Label("Coordinates: -");
    private final Label connectivityVariableLabel = new Label("Connectivity: -");
    private final Label variableMetaLabel = new Label("Variable details: -");
    private final Label layerInfoLabel = new Label("Layer: -");
    private final Label rangeInfoLabel = new Label("Range: -");
    private final Label statusLabel = new Label("Ready");
    private final Label authorLabel = new Label(AppMetadata.AUTHOR_LABEL);
    private final Slider depthSlider = new Slider(0, 0, 0);
    private final ComboBox<String> colorMapCombo = new ComboBox<>();
    private final CheckBox autoRangeCheck = new CheckBox("Auto range");
    private final TextField minField = new TextField();
    private final TextField maxField = new TextField();
    private final Canvas renderCanvas = new Canvas(900, 720);
    private final ColorBarCanvas colorBarCanvas = new ColorBarCanvas();
    private final StackPane canvasHost = new StackPane();
    private final HBox visualizationBox = new HBox(12);

    public MainView() {
        build();
    }

    private void build() {
        setPadding(new Insets(8));

        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(openMenuItem, exportPngMenuItem, exitMenuItem);
        helpMenu.getItems().add(aboutMenuItem);
        MenuBar menuBar = new MenuBar(fileMenu, helpMenu);

        ToolBar toolBar = new ToolBar(openButton, exportButton, resetViewButton, new Separator(), datasetLabel);
        VBox topBox = new VBox(menuBar, toolBar);
        setTop(topBox);

        configureTextArea(summaryArea);
        configureTextArea(attributesArea);
        configureTextArea(warningsArea);

        variableList.setPlaceholder(new Label("No variables"));
        variableList.setPrefHeight(260);

        TabPane leftTabs = new TabPane(
            createTab("Summary", summaryArea),
            createTab("Attributes", attributesArea),
            createTab("Warnings", warningsArea)
        );
        VBox.setVgrow(leftTabs, Priority.ALWAYS);

        Label variableListLabel = new Label("Variables");
        VBox leftPanel = new VBox(8, variableListLabel, variableList, leftTabs);
        leftPanel.setPadding(new Insets(12));
        leftPanel.setPrefWidth(330);

        overlayLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #4A5568; -fx-background-color: rgba(255,255,255,0.88); -fx-padding: 16 20 16 20; -fx-background-radius: 10;");
        overlayLabel.setWrapText(true);
        overlayLabel.setMaxWidth(420);
        overlayLabel.setMouseTransparent(true);

        canvasHost.setStyle("-fx-background-color: linear-gradient(to bottom, #F8FBFD, #EEF3F7); -fx-border-color: #D0D7DE; -fx-border-radius: 8; -fx-background-radius: 8;");
        canvasHost.setPrefSize(900, 720);
        renderCanvas.setManaged(false);
        canvasHost.getChildren().addAll(renderCanvas, overlayLabel);
        StackPane.setAlignment(renderCanvas, Pos.TOP_LEFT);
        StackPane.setAlignment(overlayLabel, Pos.CENTER);

        visualizationBox.getChildren().addAll(canvasHost, colorBarCanvas);
        HBox.setHgrow(canvasHost, Priority.ALWAYS);

        VBox centerPanel = new VBox(8, currentVariableLabel, visualizationBox);
        centerPanel.setPadding(new Insets(12));
        VBox.setVgrow(visualizationBox, Priority.ALWAYS);

        Label colorMapLabel = new Label("Color map");
        Label depthLabel = new Label("Layer");
        depthSlider.setBlockIncrement(1);
        depthSlider.setMajorTickUnit(1);
        depthSlider.setMinorTickCount(0);
        depthSlider.setShowTickMarks(true);
        depthSlider.setShowTickLabels(false);

        autoRangeCheck.setSelected(true);
        minField.setPromptText("Min");
        maxField.setPromptText("Max");
        HBox rangeBox = new HBox(8, minField, maxField, applyRangeButton);

        VBox rightPanel = new VBox(
            10,
            coordinateVariableLabel,
            connectivityVariableLabel,
            variableMetaLabel,
            visualizeButton,
            colorMapLabel,
            colorMapCombo,
            depthLabel,
            depthSlider,
            layerInfoLabel,
            autoRangeCheck,
            rangeBox,
            rangeInfoLabel
        );
        rightPanel.setPadding(new Insets(12));
        rightPanel.setPrefWidth(280);
        rightPanel.setStyle("-fx-background-color: #FBFCFE; -fx-border-color: #D0D7DE; -fx-border-radius: 8; -fx-background-radius: 8;");

        ScrollPane rightScroll = new ScrollPane(rightPanel);
        rightScroll.setFitToWidth(true);
        rightScroll.setFitToHeight(true);

        SplitPane splitPane = new SplitPane(leftPanel, centerPanel, rightScroll);
        splitPane.setDividerPositions(0.22, 0.8);
        setCenter(splitPane);

        Region statusSpacer = new Region();
        HBox.setHgrow(statusSpacer, Priority.ALWAYS);
        authorLabel.setStyle("-fx-text-fill: #475569;");
        HBox statusBar = new HBox(12, statusLabel, statusSpacer, authorLabel);
        statusBar.setPadding(new Insets(8, 12, 4, 12));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setStyle("-fx-background-color: #F5F7FA; -fx-border-color: #D0D7DE transparent transparent transparent;");
        setBottom(statusBar);
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
    }

    public MenuItem getOpenMenuItem() {
        return openMenuItem;
    }

    public MenuItem getExportPngMenuItem() {
        return exportPngMenuItem;
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

    public CheckBox getAutoRangeCheck() {
        return autoRangeCheck;
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
