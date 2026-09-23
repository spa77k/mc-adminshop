package dev.spa.adminshop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** ショップ画面の操作。画面の中身は飾りなので、あらゆる持ち出しを禁じる。 */
final class GuiListener implements Listener {

    private final AdminShopPlugin plugin;

    GuiListener(AdminShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ShopHolder) && !(holder instanceof ConfirmHolder)
                && !(holder instanceof HeadShopHolder) && !(holder instanceof HeadConfirmHolder)) {
            return;
        }

        // 自分の持ち物側をクリックした場合も、シフトクリックで飾りが動くのを防ぐため一律で止める。
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        if (holder instanceof ShopHolder shop) {
            handleShopClick(player, shop, event.getSlot());
            return;
        }
        if (holder instanceof HeadShopHolder) {
            handleHeadShopClick(player, event.getSlot());
            return;
        }
        if (holder instanceof HeadConfirmHolder headConfirm) {
            handleHeadConfirmClick(player, headConfirm, event.getSlot());
            return;
        }
        handleConfirmClick(player, (ConfirmHolder) holder, event.getSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ShopHolder || holder instanceof ConfirmHolder
                || holder instanceof HeadShopHolder || holder instanceof HeadConfirmHolder) {
            event.setCancelled(true);
        }
    }

    private void handleShopClick(Player player, ShopHolder holder, int slot) {
        if (slot == holder.headSlot()) {
            plugin.shopGui().openHeadShop(player);
            return;
        }
        for (ShopItem item : plugin.shopConfig().items()) {
            if (item.slot() == slot) {
                plugin.shopGui().openConfirm(player, item);
                return;
            }
        }
    }

    private void handleHeadShopClick(Player player, int slot) {
        if (slot == HeadShopHolder.BACK_SLOT) {
            plugin.shopGui().openShop(player);
            return;
        }
        int index = 0;
        for (HeadItem head : plugin.headCatalog().items()) {
            if (index++ == slot) {
                plugin.shopGui().openHeadConfirm(player, head);
                return;
            }
        }
    }

    private void handleHeadConfirmClick(Player player, HeadConfirmHolder holder, int slot) {
        if (slot == ConfirmHolder.CANCEL_SLOT) {
            plugin.shopGui().openHeadShop(player);
            return;
        }
        if (slot != ConfirmHolder.BUY_SLOT) return;

        HeadItem head = plugin.headCatalog().item(holder.headId());
        player.closeInventory();
        if (head == null) {
            player.sendMessage(Text.prefixed("&cそのヘッドは現在取り扱っていません。"));
            return;
        }
        plugin.purchases().buyHead(player, head);
    }

    private void handleConfirmClick(Player player, ConfirmHolder holder, int slot) {
        if (slot == ConfirmHolder.CANCEL_SLOT) {
            plugin.shopGui().openShop(player);
            return;
        }
        if (slot != ConfirmHolder.BUY_SLOT) {
            return;
        }

        ShopItem item = plugin.shopConfig().item(holder.itemId());
        if (item == null) {
            // 確認画面を開いたあとに /ashop reload で商品が消えた場合。
            player.closeInventory();
            player.sendMessage(Text.prefixed("&cその商品は現在取り扱っていません。"));
            return;
        }

        player.closeInventory();
        plugin.purchases().buy(player, item);
    }
}
