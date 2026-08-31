package dev.spa.adminshop;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * 商品アイテムの生成と判定。
 *
 * 効果の種類は PersistentDataContainer に書き込み、判定もそこだけを見る。表示名やロアを見ないのは、
 * 金床で同じ名前を付けただけの偽物を護符として認めないため。
 */
final class PerkItems {

    private final NamespacedKey perkKey;

    PerkItems(Plugin plugin) {
        this.perkKey = new NamespacedKey(plugin, "perk");
    }

    ItemStack create(ShopItem item) {
        ItemStack stack = new ItemStack(item.material(), item.amount());
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Text.item(item.displayName()));

        List<Component> lore = new ArrayList<>();
        for (String line : item.lore()) {
            lore.add(Text.item(line));
        }
        meta.lore(lore);

        if (item.glow()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.getPersistentDataContainer().set(perkKey, PersistentDataType.STRING, item.perk());
        stack.setItemMeta(meta);
        return stack;
    }

    /** 効果を持たないアイテムなら null を返す。 */
    String perkOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer container = stack.getItemMeta().getPersistentDataContainer();
        return container.get(perkKey, PersistentDataType.STRING);
    }

    boolean hasPerk(ItemStack stack, String perk) {
        return perk.equals(perkOf(stack));
    }
}
