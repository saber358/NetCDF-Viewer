package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.basemap.BaseMapDefinition;
import com.example.netcdfviewer.basemap.BaseMapLayer;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.Arrays;
import java.util.List;

public final class BaseMapConfigDialog {
    private BaseMapConfigDialog() {
    }

    /*
     * ========================================================================
     * 步骤1：创建自定义底图对话框
     * ========================================================================
     * 目标：
     *   1) 收集自定义底图名称、URL 模板、token、子域和透明度
     *   2) 将用户输入收敛为 BaseMapDefinition
     */
    public static Dialog<BaseMapDefinition> create(Window owner) {
        Dialog<BaseMapDefinition> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("自定义底图");
        dialog.setHeaderText("添加 XYZ 瓦片底图");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField nameField = new TextField("自定义底图");
        TextField urlField = new TextField("https://tiles.example.com/{z}/{x}/{y}.png");
        TextField tokenField = new TextField();
        TextField subdomainField = new TextField();
        TextField opacityField = new TextField("1.0");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("名称"), nameField);
        grid.addRow(1, new Label("URL 模板"), urlField);
        grid.addRow(2, new Label("Token"), tokenField);
        grid.addRow(3, new Label("子域名"), subdomainField);
        grid.addRow(4, new Label("透明度"), opacityField);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            String name = nameField.getText().trim();
            String template = urlField.getText().trim();
            String token = tokenField.getText().trim();
            List<String> subdomains = Arrays.stream(subdomainField.getText().split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
            double opacity = parseOpacity(opacityField.getText());
            return new BaseMapDefinition(
                "custom",
                name,
                List.of(new BaseMapLayer(name, template, token, subdomains, opacity)),
                false
            );
        });

        return dialog;
    }

    private static double parseOpacity(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception exception) {
            return 1.0;
        }
    }
}
