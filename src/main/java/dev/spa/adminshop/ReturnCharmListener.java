package dev.spa.adminshop;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** 最後の死亡地点へ戻る使い切り商品。死亡記録は商品を持っていなくても更新する。 */
final class ReturnCharmListener implements Listener {
    private final AdminShopPlugin plugin;
    private final File file;
    private final YamlConfiguration deaths;
    private final Set<UUID> using = new HashSet<>();
    private static final Set<Material> HAZARDS = Set.of(Material.LAVA, Material.FIRE,
            Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE,
            Material.POWDER_SNOW, Material.POINTED_DRIPSTONE, Material.NETHER_PORTAL,
            Material.END_PORTAL, Material.END_GATEWAY);

    ReturnCharmListener(AdminShopPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "deaths.yml");
        deaths = YamlConfiguration.loadConfiguration(file);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Location location = event.getEntity().getLocation();
        String key = event.getEntity().getUniqueId().toString();
        deaths.set(key + ".world", location.getWorld().getUID().toString());
        deaths.set(key + ".x", location.getX());
        deaths.set(key + ".y", location.getY());
        deaths.set(key + ".z", location.getZ());
        try {
            deaths.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("死亡地点を保存できませんでした: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !plugin.perkItems().hasPerk(event.getItem(), Perks.RETURN_CHARM)) {
            return;
        }
        // 空中クリックはバニラ側でキャンセル済みの場合があるため isCancelled では判定しない。
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (player.isDead() || !using.add(player.getUniqueId())) {
            return;
        }
        try {
            use(player);
        } finally {
            using.remove(player.getUniqueId());
        }
    }

    private void use(Player player) {
        String key = player.getUniqueId().toString();
        String worldId = deaths.getString(key + ".world");
        if (worldId == null) {
            tell(player, "&c死亡地点がまだ記録されていません。");
            return;
        }
        World world;
        try {
            world = Bukkit.getWorld(UUID.fromString(worldId));
        } catch (IllegalArgumentException e) {
            world = null;
        }
        if (world == null) {
            tell(player, "&c死亡したワールドが利用できません。護符は消費していません。");
            return;
        }
        Location death = new Location(world, deaths.getDouble(key + ".x"),
                deaths.getDouble(key + ".y"), deaths.getDouble(key + ".z"));
        Location target = findSafeLocation(death);
        if (target == null) {
            tell(player, "&c死亡地点の近くに安全な足場がありません。護符は消費していません。");
            return;
        }
        target.setYaw(player.getLocation().getYaw());
        // 同期移動中のイベントにも備え、1個を預かり、失敗したときだけ返す。
        ItemStack stack = player.getInventory().getItemInMainHand();
        ItemStack refund = stack.clone();
        refund.setAmount(1);
        stack.setAmount(stack.getAmount() - 1);
        boolean moved = false;
        try {
            moved = player.teleport(target, TeleportCause.PLUGIN);
            if (moved) {
                player.setFallDistance(0);
                tell(player, "&b最後に死亡した場所の近くへ戻りました。帰還の護符を1個消費しました。");
            } else {
                tell(player, "&c移動できませんでした。護符は消費していません。");
            }
        } finally {
            if (!moved) {
                player.getInventory().addItem(refund).values().forEach(
                        item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
        }
    }

    /** 死亡座標の各軸±8ブロック内で、最も近い安全な足場を選ぶ。 */
    static Location findSafeLocation(Location death) {
        World world = death.getWorld();
        Location best = null;
        double distance = Double.MAX_VALUE;
        int minY = Math.max(world.getMinHeight() + 1, death.getBlockY() - 8);
        int maxY = Math.min(world.getMaxHeight() - 2, death.getBlockY() + 8);
        for (int x = death.getBlockX() - 8; x <= death.getBlockX() + 8; x++) {
            for (int z = death.getBlockZ() - 8; z <= death.getBlockZ() + 8; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Location candidate = new Location(world, x + 0.5, y, z + 0.5);
                    double d = candidate.distanceSquared(death);
                    if (d < distance && world.getWorldBorder().isInside(candidate)
                            && isSafe(world, x, y, z)) {
                        best = candidate;
                        distance = d;
                    }
                }
            }
        }
        return best;
    }

    private static boolean isSafe(World world, int x, int y, int z) {
        if (!world.getBlockAt(x, y, z).getType().isAir()
                || !world.getBlockAt(x, y + 1, z).getType().isAir()) {
            return false;
        }
        Block floor = world.getBlockAt(x, y - 1, z);
        var box = floor.getBoundingBox();
        if (!floor.getType().isSolid() || box.getWidthX() != 1
                || box.getWidthZ() != 1 || box.getHeight() != 1) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (HAZARDS.contains(world.getBlockAt(x + dx, y + dy, z + dz).getType())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void tell(Player player, String message) {
        player.sendMessage(Text.prefixed(message));
    }
}
