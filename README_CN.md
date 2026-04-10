# BGTactician

[English README](./README.md)

`BGTactician` 是一个面向《炉石传说：酒馆战棋》的安卓悬浮窗战术助手。项目目标是提供：

- 游戏内悬浮球与侧滑面板
- 种族筛选、畸变选择、双打切换
- 阵容推荐、核心牌提示、升本节奏和站位建议
- 本地离线数据兜底
- 远程 `manifest.json` 更新链路
- GitHub Actions 定时拉取与发布策略数据

## 当前能力

- 基于 `WindowManager` 和 `TYPE_APPLICATION_OVERLAY` 的悬浮层
- 使用 Jetpack Compose 编写的控制台与策略详情界面
- 前台服务保活与通知栏恢复交互
- 本地 `strategies` 资源与缓存回退
- 启动后后台检查远程 `manifest` 更新
- 远程数据 `sha256` 校验
- GitHub Pages 发布 `manifest.json` / `strategies.json`

## 项目结构

- 安卓主工程：[`app/`](/home/jerry/work/BGTactician/app)
- 远程数据导入脚本：[`tools/import_external_strategies.py`](/home/jerry/work/BGTactician/tools/import_external_strategies.py)
- Pages 打包脚本：[`tools/build_pages_bundle.py`](/home/jerry/work/BGTactician/tools/build_pages_bundle.py)
- 中文策略补丁：[`tools/strategy_translations_zhCN.json`](/home/jerry/work/BGTactician/tools/strategy_translations_zhCN.json)
- GitHub Actions 工作流：[`.github/workflows/publish-strategy-data.yml`](/home/jerry/work/BGTactician/.github/workflows/publish-strategy-data.yml)
- 远程数据中文文档：[`docs/REMOTE_DATA_CN.md`](/home/jerry/work/BGTactician/docs/REMOTE_DATA_CN.md)

## 构建要求

- Android Studio
- JDK 17
- Android SDK 36
- Android 8.0 以上设备

本项目当前已经在准备好的环境中通过：

```bash
./gradlew assembleDebug
```

## 本地运行

1. 用 Android Studio 打开项目
2. 确认已安装 Android SDK 36 和 JDK 17
3. 连接设备或启动模拟器
4. 执行：

```bash
./gradlew assembleDebug
```

输出 APK 路径：

- [`app/build/outputs/apk/debug/app-debug.apk`](/home/jerry/work/BGTactician/app/build/outputs/apk/debug/app-debug.apk)

## 远程数据更新

应用当前采用：

- APK 内置默认策略库
- 本地缓存优先读取
- 启动后后台请求 `manifest.json`
- 如发现新版本，下载远程策略库并校验 `sha256`
- 校验通过后覆盖本地缓存

生产环境建议通过 Gradle 属性设置固定更新源：

```bash
echo 'BGT_REMOTE_MANIFEST_URL=https://<your-pages-host>/manifest.json' >> ~/.gradle/gradle.properties
```

如果没有配置这个地址，应用仍可运行，只是默认使用内置数据；调试时也可以在 UI 中手动填写 `manifest` 覆盖地址。

当前已经跑通的示例地址：

```text
https://245138061.github.io/Hearthstone/manifest.json
```

## GitHub Actions 数据发布

项目已经提供定时发布工作流：

- 每天自动拉取上游阵容数据
- 生成 `manifest.json`
- 生成 `zhCN` 和 `enUS` 两份策略库
- 发布到 GitHub Pages

启用步骤：

1. 进入仓库 `Settings`
2. 打开 `Pages`
3. 在 `Build and deployment` 中把 `Source` 设为 `GitHub Actions`

详细说明见：

- [`docs/REMOTE_DATA_CN.md`](/home/jerry/work/BGTactician/docs/REMOTE_DATA_CN.md)

## 注意事项

- [`local.properties`](/home/jerry/work/BGTactician/local.properties) 是本机 SDK 配置，不应提交到 Git
- `GitHub Pages` 在中国大陆可能不稳定，生产环境更建议将构建产物同步到国内 CDN
- 当前阵容名和大部分运营提示已中文化，但部分核心牌名仍是英文
- 真实上线前仍建议继续收紧域名白名单、补测试和做远程回滚策略
