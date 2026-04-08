package com.example.netcdfviewer;

import com.example.netcdfviewer.ui.MainController;
import com.example.netcdfviewer.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

public final class App extends Application {
    public static final String APP_NAME = AppMetadata.APP_NAME;

    @Override
    public void start(Stage stage) {
        MainView mainView = createMainView(stage);
        Scene scene = new Scene(mainView, 1440, 900);
        try (InputStream stream = App.class.getResourceAsStream("/icons/app-icon.png")) {
            if (stream != null) {
                stage.getIcons().add(new Image(stream));
            }
        } catch (Exception ignored) {
            // Continue without a custom icon if the resource cannot be loaded.
        }
        stage.setTitle(APP_NAME);
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.show();
    }

    public static MainView createMainView(Stage stage) {
        MainView mainView = new MainView();
        new MainController(stage, mainView).initialize();
        return mainView;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
