# UI 可用信息说明

本文档只回答一件事：当前项目已经能给 UI 提供什么信息，以及这些信息是否可靠。

## 1. 可直接用于 UI 的主状态

主入口状态：`DashboardUiState`

位置：
- `app/src/main/java/com/bgtactician/app/viewmodel/MainViewModel.kt`

当前可直接给 UI 使用的字段：

### 1.1 应用与数据源状态
- `appVersionLabel`：应用版本号
- `catalogVersion`：当前策略数据版本
- `isLoading`：首页/面板是否还在初始化
- `isRefreshing`：是否正在刷新远程数据
- `dataSource`：当前数据来源
  - `ASSET`：内置
  - `CACHE`：远程缓存
  - `REMOTE`：刚拉下来的远程数据
- `lastSyncLabel`：最近同步时间
- `manifestVersionLabel`：远程 manifest 版本
- `syncMessage`：同步结果提示文案
- `manifestUrlOverride` / `effectiveManifestUrl`：远程更新地址

适合做的 UI：
- 顶部状态栏
- 数据同步状态卡片
- 调试设置页

### 1.2 当前对局环境
- `selectedTribes`：当前识别到或手动选择的 5 族环境
- `autoDetectStatus`：自动识别状态
  - `WAITING`
  - `SCANNING`
  - `LOCKED`
  - `NEEDS_ATTENTION`
- `autoDetectDebugInfo`：自动识别调试信息
  - `tavernTier`：当前酒馆等级
  - `tavernTierLabel`：酒馆等级文本
  - `recognizedTribesLabel`：识别到的种族文本
  - `rawText`：OCR/识别原始文本
  - `aiSourceLabel` / `aiModelLabel`：AI 来源与模型
  - `aiRequestId`：请求 ID
  - `aiScreenTypeLabel`：AI 判断到的画面类型
  - `aiHeroesLabel`：识别到的英雄摘要
  - `aiSummaryLabel`：AI 返回摘要
  - `viewportLabel` / `headerLabel`：截图和头部区域调试信息
  - `latestDumpPath`：最近一次调试落盘路径
  - `lastUpdatedLabel`：最近更新时间
- `currentTavernTier`：当前酒馆等级，来自 `autoDetectDebugInfo.tavernTier`

适合做的 UI：
- 首页实时酒馆状态卡
- 悬浮窗顶部状态条
- 调试抽屉

### 1.3 英雄识别与推荐
- `recognizedHeroes`：当前识别出的英雄列表
- `selectedHeroCardId` / `selectedHeroName` / `selectedHeroSlot`：当前选中的英雄
- `selectedHero`：当前已锁定英雄
- `heroStatsUpdatedAtLabel`：英雄统计更新时间

`recognizedHeroes` 中每个英雄当前可用字段：
- `slot`：第几个英雄位
- `recognizedName`：识别出来的原始名称
- `heroCardId`：英雄卡牌 ID
- `displayName`：用于 UI 的显示名
- `localizedName`：本地化名称
- `armor`：护甲
- `matchSource`：命中来源
  - `NONE`
  - `HERO_CARD_ID`
  - `HERO_NAME_ALIAS`
- `averagePosition`：平均名次
- `conservativePositionEstimate`：保守名次估计
- `dataPoints`：样本量
- `totalOffered`：被发到次数
- `totalPicked`：被选择次数
- `synergyTribes`：当前 5 族下的协同种族
- `bestLobbyTribe` / `bestLobbyImpact`：最强环境族及影响
- `worstLobbyTribe` / `worstLobbyImpact`：最差环境族及影响
- `recommendation`：推荐结果

`recommendation` 当前字段：
- `tier`：推荐等级
  - `TOP_PICK`
  - `GOOD_PICK`
  - `NICHE`
  - `AVOID`
- `score`：综合分
- `recommendedCompId` / `recommendedCompName`：推荐流派
- `fallbackCompId` / `fallbackCompName`：备选流派
- `pivotHint`：转型提示
- `summary`：一句话总结
- `reason`：推荐原因

适合做的 UI：
- 英雄三选卡片
- 评分排序列表
- 英雄详情侧栏
- 推荐理由说明区

