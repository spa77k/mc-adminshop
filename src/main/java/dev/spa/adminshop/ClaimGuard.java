package dev.spa.adminshop;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * GriefPrevention の土地保護を確かめる。
 *
 * GriefPrevention を依存に加えないよう、リフレクションで呼ぶ。プラグインが入っていなければ何も制限しない。
 * 呼び出しに失敗したときは、他人の土地を荒らす側へ倒れないよう「許可しない」を返す。
 */
final class ClaimGuard {

    private final AdminShopPlugin plugin;

    ClaimGuard(AdminShopPlugin plugin) {
        this.plugin = plugin;
    }

    /** 土地の中の生き物を触る権限（コンテナ信頼）が無ければ true。 */
    boolean deniesContainers(Player player, Location location) {
        return denies(player, location, "allowContainers", null);
    }

    /** 土地の中へ生き物を出す権限（建築信頼）が無ければ true。 */
    boolean deniesBuild(Player player, Location location) {
        return denies(player, location, "allowBuild", Material.DIRT);
    }

    private boolean denies(Player player, Location location, String methodName, Material material) {
        Plugin griefPrevention = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (griefPrevention == null || !griefPrevention.isEnabled()) {
            return false;
        }
        try {
            Object dataStore = griefPrevention.getClass().getField("dataStore").get(griefPrevention);
            Method getClaimAt = dataStore.getClass()
                    .getMethod("getClaimAt", Location.class, boolean.class, Class.forName(
                            "me.ryanhamshire.GriefPrevention.Claim"));
            Object claim = getClaimAt.invoke(dataStore, location, false, null);
            if (claim == null) {
                return false;
            }
            Object result;
            if (material == null) {
                result = claim.getClass().getMethod(methodName, Player.class).invoke(claim, player);
            } else {
                result = claim.getClass().getMethod(methodName, Player.class, Material.class)
                        .invoke(claim, player, material);
            }
            // 許可されているときは null、拒否されるときは理由の文字列が返る。
            return result != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("GriefPrevention の土地判定に失敗したため許可しません: " + e);
            return true;
        }
    }
}
