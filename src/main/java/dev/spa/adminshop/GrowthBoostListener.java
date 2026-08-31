package dev.spa.adminshop;

import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 豊穣の鐘の効果。
 *
 * 成長を速めるのに randomTickSpeed は使わない。あれはサーバー全体のあらゆるランダム更新を速めるため、
 * 農作物だけでなく草の広がりや葉の枯れまで巻き込み、負荷が跳ね上がる。代わりに、作物が育つ瞬間に
 * 発生する BlockGrowEvent を拾って余分に段階を進める。処理が乗るのは実際に育った回数だけで済む。
 */
final class GrowthBoostListener implements Listener {

    private final AdminShopPlugin plugin;

    GrowthBoostListener(AdminShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        GrowthBoostService boost = plugin.growthBoost();
        if (!boost.isActive()) {
            return;
        }

        int extraStages = boost.multiplier() - 1;
        if (extraStages <= 0) {
            return;
        }

        BlockState newState = event.getNewState();
        if (!(newState.getBlockData() instanceof Ageable ageable)) {
            return;
        }

        int grown = Math.min(ageable.getMaximumAge(), ageable.getAge() + extraStages);
        if (grown == ageable.getAge()) {
            return;
        }
        ageable.setAge(grown);
        newState.setBlockData(ageable);
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
        if (!plugin.perkItems().hasPerk(stack, Perks.GROWTH_BOOST)) {
            return;
        }

        // 鐘が地面に設置されたり、右クリック本来の動作が起きたりしないよう必ず止める。
        event.setCancelled(true);
        plugin.growthBoost().use(event.getPlayer(), stack);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.growthBoost().showTo(event.getPlayer());
    }
}
