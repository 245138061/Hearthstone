# Repository Guidelines

## 项目结构与模块划分
本仓库是单模块 Android 应用，主模块在 `app/`。核心 Kotlin 代码位于 `app/src/main/java/com/bgtactician/app/`，按职责拆分为 `autodetect/`、`data/`、`overlay/`、`ui/`、`viewmodel/`、`vision/`。静态策略与英雄映射资源在 `app/src/main/assets/`，Android 资源在 `app/src/main/res/`。单元测试放在 `app/src/test/`，数据导入和打包脚本在 `tools/`，补充说明文档在 `docs/`。

## 构建、测试与开发命令
- `./gradlew :app:assembleDebug`：构建调试包。
- `./gradlew :app:compileDebugKotlin`：快速检查 Kotlin 是否可编译。
- `./gradlew :app:testDebugUnitTest`：运行本地 JVM 单元测试。
- `./gradlew :app:installDebug`：安装调试包到已连接设备。
- `python3 tools/import_external_strategies.py`：刷新外部策略数据。
- `python3 tools/build_pages_bundle.py`：重建 Pages 发布产物。

调试包输出路径：`app/build/outputs/apk/debug/app-debug.apk`。

## 编码规范与命名约定
统一使用 Kotlin 默认风格：4 空格缩进，类型名使用 `PascalCase`，方法和属性使用 `camelCase`，常量使用 `SCREAMING_SNAKE_CASE`。Compose 组件命名应直接表达用途，例如 `HeroSelectionFloatingOverlay`。优先按功能拆文件，不要把无关逻辑堆到通用工具类里。

需要注释时，必须写中文，并明确说明业务意图或特殊约束。例如：`// 英雄识别成功但 5 族不稳定时，沿用当前环境避免直接判失败`。不要写“给变量赋值”这类重复代码表面的空注释。

## 测试要求
测试框架使用 JUnit 4，测试文件放在 `app/src/test/`。测试文件名应与目标类对应，例如 `HeroSelectionRecommendationEngineTest.kt`。测试名建议用反引号包裹的行为描述。修改推荐逻辑、视觉解析、失败切换时，应同步补测试并先跑 `./gradlew :app:testDebugUnitTest`。

## 提交与合并请求规范
提交信息建议使用清晰的祈使句，优先采用 `feat:`、`fix:`、`docs:`、`chore:` 前缀，例如 `fix: 调整英雄选择页识别容错`。一次提交尽量只做一类变更。PR 需要包含：
- 问题与修改摘要
- 影响范围，如 `vision`、`overlay`、`ui`
- UI 或悬浮窗改动的截图/录屏
- 本地验证结果，如 `:app:testDebugUnitTest`、`:app:assembleDebug`

## 配置与安全说明
不要提交真实 API Key。视觉模型和远程 manifest 地址通过 Gradle 属性或 `local.properties` 注入。本地调试覆盖配置不要入库；新增配置项时，优先保持调试与正式环境隔离。
