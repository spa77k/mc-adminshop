package dev.spa.adminshop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 購入と発動の記録。1行1件のJSON（JSON Lines）で追記する。
 *
 * SQLite を使わないのは、sqlite-jdbc のネイティブ関数名が org.sqlite に固定されていて、
 * 単一JARにまとめる際の再配置と両立しないため。記録は追記しかしないので、行単位のテキストで足りる。
 *
 * 記録が取れなくても遊ぶこと自体は続けられるため、書き込みに失敗しても警告を出すだけにする。
 */
final class ActivityLog {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final JavaPlugin plugin;
    private final Path purchasesFile;
    private final Path activationsFile;
    private final Path boostsFile;

    private ActivityLog(JavaPlugin plugin, Path purchasesFile, Path activationsFile, Path boostsFile) {
        this.plugin = plugin;
        this.purchasesFile = purchasesFile;
        this.activationsFile = activationsFile;
        this.boostsFile = boostsFile;
    }

    /** 記録先を用意できなければ null を返す。 */
    static ActivityLog open(JavaPlugin plugin) {
        Path folder = plugin.getDataFolder().toPath();
        try {
            Files.createDirectories(folder);
        } catch (IOException exception) {
            plugin.getLogger().warning("記録用のフォルダを作れませんでした。記録なしで動作します: " + exception.getMessage());
            return null;
        }
        return new ActivityLog(plugin, folder.resolve("purchases.log"),
                folder.resolve("activations.log"), folder.resolve("boosts.log"));
    }

    void logPurchase(UUID uuid, String name, String itemId, int amount, double price) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("at", now());
        fields.put("player", name);
        fields.put("uuid", uuid.toString());
        fields.put("item", itemId);
        fields.put("amount", amount);
        fields.put("price", price);
        append(purchasesFile, fields);
    }

    void logActivation(UUID uuid, String name, String perk, int keptItems, Location location, String killer) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("at", now());
        fields.put("player", name);
        fields.put("uuid", uuid.toString());
        fields.put("perk", perk);
        fields.put("kept", keptItems);
        fields.put("world", location.getWorld() == null ? "unknown" : location.getWorld().getName());
        fields.put("x", location.getBlockX());
        fields.put("y", location.getBlockY());
        fields.put("z", location.getBlockZ());
        fields.put("killer", killer);
        append(activationsFile, fields);
    }

    void logBoost(UUID uuid, String name, String perk, int minutes, int level, long endsAt) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("at", now());
        fields.put("player", name);
        fields.put("uuid", uuid.toString());
        fields.put("perk", perk);
        fields.put("minutes", minutes);
        fields.put("level", level);
        fields.put("ends_at", ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(endsAt), ZONE).format(TIMESTAMP));
        append(boostsFile, fields);
    }

    private String now() {
        return ZonedDateTime.now(ZONE).format(TIMESTAMP);
    }

    private void append(Path file, Map<String, Object> fields) {
        String line = toJson(fields) + System.lineSeparator();
        Runnable task = () -> {
            try {
                Files.writeString(file, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                plugin.getLogger().warning("記録の書き込みに失敗しました: " + exception.getMessage());
            }
        };
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            task.run();
        }
    }

    private static String toJson(Map<String, Object> fields) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append("\":");

            Object value = entry.getValue();
            if (value == null) {
                builder.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else {
                builder.append('"').append(escape(value.toString())).append('"');
            }
        }
        return builder.append('}').toString();
    }

    private static String escape(String raw) {
        StringBuilder builder = new StringBuilder(raw.length() + 8);
        for (int index = 0; index < raw.length(); index++) {
            char character = raw.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }
}
