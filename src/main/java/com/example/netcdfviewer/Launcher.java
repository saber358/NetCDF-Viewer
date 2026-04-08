package com.example.netcdfviewer;

/**
 * 独立启动器。
 * 在某些打包场景下，单独保留一个普通 main 类会更稳定。
 */
public final class Launcher {
    private Launcher() {
        // 工具类不允许被实例化。
    }

    public static void main(String[] args) {
        // 统一转发到真正的 JavaFX 应用入口。
        App.main(args);
    }
}
