# Claims —— 纯服务端领地保护 Fabric 模组

一个 **只安装在服务器上** 的领地保护模组，原版客户端无需安装任何东西即可连接。
玩家通过**聊天命令 + 金斧选区**圈地，圈好的领地内只有主人和信任玩家可以自由活动，
其余玩家会被**完全阻止**（不能进入、不能破坏/放置、不能使用方块、不能攻击/交互实体）。

- 目标版本：Minecraft **26.2**
- Fabric Loader：**0.19.3**
- Fabric API：**0.158.0+26.2**
- 运行/编译：**Java 25**
- 形态：`environment = "server"`（纯服务端，客户端无需安装）
- ✅ **已按真实 26.2 + Fabric API 0.158.0+26.2 编译验证通过**（产物 `build/libs/claims-1.0.0.jar`）

> 设计细节见 [DESIGN.md](DESIGN.md)。

---

## 命令一览

| 命令 | 说明 |
|---|---|
| `/claim create [名字]` | 用当前选区创建领地（先选好两个角点）。名字可省略，自动命名。 |
| `/claim remove [名字]` | 删除领地。缺省删除你**当前所在**的领地；仅主人（或 OP）可删。 |
| `/claim info [名字]` | 查看领地信息（主人/信任列表/范围）。缺省显示你当前所在的领地。 |
| `/claim list` | 列出你的所有领地。 |
| `/claim trust <玩家> [名字]` | 信任玩家。作用于你当前所在（或指定名字）的领地，仅主人（或 OP）可操作。 |
| `/claim untrust <玩家> [名字]` | 取消信任。同上。 |
| `/claim border show [秒数] [颜色]` | 开启边框显示。可延时自动关闭、可选颜色。 |
| `/claim border hide` | 关闭边框显示。 |
| `/claim cancel` | 清除当前选区。 |

颜色取值：`red / orange / yellow / green / cyan / blue / purple / pink / white`。

### 选区方法

1. 手持 **金斧**。
2. **左键点击**一个方块 → 设为第一个角点。
3. **右键点击**另一个方块 → 设为第二个角点（自动回显坐标与尺寸）。
4. `/claim create [名字]` → 圈地完成。

> 选区操作兼容 tweakeroo 的 freecamera 模式：服务端只依据你发出的攻击/使用方块事件处理，
> 不校验相机位置，因此 freecamera 下同样可以选点。

### 边框显示

- 边框用**粒子**绘制（只发给你一个人，原版客户端即可看到），每 `borderRefreshTicks` 刷新一次。
- 显示你**当前选区**的完整立体框 + 你**周围 `borderRadiusBlocks` 方块内所有领地**的轮廓。
- 每个玩家的显示颜色互不相同；若你指定的颜色已被他人占用，会自动改派一个未占用色。
- `/claim border show 30 red` = 红色显示 30 秒后自动关闭；`/claim border show` = 一直显示直到 `/claim border hide`。

---

## 保护规则

- 领地是**某个维度内的 X/Z 矩形**，Y 轴覆盖整个世界高度。
- **主人**与**信任玩家**：领地内完全等同原版，无任何额外限制（红石/活塞/水流/爆炸/TNT/生物等一律不干预）。
- **其它玩家**：被完全阻止——
  - 破坏方块、放置方块；
  - 使用方块（箱子、门、按钮、拉杆、工作台等）；
  - 使用物品（倒水桶、吃食物等，位于领地内时）；
  - 攻击实体、使用实体（骑乘、开马/驴箱、交易、剪羊毛等）；
  - **进入领地**（每 tick 检测，进入无权限领地会被弹回上一个安全位置或重生点）。
- OP（权限等级 ≥ 2）默认绕过保护（可用配置 `opsBypass` 关闭）。

---

## 配置

文件：`config/claims/config.json`（首次运行自动生成）

```json
{
  "enableProtection": true,
  "opsBypass": true,
  "maxClaimsPerPlayer": 5,
  "maxClaimSideLength": 256,
  "borderRefreshTicks": 4,
  "borderParticleSpacing": 0.5,
  "borderRadiusBlocks": 64
}
```

