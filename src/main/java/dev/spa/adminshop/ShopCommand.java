package dev.spa.adminshop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /shop でショップを開く。 */
final class ShopCommand implements CommandExecutor {

    private final AdminShopPlugin plugin;

    ShopCommand(AdminShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはゲーム内から実行してください。");
            return true;
        }
        if (!player.hasPermission("adminshop.use")) {
            player.sendMessage(Text.prefixed("&cショップを利用する権限がありません。"));
            return true;
        }
        plugin.shopGui().openShop(player);
        return true;
    }
}
