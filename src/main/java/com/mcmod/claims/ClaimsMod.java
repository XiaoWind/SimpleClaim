package com.mcmod.claims;

import com.mcmod.claims.border.BorderRenderer;
import com.mcmod.claims.command.ClaimCommand;
import com.mcmod.claims.config.ClaimsConfig;
import com.mcmod.claims.protection.ProtectionHandler;
import com.mcmod.claims.selection.SelectionManager;
import com.mcmod.claims.storage.ClaimStore;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 纯服务端领地保护模组入口。
 * 仅在专用服务器上运行（fabric.mod.json 中 environment=server）。
 */
public final class ClaimsMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "simpleclaim";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ClaimsConfig CONFIG;
    public static ClaimStore STORE;
    public static SelectionManager SELECTIONS;
    public static BorderRenderer BORDERS;

    @Override
    public void onInitializeServer() {
        CONFIG = ClaimsConfig.load();
        STORE = ClaimStore.load();
        SELECTIONS = new SelectionManager();
        BORDERS = new BorderRenderer();

        ProtectionHandler.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ClaimCommand.register(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ProtectionHandler.tickMovement(server);
            BORDERS.tick(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            STORE.save();
            CONFIG.save();
        });

        LOGGER.info("[simpleclaim] 已初始化，已加载 {} 个领地", STORE.all().size());
    }

    /** 返回世界维度的标识字符串，如 "minecraft:overworld"。 */
    public static String dimensionOf(Level world) {
        return world.dimension().identifier().toString();
    }
}
