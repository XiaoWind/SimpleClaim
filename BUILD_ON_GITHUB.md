# 用 GitHub Actions 编译本模组

> ✅ 本模组源码已按真实 **Minecraft 26.2 + Fabric API 0.158.0+26.2** 编译验证通过（`BUILD SUCCESSFUL`），
> 产物为 `build/libs/claims-1.0.0.jar`。下面这套 GitHub Actions 流程是给“本机不便装 JDK/Gradle”时用的备选。

本机没有 JDK/Gradle/出网，但 GitHub Actions 的 Linux 运行器自带这些，且能访问 Maven 拉取 Minecraft/映射/Fabric 依赖。
**全程不需要把 Personal Access Token 交给任何人**，一切在 GitHub 上完成。

## 一次准备（在有 git + 网络的电脑上操作）

1. 到 github.com 新建一个**空仓库**（不要勾选 "Add a README"），例如 `mcmod-claims`。
2. 确认本机装了 git（Windows 用 Git for Windows；本项目的沙盒环境没装 git，需换到有 git 的机器）。
3. 打开终端，进入本项目根目录（包含 `build.gradle` 的那个目录），执行：

```bash
git init
git add -A
git commit -m "Claims: server-side claim protection mod"
git branch -M main
git remote add origin https://github.com/<你的用户名>/mcmod-claims.git
git push -u origin main
```

推送时按提示登录（用 GitHub 账号，或一个只有 `repo` 权限、用完即撤销的 PAT 即可）。

## 自动构建

推送后，GitHub 会自动运行 `.github/workflows/build.yml`：
检出代码 → 装 JDK 25 → 装 Gradle 9 → `gradle build` → 上传产物 jar。

查看方式：仓库页顶部 **Actions** 标签 → 点本次运行 → 拉到底部 **Artifacts** → 下载 `claims-jar`，
解压得到的 `claims-1.0.0.jar` 就是成品，丢进服务端 `mods/` 即可。

## 如果构建失败（首次很可能报 1~3 处错）

这正是本机无法联网编译而留下的待确认点。处理流程：

1. 点失败的步骤，展开日志；
2. 复制红色报错段（例如 `Could not resolve ... fabric-api:0.158.0+26.2` 或 `cannot find symbol: ServerPlayer`）；
3. 贴回给我，我据此精确修正。

## 常见报错速查

| 报错 | 处理 |
|---|---|
| Could not resolve `net.fabricmc.fabric-api:fabric-api:0.158.0+26.2` | `gradle.properties` 的 `fabric_version` 改为实际存在的版本（如 `0.156.0+26.2`） |
| Could not resolve plugin `fabric-loom:1.17-SNAPSHOT` | `build.gradle` 的 loom 版本改成存在的版本号 |
| cannot find symbol: `ServerPlayer` / `Level` / `Component` | 26.2 可能默认 Yarn 映射，改用 Yarn 类名或运行 `migrateMappings` |
| cannot find symbol: `DustParticleOptions(int,float)` | `BorderRenderer.dust()` 里把粒子构造换回 Vector3f 形式（有注释） |
| `teleportTo` 参数不匹配 | `ProtectionHandler` 里改成带 `Set<RelativeMovement>` 的重载或 `player.connection.teleport(...)` |
