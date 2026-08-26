package com.mcmod.claims.command;

import com.mcmod.claims.ClaimsMod;
import com.mcmod.claims.border.BorderRenderer;
import com.mcmod.claims.model.Claim;
import com.mcmod.claims.model.Selection;
import com.mcmod.claims.protection.ProtectionHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * /claim 命令树。
 */
public final class ClaimCommand {
    private ClaimCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("claim")
                .then(Commands.literal("create")
                        .executes(ctx -> create(ctx, null))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("remove")
                        .executes(ctx -> remove(ctx, null))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> remove(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx, null))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("list")
                        .executes(ClaimCommand::list))
                .then(Commands.literal("trust")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> trust(ctx, EntityArgument.getPlayer(ctx, "player"), null))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> trust(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("untrust")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> untrust(ctx, EntityArgument.getPlayer(ctx, "player"), null))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> untrust(ctx, EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("border")
                        .then(Commands.literal("show")
                                .executes(ctx -> borderShow(ctx, 0, null))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(ctx -> borderShow(ctx, IntegerArgumentType.getInteger(ctx, "seconds"), null))
                                        .then(Commands.argument("color", StringArgumentType.word())
                                                .executes(ctx -> borderShow(ctx, IntegerArgumentType.getInteger(ctx, "seconds"),
                                                        StringArgumentType.getString(ctx, "color")))))
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .executes(ctx -> borderShow(ctx, 0, StringArgumentType.getString(ctx, "color")))))
                        .then(Commands.literal("hide")
                                .executes(ClaimCommand::borderHide)))
                .then(Commands.literal("cancel")
                        .executes(ClaimCommand::cancel)));
    }

    private static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    private static boolean canOp(ServerPlayer player) {
        return ProtectionHandler.isOp(player);
    }

    private static int create(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        Selection sel = ClaimsMod.SELECTIONS.get(player.getUUID());
        if (sel == null || !sel.complete()) {
            msg(player, "§c请先用金斧左键、金锄右键选择两个对角点");
            return 0;
        }
        if (sel.sideX() > ClaimsMod.CONFIG.maxClaimSideLength || sel.sideZ() > ClaimsMod.CONFIG.maxClaimSideLength) {
            msg(player, "§c领地单边长度不能超过 " + ClaimsMod.CONFIG.maxClaimSideLength);
            return 0;
        }
        if (!sel.world.equals(ClaimsMod.dimensionOf(player.level()))) {
            msg(player, "§c选区与当前所在维度不一致，请重新选择");
            return 0;
        }
        int owned = ClaimsMod.STORE.countByOwner(player.getUUID());
        if (owned >= ClaimsMod.CONFIG.maxClaimsPerPlayer) {
            msg(player, "§c你的领地数量已达上限 (" + ClaimsMod.CONFIG.maxClaimsPerPlayer + ")");
            return 0;
        }
        String claimName = (name != null && !name.isBlank()) ? name : ("claim-" + (owned + 1));
        Claim claim = new Claim(claimName, player.getUUID(), player.getGameProfile().name(),
                sel.world, sel.minX(), sel.minZ(), sel.maxX(), sel.maxZ());
        if (ClaimsMod.STORE.overlaps(claim)) {
            msg(player, "§c选区与已有领地重叠");
            return 0;
        }
        ClaimsMod.STORE.add(claim);
        ClaimsMod.SELECTIONS.clear(player.getUUID());
        msg(player, "§a已创建领地 §e" + claim.name + " §a范围 " + claim.sideX() + "x" + claim.sideZ());
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        Claim claim = resolveClaim(player, name);
        if (claim == null) {
            msg(player, "§c未找到领地（请站在领地内或指定名字）");
            return 0;
        }
        if (!claim.isOwner(player.getUUID()) && !canOp(player)) {
            msg(player, "§c你不是该领地的主人");
            return 0;
        }
        ClaimsMod.STORE.remove(claim);
        msg(player, "§a已删除领地 §e" + claim.name);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        Claim claim = resolveClaim(player, name);
        if (claim == null) {
            msg(player, "§c未找到领地");
            return 0;
        }
        sendInfo(player, claim);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        List<Claim> mine = ClaimsMod.STORE.byOwner(player.getUUID());
        if (mine.isEmpty()) {
            msg(player, "§7你还没有任何领地");
            return 0;
        }
        msg(player, "§a你的领地 (" + mine.size() + "):");
        for (Claim c : mine) {
            msg(player, "§7- §e" + c.name + " §7[" + c.world + "] §f(" + c.minX + "," + c.minZ
                    + ") → (" + c.maxX + "," + c.maxZ + ")  " + c.sideX() + "x" + c.sideZ());
        }
        return 1;
    }

    private static int trust(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String name)
            throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        Claim claim = resolveClaim(player, name);
        if (claim == null) {
            msg(player, "§c未找到领地");
            return 0;
        }
        if (!claim.isOwner(player.getUUID()) && !canOp(player)) {
            msg(player, "§c你不是该领地的主人");
            return 0;
        }
        if (target.getUUID().equals(player.getUUID())) {
            msg(player, "§c你本来就是该领地的主人，无需信任自己");
            return 0;
        }
        if (claim.trusted.add(target.getUUID().toString())) {
            ClaimsMod.STORE.save();
            msg(player, "§a已将 §e" + target.getGameProfile().name() + " §a加入领地 §e" + claim.name + " §a的信任名单");
        } else {
            msg(player, "§e" + target.getGameProfile().name() + " §7已在信任名单中");
        }
        return 1;
    }

    private static int untrust(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String name)
            throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        Claim claim = resolveClaim(player, name);
        if (claim == null) {
            msg(player, "§c未找到领地");
            return 0;
        }
        if (!claim.isOwner(player.getUUID()) && !canOp(player)) {
            msg(player, "§c你不是该领地的主人");
            return 0;
        }
        if (claim.trusted.remove(target.getUUID().toString())) {
            ClaimsMod.STORE.save();
            msg(player, "§a已将 §e" + target.getGameProfile().name() + " §a从领地 §e" + claim.name + " §a的信任名单移除");
        } else {
            msg(player, "§e" + target.getGameProfile().name() + " §7不在信任名单中");
        }
        return 1;
    }

    private static int borderShow(CommandContext<CommandSourceStack> ctx, int seconds, String colorName)
            throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        Integer color = null;
        if (colorName != null) {
            color = BorderRenderer.NAMED_COLORS.get(colorName.toLowerCase(Locale.ROOT));
            if (color == null) {
                msg(player, "§c未知颜色: " + colorName + "，可选: " + String.join(", ", BorderRenderer.NAMED_COLORS.keySet()));
                return 0;
            }
        }
        ClaimsMod.BORDERS.show(player, seconds, color);
        msg(player, "§a已开启领地边框显示" + (seconds > 0 ? "（" + seconds + " 秒后自动关闭）" : ""));
        return 1;
    }

    private static int borderHide(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        ClaimsMod.BORDERS.hide(player.getUUID());
        msg(player, "§a已关闭领地边框显示");
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        ClaimsMod.SELECTIONS.clear(player.getUUID());
        msg(player, "§a已清除选区");
        return 1;
    }

    private static Claim resolveClaim(ServerPlayer player, String name) {
        return name != null
                ? ClaimsMod.STORE.findByName(name)
                : ClaimsMod.STORE.findAt(player.blockPosition(), ClaimsMod.dimensionOf(player.level()), player.getUUID());
    }

    private static void sendInfo(ServerPlayer player, Claim c) {
        msg(player, "§6===== 领地 " + c.name + " =====");
        msg(player, "§7主人: §f" + c.ownerName);
        msg(player, "§7维度: §f" + c.world);
        msg(player, "§7范围: §f(" + c.minX + "," + c.minZ + ") → (" + c.maxX + "," + c.maxZ + ")  " + c.sideX() + "x" + c.sideZ());
        msg(player, "§7信任: §f" + namesOf(c.trusted, player.level().getServer()));
    }

    private static String namesOf(Set<String> uuids, MinecraftServer server) {
        if (uuids.isEmpty()) {
            return "（无）";
        }
        List<String> names = new ArrayList<>();
        for (String u : uuids) {
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(u));
            names.add(p != null ? p.getGameProfile().name() : u.substring(0, 8));
        }
        return String.join(", ", names);
    }

    private static void msg(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text));
    }
}
