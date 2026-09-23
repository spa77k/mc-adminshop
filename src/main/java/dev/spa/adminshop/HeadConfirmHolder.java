package dev.spa.adminshop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** 装飾ヘッドの購入確認画面。 */
final class HeadConfirmHolder implements InventoryHolder {
    private final String headId;
    private Inventory inventory;

    HeadConfirmHolder(String headId) {
        this.headId = headId;
    }

    String headId() {
        return headId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
