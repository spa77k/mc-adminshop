package dev.spa.adminshop;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 同じ人が同じ週に同じ商品を買うほど値上がりさせる。お金を多く持つ人ほど多く払う形にして、
 * 出回るお金を減らすのが目的。数えた個数は escalation.yml に残し、再起動しても引き継ぐ。
 *
 * 数えるのはショップでの購入だけで、/ashop give や他プラグインの報酬は含めない。
 */
final class PriceEscalation {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter WEEK_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AdminShopPlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, Integer>> counts = new HashMap<>();
    private String week = "";

    private PriceEscalation(AdminShopPlugin plugin, File file) {
        this.plugin = plugin;
        this.file = file;
    }

    static PriceEscalation load(AdminShopPlugin plugin) {
        PriceEscalation escalation = new PriceEscalation(plugin, new File(plugin.getDataFolder(), "escalation.yml"));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(escalation.file);
        escalation.week = yaml.getString("week", "");
        ConfigurationSection players = yaml.getConfigurationSection("counts");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                ConfigurationSection items = players.getConfigurationSection(key);
                if (items == null) continue;
                UUID uuid;
                try {
                    uuid = UUID.fromString(key);
                } catch (IllegalArgumentException exception) {
                    continue;
                }
                Map<String, Integer> bought = new HashMap<>();
                for (String itemId : items.getKeys(false)) {
                    bought.put(itemId, Math.max(0, items.getInt(itemId)));
                }
                escalation.counts.put(uuid, bought);
            }
        }
        return escalation;
    }

    /** 次に買うときの価格。値上がりしない商品は定価のまま。 */
    double price(UUID uuid, ShopItem item) {
        ShopConfig config = plugin.shopConfig();
        if (!config.escalates(item.id())) {
            return item.price();
        }
        return item.price() * config.escalationMultiplier(nextNumber(uuid, item.id()));
    }

    /** 次に買うのがその週の何個目か（1始まり）。 */
    int nextNumber(UUID uuid, String itemId) {
        rollWeek();
        return counts.getOrDefault(uuid, Map.of()).getOrDefault(itemId, 0) + 1;
    }

    void recordPurchase(UUID uuid, String itemId) {
        if (!plugin.shopConfig().escalates(itemId)) {
            return;
        }
        rollWeek();
        counts.computeIfAbsent(uuid, ignored -> new HashMap<>()).merge(itemId, 1, Integer::sum);
        save();
    }

    /** 価格がもとに戻る日時の表示（例: 10/12(月) 4:00）。 */
    String nextResetLabel() {
        ShopConfig config = plugin.shopConfig();
        ZonedDateTime next = weekStart(ZonedDateTime.now(ZONE))
                .atTime(config.escalationResetHour(), 0).atZone(ZONE).plusWeeks(1);
        String[] days = {"月", "火", "水", "木", "金", "土", "日"};
        return next.getMonthValue() + "/" + next.getDayOfMonth() + "(" + days[next.getDayOfWeek().getValue() - 1]
                + ") " + next.getHour() + ":00";
    }

    private void rollWeek() {
        String current = weekStart(ZonedDateTime.now(ZONE)).format(WEEK_FORMAT);
        if (current.equals(week)) {
            return;
        }
        week = current;
        if (!counts.isEmpty()) {
            counts.clear();
            save();
        }
    }

    /** 区切りの曜日・時刻から数えた、今週の始まりの日付。 */
    private LocalDate weekStart(ZonedDateTime now) {
        ShopConfig config = plugin.shopConfig();
        return now.minusHours(config.escalationResetHour()).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(config.escalationResetDay()));
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("week", week);
        for (Map.Entry<UUID, Map<String, Integer>> player : counts.entrySet()) {
            for (Map.Entry<String, Integer> item : player.getValue().entrySet()) {
                yaml.set("counts." + player.getKey() + "." + item.getKey(), item.getValue());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("escalation.yml を保存できませんでした: " + exception.getMessage());
        }
    }
}
