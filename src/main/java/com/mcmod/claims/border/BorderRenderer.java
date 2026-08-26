package com.mcmod.claims.border;

import com.mcmod.claims.ClaimsMod;
import com.mcmod.claims.model.Claim;
import com.mcmod.claims.model.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 通过只发给单个玩家的粒子绘制选区/领地边框（原版客户端即可看到）。
 * 每个玩家的显示颜色互不相同。
 */
public final class BorderRenderer {
    /** 颜色名 -> RGB */
    public static final Map<String, Integer> NAMED_COLORS = new LinkedHashMap<>();

    static {
        NAMED_COLORS.put("red", 0xFF5555);
        NAMED_COLORS.put("orange", 0xFFAA00);
        NAMED_COLORS.put("yellow", 0xFFFF55);
        NAMED_COLORS.put("green", 0x55FF55);
        NAMED_COLORS.put("cyan", 0x55FFFF);
        NAMED_COLORS.put("blue", 0x5555FF);
        NAMED_COLORS.put("purple", 0xAA55FF);
        NAMED_COLORS.put("pink", 0xFF55FF);
        NAMED_COLORS.put("white", 0xFFFFFF);
    }

    private static final int[] PALETTE = {
            0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF,
            0x5555FF, 0xAA55FF, 0xFF55FF, 0xFF88AA, 0xFFFFFF
    };

    private final Map<UUID, Session> sessions = new HashMap<>();
    private int tickCounter;

    /** 开启显示；seconds<=0 表示一直显示直到手动 hide。 */
    public void show(ServerPlayer player, int seconds, Integer requestedColor) {
        Session s = new Session();
        s.remainingTicks = seconds <= 0 ? -1L : seconds * 20L;
        s.color = assignColor(player.getUUID(), requestedColor);
        sessions.put(player.getUUID(), s);
    }

    public void hide(UUID id) {
        sessions.remove(id);
    }

    /** 分配一个不与其它正在显示的玩家冲突的颜色。 */
    private int assignColor(UUID id, Integer requested) {
        Set<Integer> used = new HashSet<>();
        for (Map.Entry<UUID, Session> e : sessions.entrySet()) {
            if (!e.getKey().equals(id)) {
                used.add(e.getValue().color);
            }
        }
        if (requested != null && !used.contains(requested)) {
            return requested;
        }
        for (int c : PALETTE) {
            if (!used.contains(c)) return c;
        }
        return PALETTE[Math.abs(id.hashCode()) % PALETTE.length];
    }

    public void tick(MinecraftServer server) {
        if (sessions.isEmpty()) return;
        tickCounter++;
        int refresh = Math.max(1, ClaimsMod.CONFIG.borderRefreshTicks);
        boolean render = tickCounter % refresh == 0;

        Iterator<Map.Entry<UUID, Session>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> e = it.next();
            Session s = e.getValue();
            if (s.remainingTicks > 0) {
                s.remainingTicks--;
                if (s.remainingTicks == 0) {
                    it.remove();
                    continue;
                }
            }
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) continue;
            if (render) {
                renderFor(player, s);
            }
        }
    }

    private void renderFor(ServerPlayer player, Session s) {
        ServerLevel level = player.level();
        String world = ClaimsMod.dimensionOf(level);
        double spacing = Math.max(0.2, ClaimsMod.CONFIG.borderParticleSpacing);

        // 1) 玩家正在进行的选区：画完整 3D 盒子
        Selection sel = ClaimsMod.SELECTIONS.get(player.getUUID());
        if (sel != null && sel.complete()) {
            drawBox(level, player, s.color,
                    sel.minX(), sel.minY(), sel.minZ(),
                    sel.maxX() + 1, sel.maxY() + 1, sel.maxZ() + 1,
                    spacing);
        }

        // 2) 玩家周围一定半径内的所有领地：画 3D 立方体边框
        int radius = Math.max(8, ClaimsMod.CONFIG.borderRadiusBlocks);
        BlockPos center = player.blockPosition();
        for (Claim c : ClaimsMod.STORE.inWorld(world)) {
            if (c.maxX < center.getX() - radius || c.minX > center.getX() + radius
                    || c.maxZ < center.getZ() - radius || c.minZ > center.getZ() + radius) {
                continue;
            }
            drawBox(level, player, s.color,
                    c.minX, c.minY, c.minZ,
                    c.maxX + 1, c.maxY + 1, c.maxZ + 1,
                    spacing);
        }
    }

    private void drawRectangle(ServerLevel level, ServerPlayer player, int rgb,
                               double x0, double z0, double x1, double z1,
                               double y, double spacing) {
        drawLine(level, player, rgb, x0, y, z0, x1, y, z0, spacing);
        drawLine(level, player, rgb, x1, y, z0, x1, y, z1, spacing);
        drawLine(level, player, rgb, x1, y, z1, x0, y, z1, spacing);
        drawLine(level, player, rgb, x0, y, z1, x0, y, z0, spacing);
    }

    private void drawBox(ServerLevel level, ServerPlayer player, int rgb,
                         double x0, double y0, double z0,
                         double x1, double y1, double z1,
                         double spacing) {
        drawRectangle(level, player, rgb, x0, z0, x1, z1, y0, spacing);
        drawRectangle(level, player, rgb, x0, z0, x1, z1, y1, spacing);
        drawLine(level, player, rgb, x0, y0, z0, x0, y1, z0, spacing);
        drawLine(level, player, rgb, x1, y0, z0, x1, y1, z0, spacing);
        drawLine(level, player, rgb, x1, y0, z1, x1, y1, z1, spacing);
        drawLine(level, player, rgb, x0, y0, z1, x0, y1, z1, spacing);
    }

    private void drawLine(ServerLevel level, ServerPlayer player, int rgb,
                          double x1, double y1, double z1,
                          double x2, double y2, double z2,
                          double spacing) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.max(1, Math.ceil(len / spacing));
        DustParticleOptions particle = dust(rgb);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            level.sendParticles(player, particle, true, true,
                    x1 + dx * t, y1 + dy * t, z1 + dz * t,
                    1, 0, 0, 0, 0);
        }
    }

    private static DustParticleOptions dust(int rgb) {
        return new DustParticleOptions(0xFF000000 | rgb, 1.0f);
    }

    private static final class Session {
        long remainingTicks;
        int color;
    }
}
