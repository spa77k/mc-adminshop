package dev.spa.adminshop;

import dev.spa.adminshop.event.ServerBoostActivatedEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * サーバー全体に一定時間だけ効く商品の共通の土台。種類ごとの違いは BoostType が持つ。
 *
 * 残り時間は現実の時間で数え、ファイルに書いておいて再起動をまたいでも引き継ぐ。現実時間で
 * 数えると約束しているため、サーバーが止まっていた間も時間は進む。
 *
 * 「使った本人への上乗せ」は、効果が続いている間に使った人全員（contributors）に与える。
 * 最後に使った人だけを優遇すると、先に買った人の取り分を後から来た人が奪う形になり、
 * 重ねがけを避ける動機が生まれてしまうため。効果が切れた時点で一覧は消える。
 */
final class ServerBoostService {

    private static final long ONE_MINUTE = 60_000L;
    private static final long UPDATE_INTERVAL_TICKS = 20L;

    /** ポーション効果をかけ直す長さ。更新の間隔より少し長くし、効果終了後は自然に消えるようにする。 */
    private static final int POTION_DURATION_TICKS = 60;

    private final AdminShopPlugin plugin;
    private final File stateFile;
    private final File legacyGrowthFile;
    private final Map<BoostType, Boost> boosts = new EnumMap<>(BoostType.class);

    private BukkitTask task;

