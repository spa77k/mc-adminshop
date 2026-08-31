package dev.spa.adminshop;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** /ashop give と /ashop reload。 */
final class AdminCommand implements CommandExecutor, TabCompleter {

    private final AdminShopPlugin plugin;

    AdminCommand(AdminShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("adminshop.admin")) {
            sender.sendMessage(Text.prefixed("&cこのコマンドを使う権限がありません。"));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadShopConfig();
                sender.sendMessage(Text.prefixed("&a設定を読み込み直しました。商品 "
                        + plugin.shopConfig().items().size() + " 件。"));
            }
            case "give" -> give(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Text.prefixed("&c使い方: /ashop give <プレイヤー> <商品ID> [個数]"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Text.prefixed("&cそのプレイヤーは接続していません: &f" + args[1]));
            return;
        }

        ShopItem item = plugin.shopConfig().item(args[2]);
        if (item == null) {
            sender.sendMessage(Text.prefixed("&cその商品IDはありません: &f" + args[2]));
            return;
        }

        int amount = item.amount();
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(Text.prefixed("&c個数は数字で指定してください: &f" + args[3]));
                return;
            }
            if (amount < 1 || amount > 64) {
                sender.sendMessage(Text.prefixed("&c個数は1〜64で指定してください。"));
                return;
            }
        }

        ShopItem copy = new ShopItem(item.id(), item.slot(), item.material(), item.displayName(),
                item.lore(), item.price(), amount, item.glow(), item.perk(), item.options());
        ItemStack stack = plugin.perkItems().create(copy);

        if (!target.getInventory().addItem(stack).isEmpty()) {
            sender.sendMessage(Text.prefixed("&c相手の持ち物がいっぱいで渡せませんでした。"));
            return;
        }

        sender.sendMessage(Text.prefixed("&a" + target.getName() + " へ " + item.id() + " を "
                + amount + " 個渡しました。"));
        target.sendMessage(Text.prefixed("&f" + item.displayName() + " &7を受け取りました。"));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Text.prefixed("&f/ashop give <プレイヤー> <商品ID> [個数]"));
        sender.sendMessage(Text.prefixed("&f/ashop reload"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("adminshop.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(List.of("give", "reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> ids = new ArrayList<>();
            for (ShopItem item : plugin.shopConfig().items()) {
                ids.add(item.id());
            }
            return filter(ids, args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> candidates, String prefix) {
        List<String> matched = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase().startsWith(prefix.toLowerCase())) {
                matched.add(candidate);
            }
        }
        return matched;
    }
}
