package dev.spa.adminshop;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** 飲み終わったときだけ1個消費し、本人へバニラの持続効果を1種類付ける。 */
final class MysteryMedicineListener implements Listener {

    // 瞬間効果、襲撃の予兆、周囲へ生物を出す効果は含めない。
    // 持続する通常の効果を等確率で選び、強さはレベルI〜IIIにする。
    static final List<PotionEffectType> EFFECTS = List.of(
            PotionEffectType.SPEED, PotionEffectType.SLOWNESS,
            PotionEffectType.HASTE, PotionEffectType.MINING_FATIGUE,
            PotionEffectType.STRENGTH, PotionEffectType.JUMP_BOOST,
            PotionEffectType.NAUSEA, PotionEffectType.REGENERATION,
            PotionEffectType.RESISTANCE, PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.WATER_BREATHING, PotionEffectType.INVISIBILITY,
            PotionEffectType.BLINDNESS, PotionEffectType.NIGHT_VISION,
            PotionEffectType.HUNGER, PotionEffectType.WEAKNESS,
            PotionEffectType.POISON, PotionEffectType.WITHER,
            PotionEffectType.HEALTH_BOOST, PotionEffectType.ABSORPTION,
            PotionEffectType.GLOWING, PotionEffectType.LEVITATION,
            PotionEffectType.LUCK, PotionEffectType.UNLUCK,
            PotionEffectType.SLOW_FALLING, PotionEffectType.CONDUIT_POWER,
            PotionEffectType.DOLPHINS_GRACE, PotionEffectType.DARKNESS);

    private final AdminShopPlugin plugin;

    MysteryMedicineListener(AdminShopPlugin plugin) {
        this.plugin = plugin;
    }

    static PotionEffect roll(RandomGenerator random) {
        PotionEffectType type = EFFECTS.get(random.nextInt(EFFECTS.size()));
        int seconds = random.nextInt(60, 301);
        return new PotionEffect(type, seconds * 20, random.nextInt(3));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.isCancelled() || event.getItem().getType() != Material.POTION
                || !plugin.perkItems().hasPerk(event.getItem(), Perks.MYSTERY_MEDICINE)) {
            return;
        }
        var player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getHand());
        if (player.isDead() || held == null || !held.isSimilar(event.getItem()) || held.getAmount() < 1) {
            return;
        }
        // バニラの水瓶処理を止め、使用した手から1個だけ消す。クリエイティブでも消費する。
        event.setCancelled(true);
        ItemStack remaining = held.clone();
        remaining.setAmount(held.getAmount() - 1);
        player.getInventory().setItem(event.getHand(), remaining);

        PotionEffect effect = roll(ThreadLocalRandom.current());
        boolean applied = player.addPotionEffect(effect);
        player.sendMessage(Text.prefixed("&d怪しいお薬を飲みました。"));
        player.sendMessage(Component.translatable(effect.getType().translationKey())
                .append(Component.text(" " + (effect.getAmplifier() + 1) + " / "
                        + effect.getDuration() / 20 + "秒")));
        if (!applied) {
            player.sendMessage(Text.prefixed("&7同じ種類の強い効果が残っているため、そちらを優先しました。"));
        }
        if (plugin.activityLog() != null) {
            plugin.activityLog().logMedicine(player.getUniqueId(), player.getName(),
                    effect.getType().getKey().toString(), effect.getAmplifier() + 1,
                    effect.getDuration() / 20, applied);
        }
    }
}
