package dev.spa.adminshop;

import org.bukkit.inventory.ItemStack;

/** 見た目を固定した装飾ヘッド。 */
record HeadItem(String id, String displayName, ItemStack stack) {

    ItemStack create() {
        return stack.clone();
    }
}
