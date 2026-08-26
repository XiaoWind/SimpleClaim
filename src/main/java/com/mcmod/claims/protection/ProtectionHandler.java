package com.mcmod.claims.protection;

import com.mcmod.claims.ClaimsMod;
import com.mcmod.claims.model.Selection;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 保护拦截：阻止非主人/非信任玩家的破坏、放置、使用、攻击与进入。
 * 主人与信任玩家不经过任何额外干预，完全等同原版。
 */
public final class ProtectionHandler {
    private static final Map<UUID, SafePos> lastSafe = new HashMap<>();

    private ProtectionHandler() {
    }

    public static void register() {
        // 破坏方块（最终层，防止挖到完成）
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !isBlocked(player, world, pos));

        // 左键方块：金斧 = 选区；否则阻止开始破坏（含创造瞬间破坏）
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (isSelectionTool(player, hand)) {
                select(player, pos, true);
                return InteractionResult.FAIL;
            }
            return isBlocked(player, world, pos) ? InteractionResult.FAIL : InteractionResult.PASS;
        });

        // 右键方块：金斧 = 选区；否则阻止使用方块（箱子/门/按钮/放置等）
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (isSelectionTool(player, hand)) {
                select(player, hitResult.getBlockPos(), false);
                return InteractionResult.FAIL;
            }
            if (isBlocked(player, world, hitResult.getBlockPos())
                    || isBlocked(player, world, hitResult.getBlockPos().relative(hitResult.getDirection()))) {
                deny(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // 右键使用物品（吃东西、倒水桶、投掷等）
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (isSelectionTool(player, hand)) {
                return InteractionResult.PASS;
            }
            if (isBlocked(player, world, player.blockPosition())) {
                deny(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // 攻击实体
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (isBlocked(player, world, player.blockPosition()) || isBlocked(player, world, entity.blockPosition())) {
                deny(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // 使用实体（骑乘、打开马/驴箱、剪羊毛、交易等）
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (isBlocked(player, world, player.blockPosition()) || isBlocked(player, world, entity.blockPosition())) {
                deny(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    private static boolean isSelectionTool(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return stack.is(Items.GOLDEN_AXE);
    }

    private static void select(Player player, BlockPos pos, boolean first) {
        UUID id = player.getUUID();
        String world = ClaimsMod.dimensionOf(player.level());
        if (first) {
            ClaimsMod.SELECTIONS.setPos1(id, pos, world);
            player.sendOverlayMessage(Component.literal("§a已选择第一个点: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
        } else {
            ClaimsMod.SELECTIONS.setPos2(id, pos, world);
            Selection sel = ClaimsMod.SELECTIONS.get(id);
            String size = sel.complete() ? "  §e(" + sel.sideX() + "x" + sel.sideZ() + ")" : "";
            player.sendOverlayMessage(Component.literal("§a已选择第二个点: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + size));
        }
    }

    private static void deny(Player player) {
        player.sendOverlayMessage(Component.literal("§c你没有在此领地的权限"));
    }

    /** 该玩家在 world 的 pos 处是否被阻止。 */
    public static boolean isBlocked(Player player, Level world, BlockPos pos) {
        if (ClaimsMod.CONFIG == null || !ClaimsMod.CONFIG.enableProtection) return false;
        if (canBypass(player)) return false;
        return ClaimsMod.STORE.isProtectedAt(pos, ClaimsMod.dimensionOf(world), player.getUUID());
    }

    /** OP 是否绕过保护（可配置）。 */
    public static boolean canBypass(Player player) {
        return ClaimsMod.CONFIG != null && ClaimsMod.CONFIG.opsBypass
                && player instanceof ServerPlayer sp && isOp(sp);
    }

    public static boolean isOp(ServerPlayer player) {
        return player.level().getServer().getPlayerList().isOp(new NameAndId(player.getGameProfile()));
    }

    /**
     * 进入阻止：每 tick 检查所有玩家，若进入无权限领地则回退到上一个安全位置
     * （或重生点），实现“完全阻止进入”。
     */
    public static void tickMovement(MinecraftServer server) {
        if (ClaimsMod.CONFIG == null || !ClaimsMod.CONFIG.enableProtection) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (canBypass(player)) continue;
            String world = ClaimsMod.dimensionOf(player.level());
            BlockPos pos = player.blockPosition();

            if (ClaimsMod.STORE.isProtectedAt(pos, world, id)) {
                SafePos safe = lastSafe.get(id);
                boolean safeOk = safe != null && safe.world.equals(world)
                        && !ClaimsMod.STORE.isProtectedAt(BlockPos.containing(safe.x, safe.y, safe.z), world, id);

                if (safeOk) {
                    player.teleportTo(player.level(), safe.x, safe.y, safe.z,
                            Set.of(), player.getYRot(), player.getXRot(), false);
                } else {
                    BlockPos spawn = player.level().getRespawnData().pos();
                    player.teleportTo(player.level(),
                            spawn.getX() + 0.5, spawn.getY() + 0.5, spawn.getZ() + 0.5,
                            Set.of(), player.getYRot(), player.getXRot(), false);
                }
                deny(player);
            } else {
                lastSafe.put(id, SafePos.of(player));
            }
        }
    }

    private static final class SafePos {
        final String world;
        final double x;
        final double y;
        final double z;

        SafePos(String world, double x, double y, double z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static SafePos of(ServerPlayer p) {
            return new SafePos(ClaimsMod.dimensionOf(p.level()), p.getX(), p.getY(), p.getZ());
        }
    }
}
