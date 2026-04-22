# 底图瓦片叠加设计

## 目标

在现有 NetCDF 平面渲染上增加在线底图能力。

第一版支持：

- 无底图
- OpenStreetMap 标准底图
- 天地图矢量底图
- 天地图影像底图
- 天地图地形底图
- 自定义 XYZ URL 模板底图

底图只作为视觉参考层，不改变 NetCDF 数据坐标、变量读取、点查、导出和叠加层逻辑。

## 非目标

第一版不做：

- WebView、Leaflet 或 OpenLayers 嵌入
- 任意投影重投影
- 离线 MBTiles
- 多自定义底图持久化管理
- 用户账号或密钥加密存储

## 总体方案

使用 JavaFX Canvas 原生渲染 XYZ 瓦片。

渲染顺序调整为：

```text
在线底图瓦片
NetCDF 标量场
海流 / 波浪 / 风场叠加
海岸线叠加
```

底图渲染接入 `MainController` 现有后台渲染任务。

底图图像由新增 `basemap` 包生成。主控制器只关心当前底图配置和返回的 `BufferedImage`，不直接处理 URL 拼接、瓦片编号、缓存和网络请求。

## UI 设计

新增“底图”菜单。

菜单项：

- 无底图
- OpenStreetMap 标准图
- 天地图矢量图
- 天地图影像图
- 天地图地形图
- 自定义底图...
- 清除自定义底图参数

新增“自定义底图”对话框。

字段：

- 名称
- URL 模板
- Token 参数
- 子域名列表
- 透明度

URL 模板支持占位符：

- `{z}`
- `{x}`
- `{y}`
- `{s}`
- `{tk}`

天地图内置模板不写死 `tk`。用户需要输入 token。

## 内置底图

OpenStreetMap：

```text
https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png
```

默认子域：

```text
a,b,c
```

天地图使用 WMTS 瓦片 URL。

矢量：

```text
vec_w + cva_w
```

影像：

```text
img_w + cia_w
```

地形：

```text
ter_w + cta_w
```

默认子域：

```text
0,1,2,3,4,5,6,7
```

天地图底图由底图层和注记层组成，所以底图配置模型需要支持多个瓦片层。

## 坐标与投影

底图只在经纬度坐标域启用。

判定规则：

- X 范围落在 `[-180, 180]`
- Y 范围落在 `[-85.05112878, 85.05112878]`
- X 最大值大于最小值
- Y 最大值大于最小值

瓦片使用 Web Mercator XYZ 规则。

渲染时按屏幕像素反算当前经纬度，再映射到对应瓦片像素。这样能避免直接把 Web Mercator 瓦片拉伸到经纬度矩形后产生明显错位。

如果当前数据不是经纬度坐标，底图菜单仍可选择，但渲染时状态栏提示“当前坐标域不是经纬度，已跳过底图”。

## 缓存策略

缓存分两层。

内存缓存：

- 以 URL 为 key
- LRU 淘汰
- 默认保留最近 512 张瓦片

磁盘缓存：

- 路径：用户目录下的 `.netcdf-viewer/tile-cache`
- 以 URL 哈希分目录保存
- 下载成功后写入缓存
- 请求失败时不写入坏文件

第一版不做主动清理。后续可增加缓存大小限制和清理菜单。

## 网络策略

瓦片下载使用 Java 标准 HTTP 客户端。

约束：

- 连接超时 5 秒
- 读取超时 8 秒
- 每次渲染只请求当前视口需要的瓦片
- 缺失瓦片不让整次渲染失败
- 失败瓦片用透明像素跳过

OSM 公共瓦片有访问限制。系统默认可用，但不保证高频批量访问稳定。用户可以用自定义 URL 改成自己的瓦片服务。

## 渲染链路

```text
MainController
  ├─ 收集当前底图配置
  ├─ 确保视口适配当前空间域
  ├─ 后台并行构建底图瓦片图像
  ├─ 后台构建 NetCDF 标量场图像
  ├─ 后台构建波浪 / 海流 / 风场叠加帧
  └─ JavaFX 线程绘制最终画面
```

`RenderFrame` 增加 `baseMapImage` 字段。

`drawLatestFrame` 改为：

```text
清空 Canvas
绘制底图瓦片图像
绘制 NetCDF 标量场图像
绘制流线、波浪、风羽
绘制海岸线
缓存复合帧
```

缩放和平移预览继续使用复合帧缓存，不单独重算底图。

## 新增包结构

```text
src/main/java/com/example/netcdfviewer/basemap/
  BaseMapDefinition.java
  BaseMapLayer.java
  BaseMapPreset.java
  BaseMapSelection.java
  TileAddress.java
  TileCache.java
  TileClient.java
  TileUrlTemplate.java
  TileMath.java
  TileRenderer.java
```

职责：

- `BaseMapDefinition`：描述一个底图，可包含多个瓦片层
- `BaseMapLayer`：单个 URL 模板、子域、token 和透明度
- `BaseMapPreset`：内置 OSM 和天地图配置
- `BaseMapSelection`：当前用户选择
- `TileAddress`：瓦片 z/x/y
- `TileCache`：内存和磁盘缓存
- `TileClient`：下载瓦片
- `TileUrlTemplate`：替换 URL 占位符
- `TileMath`：经纬度和 XYZ 瓦片像素互转
- `TileRenderer`：把当前视口渲染成 `BufferedImage`

## 错误处理

底图错误不能影响 NetCDF 主图。

处理规则：

- URL 模板为空：不启用自定义底图
- token 缺失：天地图不发请求，状态栏提示需要 token
- 网络失败：跳过失败瓦片，继续渲染主图
- 图片解码失败：跳过该瓦片
- 非经纬度坐标：跳过底图

## 测试

新增测试：

- `TileMathTest`
- `TileUrlTemplateTest`
- `TileCacheTest`
- `TileRendererTest`
- `MainControllerLoadFileTest` 增补底图选择和重绘联动测试

测试不依赖真实公网。

网络相关测试使用假的 `TileClient` 返回内存图片。

## 文档

README 更新：

- 功能亮点增加 OSM / 天地图 / 自定义底图
- 使用说明增加天地图 token 提示
- 质量状态增加底图相关测试

CHANGELOG 在发布版本中补充底图功能。

## 风险

主要风险是在线瓦片服务的网络稳定性和访问策略。

第一版用缓存、超时和失败跳过降低影响。底图只是参考层，不能让它阻断 NetCDF 可视化主链路。
