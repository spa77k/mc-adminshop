package dev.spa.adminshop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** 購入確認の画面。どの商品を確認しているかを持つ。 */
final class ConfirmHolder implements InventoryHolder {

    static final int BUY_SLOT = 11;
    static final int ITEM_SLOT = 13;
    static final int CANCEL_SLOT = 15;

    private final String itemId;
    private Inventory inventory;

    ConfirmHolder(String itemId) {
        this.itemId = itemId;
    }

    String itemId() {
        return itemId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
