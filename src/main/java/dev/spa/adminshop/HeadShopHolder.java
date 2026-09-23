package dev.spa.adminshop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** 装飾ヘッド一覧の画面。 */
final class HeadShopHolder implements InventoryHolder {
    static final int BACK_SLOT = 49;

    private Inventory inventory;

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
