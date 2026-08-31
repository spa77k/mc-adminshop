package dev.spa.adminshop;

import java.io.File;
import java.io.IOException;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * 農作物の成長を速める効果。買った人だけでなくサーバー全体に効き、現実の時間で数える。
 *
 * 残り時間はファイルに書いておき、再起動をまたいでも引き継ぐ。現実時間で数えると約束しているため、
 * サーバーが止まっていた間も時間は進む。
 */
final class GrowthBoostService {

    private static final long ONE_MINUTE = 60_000L;
    private static final long UPDATE_INTERVAL_TICKS = 20L;

    private final AdminShopPlugin plugin;
    private final File stateFile;

    /** 効果が切れる時刻。0 なら効果なし。 */
    private long endsAt;

    /** ボスバーの進み具合を出すための基準。最後に開始または延長した時点の残り時間。 */
    private long window;

    private int multiplier = 2;
    private BossBar bossBar;
    private BukkitTask task;

    GrowthBoostService(AdminShopPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "growth-boost.yml");
    }

    /** 起動時に、前回の残り時間を引き継ぐ。 */
    void load() {
        if (!stateFile.exists()) {
            return;
        }
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
        long savedEndsAt = state.getLong("ends-at", 0L);
        if (savedEndsAt <= System.currentTimeMillis()) {
            return;
        }

        this.endsAt = savedEndsAt;
        this.multiplier = Math.max(1, state.getInt("multiplier", 2));
        this.window = Math.max(1L, savedEndsAt - System.currentTimeMillis());
        startTicking();

        plugin.getLogger().info("農作物の成長ブーストを引き継ぎました。残り "
                + formatRemaining(remainingMillis()));
    }

    boolean isActive() {
        return endsAt > System.currentTimeMillis();
    }

    int multiplier() {
        return multiplier;
    }

    long remainingMillis() {
        return Math.max(0L, endsAt - System.currentTimeMillis());
    }

    /** アイテムを使ったときの入口。使えたら true。 */
    boolean use(Player player, ItemStack stack) {
        ShopItem item = plugin.shopConfig().firstItemWithPerk(Perks.GROWTH_BOOST);
        int minutes = item == null ? 30 : Math.max(1, item.option("duration-minutes", 30));
        int requested = item == null ? 2 : Math.max(1, item.option("multiplier", 2));

        long now = System.currentTimeMillis();
        long base = Math.max(now, endsAt);
        long newEndsAt = base + minutes * ONE_MINUTE;
        long limit = now + plugin.shopConfig().boostMaxTotalMinutes() * ONE_MINUTE;

        if (newEndsAt > limit) {
            player.sendMessage(Text.prefixed("&cこれ以上は効果を重ねられません。"
                    + "&7上限は " + plugin.shopConfig().boostMaxTotalMinutes() + " 分です。"));
            player.sendMessage(Text.prefixed("&7現在の残り: &f" + formatRemaining(remainingMillis())));
            return false;
        }

        boolean wasActive = isActive();
        this.endsAt = newEndsAt;
        this.multiplier = requested;
        this.window = Math.max(1L, newEndsAt - now);
        save();
        startTicking();

        stack.setAmount(stack.getAmount() - 1);
        announce(player, minutes, wasActive);

        ActivityLog activityLog = plugin.activityLog();
        if (activityLog != null) {
            activityLog.logBoost(player.getUniqueId(), player.getName(), minutes, multiplier, endsAt);
        }
        return true;
    }

    private void announce(Player player, int minutes, boolean wasActive) {
        String headline = wasActive
                ? "&e" + player.getName() + " &fが効果を &a" + minutes + "分 &f延長しました。"
                : "&e" + player.getName() + " &fが&a豊穣の鐘&fを鳴らしました。";

        plugin.getServer().broadcast(Text.prefixed(headline));
        plugin.getServer().broadcast(Text.prefixed("&fサーバー全体の農作物の成長が &a"
                + multiplier + "倍 &fになります。&7(残り " + formatRemaining(remainingMillis()) + ")"));

        for (Player online : plugin.getServer().getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.BLOCK_BELL_USE, 0.6F, 1.0F);
            showTo(online);
        }
    }

    /** 参加してきた人にもボスバーを見せる。 */
    void showTo(Player player) {
        if (bossBar != null) {
            player.showBossBar(bossBar);
        }
    }

    private void startTicking() {
        if (bossBar == null && plugin.shopConfig().boostBossBar()) {
            bossBar = BossBar.bossBar(Text.of("&a農作物の成長"), 1.0F, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                online.showBossBar(bossBar);
            }
        }
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, UPDATE_INTERVAL_TICKS);
        }
        tick();
    }

    private void tick() {
        long remaining = remainingMillis();
        if (remaining <= 0L) {
            expire();
            return;
        }
        if (bossBar != null) {
            bossBar.name(Text.of("&a農作物の成長 " + multiplier + "倍  &f残り " + formatRemaining(remaining)));
            bossBar.progress(clamp((float) remaining / (float) window));
        }
    }

    private void expire() {
        endsAt = 0L;
        save();
        clearDisplay();
        plugin.getServer().broadcast(Text.prefixed("&7豊穣の鐘の効果が切れました。農作物の成長は元に戻ります。"));
    }

    /** サーバー停止時。効果そのものは終わらせず、表示だけ片付ける。 */
    void shutdown() {
        save();
        clearDisplay();
    }

    private void clearDisplay() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (bossBar != null) {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                online.hideBossBar(bossBar);
            }
            bossBar = null;
        }
    }

    private void save() {
        YamlConfiguration state = new YamlConfiguration();
        state.set("ends-at", endsAt);
        state.set("multiplier", multiplier);
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("データフォルダを作れないため、成長ブーストの残り時間を保存できません。");
                return;
            }
            state.save(stateFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("成長ブーストの残り時間を保存できませんでした: " + exception.getMessage());
        }
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    static String formatRemaining(long millis) {
        long totalSeconds = millis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%d:%02d", minutes, seconds);
    }
}
