# Claims — 设计文档（领地保护 Fabric 服务端模组）

> 目标：Minecraft 26.2 · Fabric API 0.158.0+26.2 · Fabric Loader 0.19.3
> 形态：纯服务端模组（`environment = "server"`），原版客户端可直接连接。

## 1. 术语与数据模型

- **Claim（领地）**：一个轴对齐的 **X/Z 矩形**（由两个对角点确定），Y 轴覆盖整个世界高度（建筑范围全高）。
  属于某个**维度（dimension）**，只在该维度内生效。
- **Owner（主人）**：创建领地的玩家（按 UUID 标识）。主人天然拥有全部权限。
- **Trusted（信任玩家）**：主人通过 `/claim trust` 添加的其他玩家（按 UUID 标识），与主人拥有完全等同的权限。
- **Everyone else（其它玩家）**：被**完全阻止**：既不能进入领地，也不能在领地内破坏/放置/使用方块、攻击或交互实体。

## 2. 核心行为

### 2.1 选区（Selection）
- 手持**金斧**或**金锄**：
  - **左键点击方块**（攻击方块）→ 设置第一个角点 `pos1`。
  - **右键点击方块**（使用方块）→ 设置第二个角点 `pos2`。
- 选区交互会被拦截（不破坏方块、不犁地），并回显坐标提示。
- 兼容 tweakeroo 的 freecamera 模式：服务端仅依据玩家发出的攻击/使用方块事件处理，不依赖客户端相机位置校验，因此 freecamera 下选择同样生效。
- `/claim cancel` 清除选区。

### 2.2 领地边框显示
- 服务端通过 **粒子**（`DustParticleEffect`，RGB 颜色）向**单个玩家**绘制选区/领地边缘，原版客户端即可看到。
- 命令：`/claim border show [秒数] [颜色]`、`/claim border hide`。
- **颜色**：每个玩家分配唯一颜色；多人同时显示时不会撞色（若显式指定已被他人占用的颜色则自动改派一个未占用色）。
- **延时关闭**：`show` 可带秒数参数，到期自动关闭。
- 渲染由服务端定时任务驱动（按配置间隔刷新粒子），粒子带短生命周期自然消散。

### 2.3 保护拦截（仅对“非主人且非信任”玩家生效）
| 行为 | 拦截点 |
|---|---|
| 破坏方块 | `PlayerBlockBreakEvents.BEFORE` |
| 放置方块 | `PlayerBlockPlaceEvents`（若 26.2 提供；否则 `UseBlockCallback`/`UseItemCallback` 兜底） |
| 使用方块（箱子、门、按钮、拉杆等） | `UseBlockCallback` |
| 攻击实体（生物/玩家） | `AttackEntityCallback` |
| 使用实体（骑乘、打开马/驴箱、剪羊毛、交易等） | `UseEntityCallback` |
| 进入领地 | 服务端移动检查：每 tick 校验玩家位置，若进入无权限领地则回退到上次合法位置 |
- 权限判定顺序：OP（`hasPermissionLevel >= 2`，可配置关闭）→ 主人 → 信任 → 其它。

### 2.4 主人/信任玩家的“完全无限制”
- 不拦截任何红石、活塞、水流、爆炸、TNT、生物 AI、实体生成等；本模组**只**拦截 §2.3 列出的交互入口，其余全部走原版逻辑，不做任何额外干预。

## 3. 命令

| 命令 | 说明 |
|---|---|
| `/claim create [名字]` | 用当前选区创建领地（选中两个点）。可给领地起名，缺省自动命名。 |
| `/claim remove [名字]` | 删除领地。缺省删除你**当前所在**的领地；仅主人（或 OP）可删。 |
| `/claim info [名字]` | 查看领地信息（主人、信任列表、范围）。缺省显示你当前所在的领地。 |
| `/claim list` | 列出你的所有领地（OP 可看全部）。 |
| `/claim trust <玩家>` | 信任玩家。作用于你当前所在的领地（主人可指定 `[名字]`）。 |
| `/claim untrust <玩家>` | 取消信任。作用同上。 |
| `/claim border show [秒数] [颜色]` | 开启边框显示（可延时、可选色）。 |
| `/claim border hide` | 关闭边框显示。 |
| `/claim cancel` | 清除选区。 |

## 4. 配置（`config/claims/config.json`，力求极简）

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

> 边框默认**常显**（`/claim border show` 无秒数参数时一直显示，直到 `hide`）；`/claim border show <秒数>` 为延时自动关闭。

## 5. 持久化

- 领地数据序列化为 JSON，存于 `config/claims/claims.json`（按维度 id 分组），服务端停止/启动自动读写。
- 信任关系与领地数据一并持久化。

## 6. 项目结构

```
mcmod/
├─ build.gradle / settings.gradle / gradle.properties
├─ gradle/wrapper/…
├─ src/main/java/com/mcmod/claims/
│  ├─ ClaimsMod.java                 # 入口（注册命令/事件/定时任务）
│  ├─ config/ClaimsConfig.java       # 配置读写
│  ├─ storage/ClaimStore.java        # 领地持久化
│  ├─ model/Claim.java               # 领地模型 + 坐标计算
│  ├─ model/Selection.java           # 选区模型
│  ├─ selection/SelectionManager.java
│  ├─ border/BorderRenderer.java     # 粒子边框渲染
│  ├─ protection/ProtectionHandler.java
│  └─ command/ClaimCommand.java      # /claim 命令树
└─ src/main/resources/fabric.mod.json
```

> 具体类名/方法名以 26.2 的 Yarn 映射与 Fabric API 实际为准（见调研结论）。
