package dev.spa.adminshop;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/** config.yml の内容を読み取って保持する。/ashop reload のたびに作り直す。 */
final class ShopConfig {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##");

    private final String title;
    private final int size;
    private final String currencySymbol;
    private final boolean currencySuffix;
    private final boolean protectOnPvpDeath;
    private final boolean keepArmor;
    private final boolean keepOffhand;
    private final boolean keepHotbar;
    private final boolean broadcast;
    private final int boostMaxTotalMinutes;
    private final boolean boostBossBar;
    private final Map<String, ShopItem> items;

    private ShopConfig(String title, int size, String currencySymbol, boolean currencySuffix,
                       boolean protectOnPvpDeath, boolean keepArmor, boolean keepOffhand,
                       boolean keepHotbar, boolean broadcast, int boostMaxTotalMinutes,
                       boolean boostBossBar, Map<String, ShopItem> items) {
        this.title = title;
        this.size = size;
        this.currencySymbol = currencySymbol;
        this.currencySuffix = currencySuffix;
        this.protectOnPvpDeath = protectOnPvpDeath;
        this.keepArmor = keepArmor;
        this.keepOffhand = keepOffhand;
        this.keepHotbar = keepHotbar;
        this.broadcast = broadcast;
        this.boostMaxTotalMinutes = boostMaxTotalMinutes;
        this.boostBossBar = boostBossBar;
        this.items = items;
    }

    static ShopConfig load(JavaPlugin plugin) {
        plugin.reloadConfig();
        var config = plugin.getConfig();

        String title = config.getString("shop.title", "&8[&6管理者ショップ&8]");
        int size = normalizeSize(config.getInt("shop.size", 27));

        Map<String, ShopItem> items = new LinkedHashMap<>();
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String id : itemsSection.getKeys(false)) {
                ConfigurationSection section = itemsSection.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                ShopItem item = readItem(plugin, id, section, size);
                if (item != null) {
                    items.put(id, item);
                }
            }
        }
        if (items.isEmpty()) {
            plugin.getLogger().warning("config.yml に有効な商品がひとつもありません。ショップは空のまま開きます。");
        }

        return new ShopConfig(
                title,
                size,
                config.getString("currency.symbol", "S"),
                config.getBoolean("currency.suffix", true),
                config.getBoolean("protection.protect-on-pvp-death", true),
                config.getBoolean("protection.keep-armor", true),
                config.getBoolean("protection.keep-offhand", true),
                config.getBoolean("protection.keep-hotbar", true),
                config.getBoolean("protection.broadcast", false),
                Math.max(1, config.getInt("growth-boost.max-total-minutes", 120)),
                config.getBoolean("growth-boost.boss-bar", true),
                items);
    }

    private static ShopItem readItem(JavaPlugin plugin, String id, ConfigurationSection section, int shopSize) {
        String materialName = section.getString("material", "");
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("商品 " + id + " の material が不正なため読み飛ばします: " + materialName);
            return null;
        }

        double price = section.getDouble("price", -1.0D);
        if (price < 0.0D) {
            plugin.getLogger().warning("商品 " + id + " の price が不正なため読み飛ばします: " + price);
            return null;
        }

        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= shopSize) {
            plugin.getLogger().warning("商品 " + id + " の slot が枠の外にあるため読み飛ばします: " + slot);
            return null;
        }

        String perk = section.getString("perk", "");
        if (!Perks.isKnown(perk)) {
            plugin.getLogger().warning("商品 " + id + " の perk は未実装のため読み飛ばします: " + perk);
            return null;
        }

        int amount = Math.max(1, Math.min(64, section.getInt("amount", 1)));
        List<String> lore = section.getStringList("lore");
        String displayName = section.getString("display-name", id);
        boolean glow = section.getBoolean("glow", false);

        Map<String, Object> options = new LinkedHashMap<>();
        ConfigurationSection optionSection = section.getConfigurationSection("perk-options");
        if (optionSection != null) {
            options.putAll(optionSection.getValues(false));
        }

        return new ShopItem(id, slot, material, displayName, lore, price, amount, glow, perk, options);
    }

    /** 9の倍数で9〜54に収める。 */
    private static int normalizeSize(int raw) {
        int rounded = ((raw + 8) / 9) * 9;
        return Math.max(9, Math.min(54, rounded));
    }

    String title() {
        return title;
    }

    int size() {
        return size;
    }

    boolean protectOnPvpDeath() {
        return protectOnPvpDeath;
    }

    boolean keepArmor() {
        return keepArmor;
    }

    boolean keepOffhand() {
        return keepOffhand;
    }

    boolean keepHotbar() {
        return keepHotbar;
    }

    boolean broadcast() {
        return broadcast;
    }

    /** 成長ブーストを重ねられる上限（分）。 */
    int boostMaxTotalMinutes() {
        return boostMaxTotalMinutes;
    }

    boolean boostBossBar() {
        return boostBossBar;
    }

    Collection<ShopItem> items() {
        return items.values();
    }

    ShopItem item(String id) {
        return items.get(id);
    }

    /** 効果から商品を逆に引く。使ったアイテムの持続時間や倍率を調べるのに使う。 */
    ShopItem firstItemWithPerk(String perk) {
        for (ShopItem item : items.values()) {
            if (item.perk().equals(perk)) {
                return item;
            }
        }
        return null;
    }

    String formatMoney(double amount) {
        String number = MONEY.format(amount);
        return currencySuffix ? number + currencySymbol : currencySymbol + number;
    }
}
