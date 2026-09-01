package dev.spa.adminshop;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;

/**
 * 溶鉱の号鐘の効果。
 *
 * 焼き始めに決まる調理時間そのものを短くする。燃料の減りは変えない。燃料を長持ちさせる形にすると、
 * 燃料を余らせている人ほど得をして、石炭や木炭の需要をそのまま消してしまうため。
 *
 * 効き始めるのは、効果が続いている間に新しく焼き始めたぶんだけ。鳴らした時点ですでに焼けている
 * ものは元の時間のまま進む。逆に、効果が切れても焼き始めていたぶんは短いまま焼き上がる。
 * かまどのイベントには「誰のかまどか」の情報がないため、使った本人だけを速くすることはできない。
 */
final class SmeltBoostListener implements Listener {

    private final AdminShopPlugin plugin;

    SmeltBoostListener(AdminShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onStartSmelt(FurnaceStartSmeltEvent event) {
        ServerBoostService boosts = plugin.boosts();
        if (!boosts.isActive(BoostType.SMELT)) {
            return;
        }

        int multiplier = boosts.level(BoostType.SMELT);
        if (multiplier <= 1) {
            return;
        }

        // 0 にすると焼き上がりの判定が壊れるため、最短でも1ティックは残す。
        event.setTotalCookTime(Math.max(1, event.getTotalCookTime() / multiplier));
    }
}
