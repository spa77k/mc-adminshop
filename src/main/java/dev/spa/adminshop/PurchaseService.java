package dev.spa.adminshop;

import java.util.Map;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** 購入手続き。支払いと受け渡しのどちらかが欠けた状態で終わらないようにする。 */
final class PurchaseService {

    private final AdminShopPlugin plugin;

    PurchaseService(AdminShopPlugin plugin) {
        this.plugin = plugin;
    }

    void buy(Player player, ShopItem item) {
        buy(player, item.id(), item.displayName(), item.price(), plugin.perkItems().create(item));
    }

    void buyHead(Player player, HeadItem head) {
        buy(player, head.id(), head.displayName(), plugin.shopConfig().headPrice(), head.create());
    }

    private void buy(Player player, String itemId, String displayName, double price, ItemStack stack) {
        ShopConfig config = plugin.shopConfig();
        EconomyService economy = plugin.economy();

        if (!player.hasPermission("adminshop.use")) {
            player.sendMessage(Text.prefixed("&cショップを利用する権限がありません。"));
            return;
        }

        if (!economy.has(player, price)) {
            player.sendMessage(Text.prefixed("&c所持金が足りません。&f必要: &e" + config.formatMoney(price)
                    + " &f所持: &e" + config.formatMoney(economy.balance(player))));
            return;
        }

        // 受け取れないまま代金だけ引かれる事故を避けるため、空きを先に確かめる。
        if (player.getInventory().firstEmpty() < 0) {
            player.sendMessage(Text.prefixed("&c持ち物がいっぱいです。1枠以上空けてから購入してください。"));
            return;
        }

        if (!economy.withdraw(player, price)) {
            player.sendMessage(Text.prefixed("&c支払いに失敗しました。時間をおいて試してください。"));
            return;
        }

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            // ここへ来るのは空き確認をすり抜けた場合だけ。代金は戻す。
            economy.deposit(player, price);
            player.sendMessage(Text.prefixed("&c商品を渡せなかったため、代金を返金しました。"));
            plugin.getLogger().warning(player.getName() + " へ " + itemId + " を渡せなかったため返金しました。");
            return;
        }

        player.sendMessage(Text.prefixed("&f" + displayName + " &7を購入しました。&8("
                + config.formatMoney(price) + ")"));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8F, 1.2F);

        ActivityLog activityLog = plugin.activityLog();
        if (activityLog != null) {
            activityLog.logPurchase(player.getUniqueId(), player.getName(), itemId, stack.getAmount(), price);
        }
    }
}