    ServerBoostService(AdminShopPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "boosts.yml");
        this.legacyGrowthFile = new File(plugin.getDataFolder(), "growth-boost.yml");
    }

    /** 効果ひとつぶんの状態。 */
    private static final class Boost {
        long endsAt;
        /** ボスバーの進み具合を出すための基準。最後に開始または延長した時点の残り時間。 */
        long window;
        int level;
        final Set<UUID> contributors = new LinkedHashSet<>();
        BossBar bossBar;
    }

    /** 起動時に、前回の残り時間を引き継ぐ。 */
    void load() {
        if (stateFile.exists()) {
            YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
            for (BoostType type : BoostType.values()) {
                ConfigurationSection section = state.getConfigurationSection(type.perk());
                if (section != null) {
                    restore(type, section.getLong("ends-at", 0L), section.getInt("level", type.defaultLevel()),
                            section.getStringList("contributors"));
                }
            }
        }

        // 豊穣の鐘しかなかった頃の保存先。まだ効果が残っている間に更新した場合に取りこぼさない。
        if (!boosts.containsKey(BoostType.GROWTH) && legacyGrowthFile.exists()) {
            YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyGrowthFile);
            restore(BoostType.GROWTH, legacy.getLong("ends-at", 0L),
                    legacy.getInt("multiplier", BoostType.GROWTH.defaultLevel()), List.of());
        }

        for (Map.Entry<BoostType, Boost> entry : boosts.entrySet()) {
            plugin.getLogger().info(entry.getKey().itemName() + "の効果を引き継ぎました。残り "
                    + formatRemaining(entry.getValue().endsAt - System.currentTimeMillis()));
        }
        if (!boosts.isEmpty()) {
            startTicking();
        }
    }

    private void restore(BoostType type, long endsAt, int level, List<String> contributors) {
        if (endsAt <= System.currentTimeMillis()) {
            return;
        }
        Boost boost = new Boost();
        boost.endsAt = endsAt;
        boost.level = Math.max(1, level);
        boost.window = Math.max(1L, endsAt - System.currentTimeMillis());
        for (String raw : contributors) {
            try {
                boost.contributors.add(UUID.fromString(raw));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("保存された " + type.perk() + " の使用者IDを読めません: " + raw);
            }
        }
        boosts.put(type, boost);
    }

    boolean isActive(BoostType type) {
        Boost boost = boosts.get(type);
        return boost != null && boost.endsAt > System.currentTimeMillis();
    }

    /** 効果の強さ。倍率のものは倍率、ポーション効果のものは表示上のレベル。効果がなければ 0。 */
    int level(BoostType type) {
        Boost boost = boosts.get(type);
        return boost == null ? 0 : boost.level;
    }

    long remainingMillis(BoostType type) {
        Boost boost = boosts.get(type);
        return boost == null ? 0L : Math.max(0L, boost.endsAt - System.currentTimeMillis());
    }

    /** アイテムを使ったときの入口。使えたら true。 */
    boolean use(Player player, BoostType type, ItemStack stack) {
        ShopItem item = plugin.shopConfig().firstItemWithPerk(type.perk());
        int minutes = item == null ? type.defaultMinutes()
                : Math.max(1, item.option("duration-minutes", type.defaultMinutes()));
        int level = item == null ? type.defaultLevel()
                : Math.max(1, item.option(type.levelOption(), type.defaultLevel()));

        long now = System.currentTimeMillis();
        Boost existing = boosts.get(type);
        long base = Math.max(now, existing == null ? 0L : existing.endsAt);
        long newEndsAt = base + minutes * ONE_MINUTE;
        int maxMinutes = plugin.shopConfig().boostMaxTotalMinutes();
        long limit = now + maxMinutes * ONE_MINUTE;

        if (newEndsAt > limit) {
            player.sendMessage(Text.prefixed("&cこれ以上は効果を重ねられません。"
                    + "&7上限は " + maxMinutes + " 分です。"));
            player.sendMessage(Text.prefixed("&7現在の残り: &f" + formatRemaining(remainingMillis(type))));
            return false;
        }

        boolean wasActive = isActive(type);
        Boost boost = existing == null ? new Boost() : existing;
        boost.endsAt = newEndsAt;
        boost.level = level;
        boost.window = Math.max(1L, newEndsAt - now);
        boost.contributors.add(player.getUniqueId());
        boosts.put(type, boost);

        save();
        startTicking();

        stack.setAmount(stack.getAmount() - 1);
        announce(player, type, boost, minutes, wasActive);
        plugin.getServer().getPluginManager().callEvent(new ServerBoostActivatedEvent(
                player.getName(), type.itemName(), minutes,
                (int) Math.ceil(remainingMillis(type) / (double) ONE_MINUTE), boost.level, wasActive));

        ActivityLog activityLog = plugin.activityLog();
        if (activityLog != null) {
            activityLog.logBoost(player.getUniqueId(), player.getName(), type.perk(),
                    minutes, boost.level, boost.endsAt);
        }
        return true;
    }

    private void announce(Player player, BoostType type, Boost boost, int minutes, boolean wasActive) {
        String headline = wasActive
                ? "&e" + player.getName() + " &fが" + type.itemName() + "の効果を &a" + minutes + "分 &f延長しました。"
                : "&e" + player.getName() + " &fが&a" + type.itemName() + "&fを" + type.verb() + "。";

        plugin.getServer().broadcast(Text.prefixed(headline));
        plugin.getServer().broadcast(Text.prefixed(type.effectLine(boost.level)
                + " &7(残り " + formatRemaining(remainingMillis(type)) + ")"));

        for (Player online : plugin.getServer().getOnlinePlayers()) {
            online.playSound(online.getLocation(), type.sound(), 0.6F, 1.0F);
            showTo(online);
        }
    }

    /** 参加してきた人にもボスバーを見せる。 */
    void showTo(Player player) {
        for (Boost boost : boosts.values()) {
            if (boost.bossBar != null) {
                player.showBossBar(boost.bossBar);
            }
        }
    }

    private void startTicking() {
        if (plugin.shopConfig().boostBossBar()) {
            for (Map.Entry<BoostType, Boost> entry : boosts.entrySet()) {
                Boost boost = entry.getValue();
                if (boost.bossBar == null) {
                    boost.bossBar = BossBar.bossBar(Text.of(entry.getKey().subject()), 1.0F,
                            entry.getKey().barColor(), BossBar.Overlay.PROGRESS);
                    for (Player online : plugin.getServer().getOnlinePlayers()) {
                        online.showBossBar(boost.bossBar);
                    }
                }
            }
        }
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, UPDATE_INTERVAL_TICKS);
        }
        tick();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (BoostType type : BoostType.values()) {
            Boost boost = boosts.get(type);
            if (boost == null) {
                continue;
            }
            long remaining = boost.endsAt - now;
            if (remaining <= 0L) {
                expire(type, boost);
                continue;
            }
            if (boost.bossBar != null) {
                boost.bossBar.name(Text.of(type.barName(boost.level, formatRemaining(remaining))));
                boost.bossBar.progress(clamp((float) remaining / (float) boost.window));
            }
        }

        applyPotions();

        if (boosts.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * ポーション効果で実装するブーストを、居合わせた全員にかけ直す。
     *
     * 毎秒かけ直すのは、効果中に参加した人や死んで復活した人にも同じように効かせるため。
     * 自前で飲んだ強いポーションを上書きしてしまう心配は要らない。バニラは弱い効果で強い効果を
     * 置き換えず、いったん隠して後で戻す作りになっている。
     */
    private void applyPotions() {
        List<BoostType> active = new ArrayList<>();
        for (BoostType type : BoostType.values()) {
            if (type.potion() != null && isActive(type)) {
                active.add(type);
            }
        }
        if (active.isEmpty()) {
            return;
        }

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            for (BoostType type : active) {
                Boost boost = boosts.get(type);
                boolean contributor = boost.contributors.contains(player.getUniqueId());
                int amplifier = Math.max(0, boost.level - 1) + (contributor ? 1 : 0);
                player.addPotionEffect(effect(type.potion(), amplifier));
                if (contributor && type.contributorBonus() != null) {
                    player.addPotionEffect(effect(type.contributorBonus(), 0));
                }
            }
        }
    }

    /** 粒子は出さない（常時出ると視界が埋まる）が、HUDのアイコンは残して効果中だと分かるようにする。 */
    private static PotionEffect effect(PotionEffectType type, int amplifier) {
        return new PotionEffect(type, POTION_DURATION_TICKS, amplifier, true, false, true);
    }

    private void expire(BoostType type, Boost boost) {
        boosts.remove(type);
        hideBar(boost);
        save();
        plugin.getServer().broadcast(Text.prefixed("&7" + type.itemName() + "の効果が切れました。"
                + type.subject() + "は元に戻ります。"));
    }

    /** サーバー停止時。効果そのものは終わらせず、表示だけ片付ける。 */
    void shutdown() {
        save();
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Boost boost : boosts.values()) {
            hideBar(boost);
        }
    }

    private void hideBar(Boost boost) {
        if (boost.bossBar == null) {
            return;
        }
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            online.hideBossBar(boost.bossBar);
        }
        boost.bossBar = null;
    }

    private void save() {
        YamlConfiguration state = new YamlConfiguration();
        for (Map.Entry<BoostType, Boost> entry : boosts.entrySet()) {
            Boost boost = entry.getValue();
            String path = entry.getKey().perk();
            state.set(path + ".ends-at", boost.endsAt);
            state.set(path + ".level", boost.level);
            List<String> contributors = new ArrayList<>(boost.contributors.size());
            for (UUID uuid : boost.contributors) {
                contributors.add(uuid.toString());
            }
            state.set(path + ".contributors", contributors);
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("データフォルダを作れないため、効果の残り時間を保存できません。");
                return;
            }
            state.save(stateFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("効果の残り時間を保存できませんでした: " + exception.getMessage());
        }
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    static String formatRemaining(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%d:%02d", minutes, seconds);
    }
}
