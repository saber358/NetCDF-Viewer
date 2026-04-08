package com.example.netcdfviewer;

import com.example.netcdfviewer.ui.MainController;
import com.example.netcdfviewer.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

/**
 * 应用主入口类。
 * 这个类负责启动 JavaFX 运行时并创建主窗口。
 */
public final class App extends Application {
    // 将应用名称暴露给其他类复用，避免多处重复写死字符串。
    public static final String APP_NAME = AppMetadata.APP_NAME;

    @Override
    public void start(Stage stage) {
        // 创建主界面节点树。
        MainView mainView = createMainView(stage);
        // 创建承载主界面的场景对象，并指定默认窗口尺寸。
        Scene scene = new Scene(mainView, 1440, 900);
        // 尝试从资源目录加载应用图标。
        try (InputStream stream = App.class.getResourceAsStream("/icons/app-icon.png")) {
            // 只有在资源存在时才把图标加入窗口，避免空指针问题。
            if (stream != null) {
                stage.getIcons().add(new Image(stream));
            }
        } catch (Exception ignored) {
            // 如果图标资源加载失败，则继续启动程序，但不阻断主流程。
        }
        // 设置窗口标题。
        stage.setTitle(APP_NAME);
        // 把场景挂到窗口上。
        stage.setScene(scene);
        // 设置窗口最小宽度，避免控件被压缩得无法使用。
        stage.setMinWidth(1100);
        // 设置窗口最小高度，避免主图区域过小。
        stage.setMinHeight(720);
        // 显示窗口。
        stage.show();
    }

    public static MainView createMainView(Stage stage) {
        // 先创建视图对象。
        MainView mainView = new MainView();
        // 再创建控制器并完成事件与状态初始化。
        new MainController(stage, mainView).initialize();
        // 返回已经接线完成的主界面。
        return mainView;
    }

    public static void main(String[] args) {
        // 交给 JavaFX 标准启动流程处理。
        launch(args);
    }
}
