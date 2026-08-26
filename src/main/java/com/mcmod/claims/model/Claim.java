package com.mcmod.claims.model;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 一个领地：某个维度内的轴对齐 X/Z 矩形，Y 覆盖整个世界高度。
 */
public final class Claim {
    public String name;
    public String owner;                       // 主人 UUID 字符串
    public String ownerName;                   // 主人名称（仅用于展示）
    public Set<String> trusted = new LinkedHashSet<>(); // 信任玩家 UUID 字符串
    public String world;                       // 维度标识，如 "minecraft:overworld"
    public int minX;
    public int minZ;
    public int maxX;
    public int maxZ;

    private transient UUID ownerId;

    public Claim() {
    }

    public Claim(String name, UUID owner, String ownerName, String world,
                 int minX, int minZ, int maxX, int maxZ) {
        this.name = name;
        this.owner = owner.toString();
        this.ownerName = ownerName;
        this.world = world;
        this.minX = Math.min(minX, maxX);
        this.maxX = Math.max(minX, maxX);
        this.minZ = Math.min(minZ, maxZ);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public UUID ownerId() {
        if (ownerId == null) {
            ownerId = UUID.fromString(owner);
        }
        return ownerId;
    }

    public boolean isOwner(UUID id) {
        return ownerId().equals(id);
    }

    public boolean isTrusted(UUID id) {
        return trusted.contains(id.toString());
    }

    public boolean isOwnerOrTrusted(UUID id) {
        return isOwner(id) || isTrusted(id);
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getZ());
    }

    /** 两个领地（同维度）的 X/Z 矩形是否相交。 */
    public boolean intersects(Claim other) {
        return world.equals(other.world)
                && minX <= other.maxX && maxX >= other.minX
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }

    public int sideX() {
        return maxX - minX + 1;
    }

    public int sideZ() {
        return maxZ - minZ + 1;
    }

    public long area() {
        return (long) sideX() * sideZ();
    }
}
