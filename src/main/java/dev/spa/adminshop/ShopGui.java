package dev.spa.adminshop;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** ショップの画面を組み立てる。統合版から見ても崩れないよう、チェスト型の枠だけを使う。 */
final class ShopGui {

    private static final int CONFIRM_SIZE = 27;

    private final AdminShopPlugin plugin;

    ShopGui(AdminShopPlugin plugin) {
        this.plugin = plugin;
    }

    void openShop(Player player) {
        ShopConfig config = plugin.shopConfig();
        ShopHolder holder = new ShopHolder();
        Inventory inventory = plugin.getServer().createInventory(holder, config.size(), Text.of(config.title()));
        holder.setInventory(inventory);

        for (ShopItem item : config.items()) {
            inventory.setItem(item.slot(), display(item, player, config));
        }
        player.openInventory(inventory);
    }

    void openConfirm(Player player, ShopItem item) {
        ShopConfig config = plugin.shopConfig();
        ConfirmHolder holder = new ConfirmHolder(item.id());
        Inventory inventory = plugin.getServer().createInventory(holder, CONFIRM_SIZE, Text.of("&8本当に購入しますか？"));
        holder.setInventory(inventory);

        inventory.setItem(ConfirmHolder.BUY_SLOT, button(Material.LIME_CONCRETE, "&a購入する",
                List.of("&7" + config.formatMoney(item.price()) + " を支払う")));
        inventory.setItem(ConfirmHolder.ITEM_SLOT, display(item, player, config));
        inventory.setItem(ConfirmHolder.CANCEL_SLOT, button(Material.RED_CONCRETE, "&cやめる",
                List.of("&7ショップの一覧へ戻る")));

        player.openInventory(inventory);
    }

    /** 商品を画面に置くときの見た目。値札と所持金を末尾に足す。 */
    private ItemStack display(ShopItem item, Player player, ShopConfig config) {
        ItemStack stack = plugin.perkItems().create(item);
        ItemMeta meta = stack.getItemMeta();

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Text.item(""));
        lore.add(Text.item("&6価格: &f" + config.formatMoney(item.price())));
        lore.add(Text.item("&7所持金: &f" + config.formatMoney(plugin.economy().balance(player))));
        meta.lore(lore);

        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.item(name));

        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Text.item(line));
        }
        meta.lore(lines);

        stack.setItemMeta(meta);
        return stack;
    }
}
