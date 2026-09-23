package dev.spa.adminshop;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;

/** 42種類のMHFヘッド。購入時に外部アカウントを照会せず、同じ見た目を渡す。 */
final class HeadCatalog {

    private final Map<String, HeadItem> items;

    private HeadCatalog(Map<String, HeadItem> items) {
        this.items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }

    static HeadCatalog load(JavaPlugin plugin) {
        YamlConfiguration yaml;
        try (InputStream resource = plugin.getResource("heads.yml")) {
            if (resource == null) throw new IllegalStateException("heads.yml がありません");
            yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("heads.yml を読めません", exception);
        }
        ConfigurationSection heads = yaml.getConfigurationSection("heads");
        if (heads == null || heads.getKeys(false).size() != 42) {
            throw new IllegalStateException("MHFヘッドは42種類必要です");
        }

        Map<String, HeadItem> result = new LinkedHashMap<>();
        for (String id : heads.getKeys(false)) {
            ConfigurationSection entry = heads.getConfigurationSection(id);
            if (entry == null || !id.matches("MHF_[A-Za-z0-9]+")) {
                throw new IllegalStateException("ヘッドIDが不正です: " + id);
            }
            try {
                String name = entry.getString("name");
                UUID uuid = UUID.fromString(entry.getString("uuid"));
                String hash = entry.getString("texture");
                if (name == null || name.isBlank() || hash == null || !hash.matches("[0-9a-f]{32,64}")) {
                    throw new IllegalArgumentException("名前またはテクスチャが不正です");
                }

                PlayerProfile profile = Bukkit.createPlayerProfile(uuid, id);
                profile.getTextures().setSkin(URI.create(
                        "https://textures.minecraft.net/texture/" + hash).toURL());
                ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) stack.getItemMeta();
                meta.setOwnerProfile(profile);
                meta.displayName(Text.item("&e" + name));
                stack.setItemMeta(meta);
                result.put(id, new HeadItem(id, name, stack));
            } catch (Exception exception) {
                throw new IllegalStateException("ヘッドを読み込めません: " + id, exception);
            }
        }
        return new HeadCatalog(result);
    }

    Collection<HeadItem> items() {
        return new ArrayList<>(items.values());
    }

    HeadItem item(String id) {
        return items.get(id);
    }
}
