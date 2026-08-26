package com.mcmod.claims.model;

import net.minecraft.core.BlockPos;

/**
 * 玩家的选区：两个对角点。
 */
public final class Selection {
    public BlockPos pos1;
    public BlockPos pos2;
    public String world; // 记录第一次点击时的维度，用于校验两点同维度

    public boolean complete() {
        return pos1 != null && pos2 != null && world != null;
    }

    public int minX() { return Math.min(pos1.getX(), pos2.getX()); }
    public int maxX() { return Math.max(pos1.getX(), pos2.getX()); }
    public int minY() { return Math.min(pos1.getY(), pos2.getY()); }
    public int maxY() { return Math.max(pos1.getY(), pos2.getY()); }
    public int minZ() { return Math.min(pos1.getZ(), pos2.getZ()); }
    public int maxZ() { return Math.max(pos1.getZ(), pos2.getZ()); }

    public int sideX() { return maxX() - minX() + 1; }
    public int sideY() { return maxY() - minY() + 1; }
    public int sideZ() { return maxZ() - minZ() + 1; }
}
