package com.mcmod.claims.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.mcmod.claims.ClaimsMod;
import com.mcmod.claims.model.Claim;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 领地数据仓库 + JSON 持久化（config/simpleclaim/claims.json）。
 */
public final class ClaimStore {
    private final List<Claim> claims = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final Type LIST_TYPE = new TypeToken<List<Claim>>() {}.getType();

    public List<Claim> all() {
        return claims;
    }

    public List<Claim> byOwner(UUID owner) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : claims) {
            if (c.isOwner(owner)) out.add(c);
        }
        return out;
    }

    public List<Claim> inWorld(String world) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : claims) {
            if (c.world.equals(world)) out.add(c);
        }
        return out;
    }

    public int countByOwner(UUID owner) {
        int n = 0;
        for (Claim c : claims) {
            if (c.isOwner(owner)) n++;
        }
        return n;
    }

    public Claim findByName(String name) {
        for (Claim c : claims) {
            if (c.name.equalsIgnoreCase(name)) return c;
        }
        return null;
    }

    /** 找到包含该位置的一个领地；若同时属于多个，优先返回玩家自己拥有的。 */
    public Claim findAt(BlockPos pos, String world, UUID player) {
        Claim first = null;
        for (Claim c : claims) {
            if (c.world.equals(world) && c.contains(pos)) {
                if (first == null) first = c;
                if (c.isOwner(player)) return c;
            }
        }
        return first;
    }

    /** 该位置是否被一个“该玩家无权访问”的领地覆盖。 */
    public boolean isProtectedAt(BlockPos pos, String world, UUID player) {
        for (Claim c : claims) {
            if (c.world.equals(world) && c.contains(pos) && !c.isOwnerOrTrusted(player)) {
                return true;
            }
        }
        return false;
    }

    public boolean overlaps(Claim candidate) {
        for (Claim c : claims) {
            if (c.intersects(candidate)) return true;
        }
        return false;
    }

    public void add(Claim c) {
        claims.add(c);
        save();
    }

    public boolean remove(Claim c) {
        boolean removed = claims.remove(c);
        if (removed) save();
        return removed;
    }

    public static ClaimStore load() {
        ClaimStore store = new ClaimStore();
        Path file = dataFile();
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                List<Claim> list = GSON.fromJson(Files.readString(file), LIST_TYPE);
                if (list != null) {
                    store.claims.addAll(list);
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            ClaimsMod.LOGGER.error("[simpleclaim] 读取领地数据失败", e);
        }
        return store;
    }

    public void save() {
        Path file = dataFile();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(claims));
        } catch (IOException e) {
            ClaimsMod.LOGGER.error("[simpleclaim] 保存领地数据失败", e);
        }
    }

    private static Path dataFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("simpleclaim").resolve("claims.json");
    }
}
