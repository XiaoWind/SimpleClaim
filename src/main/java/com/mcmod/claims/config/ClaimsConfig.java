package com.mcmod.claims.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.mcmod.claims.ClaimsMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组配置，JSON 存储于 config/claims/config.json，力求极简。
 */
public final class ClaimsConfig {
    /** 总开关：是否启用领地保护。 */
    public boolean enableProtection = true;
    /** 服务器管理员（权限等级 >= 2）是否绕过保护。 */
    public boolean opsBypass = true;
    /** 每个玩家可拥有的最大领地数量。 */
    public int maxClaimsPerPlayer = 5;
    /** 领地单边最大长度（X 或 Z 方向方块数）。 */
    public int maxClaimSideLength = 256;
    /** 边框粒子刷新间隔（游戏刻）。 */
    public int borderRefreshTicks = 4;
    /** 边框粒子间距（方块）。 */
    public double borderParticleSpacing = 0.5;
    /** 边框渲染的半径（以玩家为中心渲染周围多少方块内的领地）。 */
    public int borderRadiusBlocks = 64;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public static ClaimsConfig load() {
        Path file = configFile();
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                ClaimsConfig cfg = GSON.fromJson(Files.readString(file), ClaimsConfig.class);
                if (cfg != null) {
                    cfg.sanitize();
                    return cfg;
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            ClaimsMod.LOGGER.error("[claims] 读取配置失败，使用默认配置", e);
        }
        ClaimsConfig cfg = new ClaimsConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        Path file = configFile();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException e) {
            ClaimsMod.LOGGER.error("[claims] 保存配置失败", e);
        }
    }

    private void sanitize() {
        maxClaimsPerPlayer = Math.max(1, maxClaimsPerPlayer);
        maxClaimSideLength = Math.max(1, maxClaimSideLength);
        borderRefreshTicks = Math.max(1, borderRefreshTicks);
        borderParticleSpacing = Math.max(0.1, borderParticleSpacing);
        borderRadiusBlocks = Math.max(8, borderRadiusBlocks);
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("claims").resolve("config.json");
    }
}
