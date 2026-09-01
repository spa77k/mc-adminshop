package dev.spa.adminshop;

import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

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
        ServerBoostService boosts = plugin.boosts();
        if (!boosts.isActive(BoostType.GROWTH)) {
            return;
        }

        int extraStages = boosts.level(BoostType.GROWTH) - 1;
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
}