### 1.4 流派与策略
- `allStrategies`：当前版本所有可用流派
- `strategies`：按当前 5 族过滤后的流派
- `selectedStrategyId`：当前选中的流派 ID
- `selectedStrategy`：当前选中的流派
- `liveStrategy`：当前实时推荐流派
  - 优先用户手选
  - 否则使用英雄推荐给出的主流派

每个 `StrategyComp` 当前字段：
- `id`：流派 ID
- `name`：流派名称
- `tier`：流派评级，例如 `T0/T1/T2`
- `difficulty`：难度
- `powerLevel`：原始上游评级
- `requiredTribes`：要求出现的种族
- `allowedAnomalies`：允许的畸变
- `recommendedMode`：推荐模式
- `whenToCommit`：转型信号
- `sourcePatchNumber`：来源补丁号
- `overview`：概览
- `earlyStrategy`：前期打法
- `lateStrategy`：后期打法
- `upgradeTurns`：升级节奏
- `positioningHints`：站位建议
- `keyMinions`：关键牌列表

适合做的 UI：
- 流派总览页
- 卡组详情页
- 推荐路线抽屉
- 关键牌清单

## 2. 关键牌可提供的信息

结构：`KeyMinion`

当前代码字段：
- `id`
- `name`
- `techLevel`
  - 序列化字段仍来自 JSON 的 `star`
  - 但语义现在已经固定为“真实酒馆等级”
- `phase`
- `statusRaw`
- `finalBoardWeight`
- `cardId`
- `imageUrl`
- `imageAsset`

说明：
- `techLevel` 现在已经被校正为真实 tavern tier，可以直接用于 UI 展示和过滤
- `phase` 仍然表示业务阶段，例如“主核 / 补强 / 过渡 / 经济”
- `statusRaw` 是上游原始状态值，例如 `CORE / ADDON / RECOMMENDED / CYCLE`
- `finalBoardWeight` 代表终盘重要度，适合做排序或权重提示
- `cardId` 可作为所有详情跳转、图片、统计、metadata 查询的主键
- `imageUrl` / `imageAsset` 可直接给卡牌头像组件使用

适合做的 UI：
- 关键牌瀑布流
- 分层卡池图
- 必须件 / 替代件 / 支撑件
- 按酒馆等级分组展示

## 3. 已经可用但还没有进 `DashboardUiState` 的数据

这些信息仓库里已经能读到，如果 UI 需要，我可以继续接进状态层。

### 3.1 战棋卡牌元数据
来源：`BattlegroundCardMetadataCatalog`

位置：
- `app/src/main/java/com/bgtactician/app/data/model/BattlegroundCardMetadataModels.kt`
- `app/src/main/assets/bgs_card_metadata.json`
- `StrategyRepository.loadBattlegroundCardMetadata(...)`

每张卡可提供：
- `dbfId`
- `name`
- `localizedName`
- `type`
  - `MINION`
  - `HERO`
  - `HERO_POWER`
  - `BATTLEGROUND_SPELL`
  - `BATTLEGROUND_TRINKET`
  - `BATTLEGROUND_QUEST_REWARD`
  - `BATTLEGROUND_ANOMALY`
- `techLevel`
- `races`
- `spellSchool`
- `isPoolMinion`
- `isPoolSpell`
- `premiumCardId`
- `normalCardId`
- `relatedCardId`

适合做的 UI：
- 卡牌详情抽屉
- 卡牌标签角标
- 金卡/原卡跳转
- 酒馆法术/饰品/任务奖励专用面板
- 按类型过滤器

### 3.2 卡牌规则限制
来源：`CardRulesCatalog`

当前 `DashboardUiState` 已有：`cardRules`

每张卡目前可判断：
- 需要哪些种族在局内
- 被哪些种族 ban 掉
- 某些英雄强制可用或禁用
- 是否必须 3 本后才能生效

适合做的 UI：
- “当前局可拿 / 不可拿”标签
- 灰显不可用关键牌
- 环境适配提示

### 3.3 卡牌统计
来源：`BattlegroundCardStatsCatalog`

仓库里已可加载，但还没放进 `DashboardUiState`。

