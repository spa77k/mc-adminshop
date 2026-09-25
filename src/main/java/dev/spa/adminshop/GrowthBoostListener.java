package dev.spa.adminshop;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Sapling;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.entity.Player;

/**
 * 豊穣の鐘の効果。
 *
 * 成長を速めるのに randomTickSpeed は使わない。あれはサーバー全体のあらゆるランダム更新を速めるため、
 * 農作物だけでなく草の広がりや葉の枯れまで巻き込み、負荷が跳ね上がる。代わりに、作物が育つ瞬間に
 * 発生する BlockGrowEvent を拾って余分に段階を進める。サトウキビと苗木は成長段階だけでは
 * 速くならないため、稼働中のチャンクで自然のランダム更新を追加する。
 */
final class GrowthBoostListener implements Listener {

    private final AdminShopPlugin plugin;

    GrowthBoostListener(AdminShopPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickSugarCaneAndSaplings, 1L, 1L);
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
        BlockData blockData = newState.getBlockData();
        // サトウキビと苗木はランダム更新で速める。Ageable でもあるマングローブも二重処理しない。
        if (newState.getType() == Material.SUGAR_CANE || blockData instanceof Sapling) {
            return;
        }
        if (!(blockData instanceof Ageable ageable)) {
            return;
        }

        int grown = Math.min(ageable.getMaximumAge(), ageable.getAge() + extraStages);
        if (grown == ageable.getAge()) {
            return;
        }
        ageable.setAge(grown);
        newState.setBlockData(ageable);
    }

    private void tickSugarCaneAndSaplings() {
        ServerBoostService boosts = plugin.boosts();
        if (!boosts.isActive(BoostType.GROWTH)) {
            return;
        }

        int extraTicks = boosts.level(BoostType.GROWTH) - 1;
        if (extraTicks <= 0) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (World world : plugin.getServer().getWorlds()) {
            Integer randomTickSpeed = world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED);
            if (randomTickSpeed == null || randomTickSpeed <= 0) {
                continue;
            }

            List<Chunk> playerChunks = new ArrayList<>();
            for (Player player : world.getPlayers()) {
                playerChunks.add(player.getLocation().getChunk());
            }
            if (playerChunks.isEmpty()) {
                continue;
            }

            int simulationDistance = world.getSimulationDistance();
            int attemptsPerSection = randomTickSpeed * extraTicks;
            for (Chunk chunk : world.getLoadedChunks()) {
                if (!isNearPlayer(chunk, playerChunks, simulationDistance)) {
                    continue;
                }
                for (int sectionY = world.getMinHeight(); sectionY < world.getMaxHeight(); sectionY += 16) {
                    for (int attempt = 0; attempt < attemptsPerSection; attempt++) {
                        Block block = chunk.getBlock(random.nextInt(16), sectionY + random.nextInt(16),
                                random.nextInt(16));
                        Material type = block.getType();
                        if (type == Material.SUGAR_CANE || Tag.SAPLINGS.isTagged(type)
                                || type == Material.AZALEA || type == Material.FLOWERING_AZALEA) {
                            block.randomTick();
                        }
                    }
                }
            }
        }
    }

    private static boolean isNearPlayer(Chunk chunk, List<Chunk> playerChunks, int simulationDistance) {
        for (Chunk playerChunk : playerChunks) {
            if (Math.abs(chunk.getX() - playerChunk.getX()) <= simulationDistance
                    && Math.abs(chunk.getZ() - playerChunk.getZ()) <= simulationDistance) {
                return true;
            }
        }
        return false;
    }
}
