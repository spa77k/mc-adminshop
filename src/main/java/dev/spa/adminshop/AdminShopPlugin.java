package dev.spa.adminshop;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** 管理者が無限在庫で商品を売るショップ。 */
public final class AdminShopPlugin extends JavaPlugin {

    private ShopConfig shopConfig;
    private PerkItems perkItems;
    private EconomyService economy;
    private ActivityLog activityLog;
    private ShopGui shopGui;
    private PurchaseService purchases;
    private ServerBoostService boosts;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.shopConfig = ShopConfig.load(this);
        this.perkItems = new PerkItems(this);

        this.economy = EconomyService.hook(this);
        if (economy == null) {
            getLogger().severe("Vault に経済プラグインが登録されていません。AdminShop を無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.activityLog = ActivityLog.open(this);
        this.purchases = new PurchaseService(this);
        this.shopGui = new ShopGui(this);
        this.boosts = new ServerBoostService(this);
        boosts.load();

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new BoostListener(this), this);
        getServer().getPluginManager().registerEvents(new GrowthBoostListener(this), this);
        getServer().getPluginManager().registerEvents(new SmeltBoostListener(this), this);

        register("shop", new ShopCommand(this), null);
        AdminCommand adminCommand = new AdminCommand(this);
        register("ashop", adminCommand, adminCommand);

        getLogger().info("商品 " + shopConfig.items().size() + " 件を読み込みました。");
    }

    @Override
    public void onDisable() {
        if (boosts != null) {
            boosts.shutdown();
        }
    }

    private void register(String name, org.bukkit.command.CommandExecutor executor,
                          org.bukkit.command.TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("コマンド " + name + " が plugin.yml にありません。");
            return;
        }
        command.setExecutor(executor);
        if (completer != null) {
            command.setTabCompleter(completer);
        }
    }

    void reloadShopConfig() {
        this.shopConfig = ShopConfig.load(this);
    }

    ShopConfig shopConfig() {
        return shopConfig;
    }

    PerkItems perkItems() {
        return perkItems;
    }

    EconomyService economy() {
        return economy;
    }

    ActivityLog activityLog() {
        return activityLog;
    }

    ShopGui shopGui() {
        return shopGui;
    }

    PurchaseService purchases() {
        return purchases;
    }

    ServerBoostService boosts() {
        return boosts;
    }
}