当前可提供：
- `cardId`
- `totalPlayed`
- `averagePlacement`
- `averagePlacementOther`
- `turnStats`

适合做的 UI：
- 卡牌强度趋势
- 某张核心牌“拿到后提升多少名次”
- 不同回合拿到时的表现

### 3.4 英雄名称索引
来源：`BattlegroundHeroNameIndex`

可提供：
- `heroCardId`
- 英文名
- 中文名
- 别名

适合做的 UI：
- 搜索英雄
- 名称纠错
- 英雄别名高亮

## 4. 当前数据可靠性结论

### 4.1 现在可以认为是可靠的
- 当前 5 族环境
- 当前酒馆等级
- 英雄识别结果与统计推荐
- 流派过滤结果
- `keyMinions[].techLevel`
- `keyMinions` 中只保留 `MINION`
- 战棋卡牌 metadata 的 `type / techLevel / races / pool flags`

### 4.2 当前不建议直接拿来做核心判断的
- `phase`
  - 它适合做“业务阶段标签”，不适合做 tavern tier 判断
- `statusRaw`
  - 它适合做“核心/补强/经济”分类，不适合做真实强度结论
- `powerLevel`
  - 它是上游评级，适合展示，不适合代替本地推荐分

## 5. UI 设计时推荐的模块拆法

如果你现在要重做 UI，我建议按下面的模块拿数据：

### 5.1 首页
- 当前 5 族
- 当前酒馆等级
- 自动识别状态
- 当前英雄
- 当前推荐流派
- 实时关键牌推荐

### 5.2 英雄选择页
- 三个英雄卡片
- 每个英雄的平均名次
- 样本量
- 当前 5 族协同
- 推荐主流派
- 备选流派
- 转型提示

### 5.3 流派详情页
- 流派基础信息
- 转型信号
- 前中后期说明
- 升本节奏
- 关键牌列表
- 按酒馆等级分组
- 必须件 / 替代件 / 支撑件
- 站位提示

### 5.4 卡牌详情弹层
- 卡牌名
- 图片
- 酒馆等级
- 类型
- 种族
- 是否在池
- 是否酒馆法术
- 是否有金卡映射
- 相关联卡牌

### 5.5 调试页
- 当前数据源
- 远程 manifest 版本
- 最近同步时间
- AI 模型与请求 ID
- 原始识别文本
- 最近调试截图路径

## 6. 当前明确拿不到的内容

当前项目里没有稳定提供以下信息，UI 不要先假设有：
- 对手阵容详情
- 当前商店 7 张牌完整识别结果
- 我方场面实时完整识别结果
- 手牌详情
- 当前回合数
- 当前金币数
- 畸变、任务、饰品的实时画面识别结果
- 每张牌的实时抓取率或局内出现率

这些后面可以做，但现在不要围绕它们设计强依赖 UI。

## 7. 给 UI 的最稳接口建议

如果只看设计语义，不看代码命名，建议以后 UI 统一按下面这套理解：

### 7.1 流派卡片
- `strategy.id`
- `strategy.name`
- `strategy.tier`
- `strategy.difficulty`
- `strategy.requiredTribes`
- `strategy.whenToCommit`
- `strategy.keyMinions`

### 7.2 关键牌卡片
- `minion.cardId`
- `minion.name`
- `minion.techLevel`
- `minion.phase`
- `minion.statusRaw`
- `minion.finalBoardWeight`
- `minion.imageUrl`

### 7.3 英雄卡片
- `hero.heroCardId`
- `hero.displayName`
- `hero.armor`
- `hero.averagePosition`
- `hero.dataPoints`
- `hero.synergyTribes`
- `hero.recommendation`

## 8. 如果你下一步要我继续做

我可以继续补两类东西：

1. 把 `BattlegroundCardMetadataCatalog` 正式接入 `DashboardUiState`
   - 这样 UI 可以直接读卡牌 `type / races / isPoolMinion / isPoolSpell`

2. 输出一份“UI 字段草图 JSON”
   - 我按页面直接给你整理成：首页、英雄页、流派页、卡牌详情页四份字段清单
   - 你做 Figma 会更快