| 字段 | 说明 |
|---|---|
| `enableProtection` | 总开关：是否启用保护。 |
| `opsBypass` | OP（权限等级 ≥ 2）是否绕过保护。 |
| `maxClaimsPerPlayer` | 每人最多领地数。 |
| `maxClaimSideLength` | 领地单边最大长度（X 或 Z 方块数）。 |
| `borderRefreshTicks` | 边框粒子刷新间隔（游戏刻，20 刻 = 1 秒）。 |
| `borderParticleSpacing` | 边框粒子间距（方块）。越小越密、越耗带宽。 |
| `borderRadiusBlocks` | 边框渲染半径（玩家周围多少方块内的领地会被画出）。 |

## 持久化

- 领地数据存于 `config/claims/claims.json`，服务端停止及每次增删/信任变更时自动保存。
- 信任名单与领地一并持久化。

---

## 构建

需要：**JDK 25**（不是 JRE）、Gradle **9.x**（或 IntelliJ IDEA 自带的 Gradle）、可访问 Maven 的网络。

> **方式零（推荐，GitHub Actions 自动编译）**：本机无需装 JDK/Gradle，把项目推到 GitHub 即可自动 `gradle build` 并产出 jar，
> 详见 [BUILD_ON_GITHUB.md](BUILD_ON_GITHUB.md)。

方式一（命令行）：

```bash
# 先安装 JDK 25 与 Gradle 9，然后：
gradle build
# 产物在 build/libs/claims-1.0.0.jar
```

方式二（推荐，IntelliJ IDEA）：

1. 用 IntelliJ IDEA 打开本项目根目录（`build.gradle` 所在目录）。
2. 在设置中把 Gradle JVM 指到 JDK 25（`Settings → Build Tools → Gradle → Gradle JVM`）。
3. 点击右侧 Gradle 面板的 `Tasks → build → build`。

> 本项目未附带 `gradle-wrapper.jar` 二进制。如需 wrapper，在装有 Gradle 的机器上执行
> `gradle wrapper --gradle-version 9.5.1` 即可生成（26.2 官方模板即用 Gradle 9.5.1）。

### 关于映射（Mojmap）

26.2 起 Fabric/Loom **默认使用 Mojang 官方映射（Mojmap）**，且**不再提供 Yarn 映射**
（`meta.fabricmc.net/v2/versions/yarn/26.2` 返回空数组）。因此本项目 **`build.gradle` 中不声明 `mappings`**，
源码直接使用 Mojmap 类名（`ServerPlayer`、`Level`、`Component`、`Identifier`、`ResourceKey` 等），
已按真实 26.2 API 编译验证通过。

---

## 安装到服务器

1. 在服务器上安装 Fabric（Loader **0.19.3**）与 Fabric API（**0.158.0+26.2**）。
2. 把 `build/libs/claims-1.0.0.jar` 丢进服务器的 `mods/` 目录。
3. 启动服务器。客户端保持原版即可连接。

---

## 注意事项 / 已知限制

- 领地是**全高度**（Y 覆盖整个世界高度）的 X/Z 矩形，不是任意 3D 立体。
- **金斧在本模组中被占用作选区工具**：手持它左右键会触发选点，而不是正常砍树。
  若想正常使用金斧，请换成其它工具（或把选区工具做成可配置，见下）。
- 边框是**粒子**效果，非实线：在快速移动或高延迟下可能有断续；这是“纯服务端 + 原版客户端”下唯一可行的边框方案。
- 领地不可重叠；创建时若与已有领地相交会被拒绝。
- 信任按 **UUID** 存储，玩家改名不影响信任关系。

---

## 项目结构

```
src/main/java/com/mcmod/claims/
├─ ClaimsMod.java                 # 入口
├─ config/ClaimsConfig.java       # 配置读写
├─ storage/ClaimStore.java        # 领地数据 + JSON 持久化
├─ model/Claim.java               # 领地模型
├─ model/Selection.java           # 选区模型
├─ selection/SelectionManager.java
├─ border/BorderRenderer.java     # 粒子边框渲染
├─ protection/ProtectionHandler.java
└─ command/ClaimCommand.java      # /claim 命令树
src/main/resources/fabric.mod.json
```
