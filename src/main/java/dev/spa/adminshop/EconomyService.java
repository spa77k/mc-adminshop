package dev.spa.adminshop;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Vault 経由でサーバーの経済プラグイン（EssentialsX）を使う。 */
final class EconomyService {

    private final Economy economy;

    private EconomyService(Economy economy) {
        this.economy = economy;
    }

    /** 経済プラグインが登録されていなければ null を返す。 */
    static EconomyService hook(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> provider =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : new EconomyService(provider.getProvider());
    }

    boolean has(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    double balance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    boolean withdraw(OfflinePlayer player, double amount) {
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    /** 商品を渡せなかったときの払い戻しに使う。 */
    boolean deposit(OfflinePlayer player, double amount) {
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }
}
