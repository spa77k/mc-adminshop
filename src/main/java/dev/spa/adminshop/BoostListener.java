package dev.spa.adminshop;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** サーバー全体に効く商品の使用受付。効果の中身は種類ごとのリスナーか ServerBoostService が持つ。 */
final class BoostListener implements Listener {

    private final AdminShopPlugin plugin;

    BoostListener(AdminShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        // 両手ぶん発火するため、利き手のぶんだけを見る。
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack stack = event.getItem();
        BoostType type = BoostType.byPerk(plugin.perkItems().perkOf(stack));
        if (type == null) {
            return;
        }

        // 商品が地面に設置されたり、右クリック本来の動作が起きたりしないよう必ず止める。
        event.setCancelled(true);
        plugin.boosts().use(event.getPlayer(), type, stack);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.boosts().showTo(event.getPlayer());
    }
}
