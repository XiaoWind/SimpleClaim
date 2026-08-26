package com.mcmod.claims.selection;

import com.mcmod.claims.model.Selection;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每个玩家一份的选区状态。
 */
public final class SelectionManager {
    private final Map<UUID, Selection> selections = new HashMap<>();

    public Selection get(UUID id) {
        return selections.computeIfAbsent(id, k -> new Selection());
    }

    public void setPos1(UUID id, BlockPos pos, String world) {
        Selection s = get(id);
        s.pos1 = pos;
        s.world = world;
    }

    public void setPos2(UUID id, BlockPos pos, String world) {
        Selection s = get(id);
        s.pos2 = pos;
        s.world = world;
    }

    public void clear(UUID id) {
        selections.remove(id);
    }
}
