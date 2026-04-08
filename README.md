# NetCDF Viewer

一款基于 JavaFX 的 NetCDF 非结构化三角网桌面可视化工具，支持读取包含节点坐标、三角形连接关系和属性变量的 `.nc` 文件，并进行二维平面填色显示、深度层切换与 PNG 导出。

## 项目定位

本项目面向海洋、水文、环境与数值模拟数据处理场景，重点解决以下问题：

- 读取非结构化三角网 NetCDF 数据
- 自动识别节点坐标与三角形连接关系
- 对单层与多层属性变量进行平面可视化
- 在 Windows 环境下生成可直接分发的安装包

## 主要功能

- 支持通过菜单或拖拽方式打开 `.nc` 文件
- 展示变量、维度、全局属性、坐标变量和连接变量信息
- 支持单层变量与多层变量的平面渲染
- 支持深度层切换
- 支持颜色映射与当前层最小值 / 最大值显示
- 支持导出当前可视化结果为 PNG 图片
- 支持打包为独立 Windows 安装程序 `.exe`

## 技术栈

- Java 17+
- JavaFX 21
- NetCDF-Java 5.9.x
- Maven
- JUnit 5
- jpackage

## 运行环境

- 开发环境建议：JDK 17 及以上
- 构建工具：Maven 3.9 及以上
- 打包平台：Windows
- 当前项目已在 Java 21 环境下完成测试与打包验证

## 快速开始

### 1. 运行测试

```powershell
mvn -q test
```

### 2. 开发模式启动

```powershell
mvn javafx:run
```

### 3. 打包为 Windows 安装程序

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-exe.ps1
```

打包完成后，安装包位于：

- `target\installer\NetCDFViewer-1.0.2.exe`

## 使用说明

### 打开数据

启动后可通过以下方式打开 NetCDF 文件：

- 菜单栏 `File -> Open...`
- 主界面按钮 `Open`
- 直接拖拽 `.nc` 文件到窗口

### 可视化流程

1. 打开数据文件
2. 在左侧变量列表中选择可平面化变量
3. 若变量包含深度维，则使用右侧滑块切换层
4. 主图区域显示当前层平面图
5. 如需导出，点击 `Export PNG`

## 工程结构

```text
src/
├── main/
│   ├── java/com/example/netcdfviewer/
│   │   ├── io/          # NetCDF 解析
│   │   ├── model/       # 网格与变量模型
│   │   ├── render/      # 渲染与颜色映射
│   │   └── ui/          # JavaFX 界面与控制器
│   └── resources/
│       └── icons/       # 应用与安装包图标
└── test/
    └── java/com/example/netcdfviewer/
        ├── io/
        ├── render/
        ├── runtime/
        ├── smoke/
        └── ui/
```

## 数据文件说明

为了避免仓库体积过大，根目录中的本地 `.nc` 示例数据已在 `.gitignore` 中排除，不会提交到公开仓库。

这意味着：

- 公开仓库默认只包含源码、脚本、文档和测试代码
- 运行时请自行准备 NetCDF 示例文件
- 如需公开发布样例数据，建议单独提供下载地址或发布压缩包

## 开源发布建议

如果你准备将项目发布到 GitHub / Gitee，建议至少包含以下内容：

- 中文 `README`
- 明确的开源许可证
- 贡献说明
- 版本变更记录

本仓库已补充上述基础文件，适合继续整理后对外公开。

## 质量状态

当前版本已完成以下验证：

- Maven 全量测试通过
- PNG 导出可写入且可读回验证通过
- 打包运行时模块集验证通过
- Windows 安装包生成成功

## 贡献

欢迎通过 Issue 或 Pull Request 提交问题反馈、功能建议和改进代码。

详细说明请见：

- [CONTRIBUTING.md](CONTRIBUTING.md)

## 版本记录

版本说明见：

- [CHANGELOG.md](CHANGELOG.md)

## 开源许可证

本项目采用 MIT License 开源：

- [LICENSE](LICENSE)

## 作者信息

- 作者：lwj
- 邮箱：2762692204@qq.com
