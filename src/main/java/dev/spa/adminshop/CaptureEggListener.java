package dev.spa.adminshop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 運搬の卵。空の卵を生き物へ右クリックすると、その子をステータスごと中身入りの卵へ入れる。
 * 中身入りの卵を地面へ右クリックすると、同じ子が出てきて卵は消える。
 *
 * 中身は Paper のエンティティ直列化（NBT）をそのまま持たせる。村人の取引・経験値・噂、ペットの名前・
 * 首輪の色・所有者・体力、馬の鞍や防具や荷物など、ゲームが保存しているものが丸ごと引き継がれる。
 * 一覧を自前で書かないので、将来の追加項目も取りこぼさない。
 */
final class CaptureEggListener implements Listener {

    /** 案内係NPCなど、Skript が付ける目印。運べない。 */
    private static final String GUIDE_TAG = "spsmc_guide";

    /** 卵に持たせるNBTの上限。荷物を詰めたロバなどでアイテム同期が重くなりすぎるのを防ぐ。 */
    private static final int DEFAULT_MAX_BYTES = 65536;

    /**
     * 入れた直後に、同じ右クリックから続けて届く「アイテムの使用」を無視する時間（ミリ秒）。
     * 生き物への右クリックは、取り込み後に地面への右クリックとしても処理されることがあり、
     * 中身入りになった卵をその場で出してしまい、入れた意味がなくなる。
     */
    private static final long RELEASE_DELAY_MILLIS = 300L;

    private final AdminShopPlugin plugin;
    private final Map<UUID, Long> capturedAt = new HashMap<>();
    private final ClaimGuard claims;
    private final NamespacedKey dataKey;

    CaptureEggListener(AdminShopPlugin plugin) {
        this.plugin = plugin;
        this.claims = new ClaimGuard(plugin);
        this.dataKey = new NamespacedKey(plugin, "capture_data");
    }

    // ---- 入れる ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityUse(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        boolean empty = plugin.perkItems().hasPerk(hand, Perks.CAPTURE_EGG);
        boolean filled = plugin.perkItems().hasPerk(hand, Perks.CAPTURE_EGG_FILLED);
        if (!empty && !filled) {
            return;
        }
        // 村人の取引画面などが開かないよう、どの結果でも本来の右クリックは止める。
        event.setCancelled(true);
        if (filled) {
            return;
        }
        capture(player, event.getRightClicked());
    }

    private void capture(Player player, Entity target) {
        if (!player.hasPermission("adminshop.capture")) {
            fail(player, "&c運搬の卵を使う権限がありません。");
            return;
        }
        if (blockedWorld(target.getWorld())) {
            fail(player, "&cこのワールドでは運搬の卵を使えません。");
            return;
        }
        if (!isCapturable(target)) {
            fail(player, "&cこの生き物は卵に入れられません。");
            return;
        }
        if (!target.isValid() || target.isDead()) {
            return;
        }
        boolean bypass = player.hasPermission("adminshop.capture.bypass");
        if (!bypass && target instanceof Tameable tameable && tameable.isTamed()
                && tameable.getOwnerUniqueId() != null
                && !tameable.getOwnerUniqueId().equals(player.getUniqueId())) {
            fail(player, "&c他のプレイヤーのペットは卵に入れられません。");
            return;
        }
        if (!bypass && claims.deniesContainers(player, target.getLocation())) {
            fail(player, "&c他のプレイヤーの土地にいる生き物は卵に入れられません。");
            return;
        }
        if (!target.getPassengers().isEmpty()) {
            fail(player, "&c誰かが乗っている間は卵に入れられません。");
            return;
        }
        if (target instanceof LivingEntity living && living.isLeashed()) {
            fail(player, "&cリードを外してから卵に入れてください。");
            return;
        }
        if (target instanceof Villager villager && villager.isTrading()) {
            fail(player, "&c取引中の村人は卵に入れられません。");
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack hand = inventory.getItemInMainHand();
        // 空の卵を複数持っているときは、中身入りの卵の置き場所が要る。
        if (hand.getAmount() > 1 && inventory.firstEmpty() < 0) {
            fail(player, "&c持ち物がいっぱいです。1枠以上空けてから使ってください。");
            return;
        }

        byte[] data;
        try {
            data = serialize(target);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("生き物の保存に失敗しました (" + target.getType() + "): " + e);
            fail(player, "&cこの生き物を保存できませんでした。");
            return;
        }
        if (data == null || data.length == 0) {
            fail(player, "&cこの生き物を保存できませんでした。");
            return;
        }
        if (data.length > maxBytes()) {
            fail(player, "&c持ち物や荷物が多すぎて卵に入りません。荷物を降ろしてから使ってください。");
            return;
        }

        ItemStack filled = createFilled(target, data);
        Location where = target.getLocation();
        String description = describe(target);

        // ここから先は元に戻せない。先に卵を用意してあるので、取り込みと受け渡しは続けて行う。
        target.remove();
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
            inventory.setItemInMainHand(hand);
            inventory.addItem(filled);
        } else {
            inventory.setItemInMainHand(filled);
        }

        capturedAt.put(player.getUniqueId(), System.nanoTime());
        player.sendMessage(Text.prefixed("&f" + description + " &7を卵に入れました。"));
        player.playSound(where, Sound.ENTITY_ITEM_PICKUP, 0.8F, 0.8F);
        player.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER,
                where.clone().add(0, 0.8, 0), 12, 0.3, 0.4, 0.3, 0);
        plugin.getLogger().info("[運搬の卵] " + player.getName() + " が " + describe(where, description)
                + " を卵に入れました。");
    }

    // ---- 出す ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        ItemStack stack = event.getItem();
        boolean empty = plugin.perkItems().hasPerk(stack, Perks.CAPTURE_EGG);
        boolean filled = plugin.perkItems().hasPerk(stack, Perks.CAPTURE_EGG_FILLED);
        if (!empty && !filled) {
            return;
        }
        // 卵の素材がブロックとして置かれないよう、アイテムの使用だけは必ず止める。
        // チェストなど右クリック先のブロック本来の動作は止めない。
        event.setUseItemInHand(Event.Result.DENY);
        if (!filled || action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        Player player = event.getPlayer();
        if (justCaptured(player)) {
            return;
        }
        // 開けるブロック（チェスト・ドアなど）はそちらを優先する。しゃがむと卵の使用を優先できる。
        if (event.useInteractedBlock() != Event.Result.DENY && clicked.getType().isInteractable()
                && !player.isSneaking()) {
            return;
        }
        release(player, stack, clicked, event.getBlockFace());
    }

    private boolean justCaptured(Player player) {
        Long at = capturedAt.get(player.getUniqueId());
        if (at == null) {
            return false;
        }
        if ((System.nanoTime() - at) / 1_000_000L < RELEASE_DELAY_MILLIS) {
            return true;
        }
        capturedAt.remove(player.getUniqueId());
        return false;
    }

    private void release(Player player, ItemStack egg, Block clicked, BlockFace face) {
        if (!player.hasPermission("adminshop.capture")) {
            fail(player, "&c運搬の卵を使う権限がありません。");
            return;
        }
        World world = clicked.getWorld();
        if (blockedWorld(world)) {
            fail(player, "&cこのワールドでは運搬の卵を使えません。");
            return;
        }
        byte[] data = egg.getItemMeta().getPersistentDataContainer().get(dataKey, PersistentDataType.BYTE_ARRAY);
        if (data == null || data.length == 0) {
            fail(player, "&cこの卵は空です。");
            return;
        }

        Block base = clicked.isReplaceable() ? clicked : clicked.getRelative(face);
        Location spawn = base.getLocation().add(0.5, 0.0, 0.5);
        spawn.setYaw(player.getLocation().getYaw() + 180.0F);

        if (!player.hasPermission("adminshop.capture.bypass") && claims.deniesBuild(player, spawn)) {
            fail(player, "&c他のプレイヤーの土地には生き物を出せません。");
            return;
        }

        Entity entity;
        try {
            entity = deserialize(data, world);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("卵の中身を復元できませんでした (" + player.getName() + "): " + e);
            fail(player, "&c卵の中身を復元できませんでした。管理者へ連絡してください。");
            return;
        }
        if (entity == null) {
            fail(player, "&c卵の中身を復元できませんでした。管理者へ連絡してください。");
            return;
        }

        // 背の高い生き物が天井や壁にめり込まないよう、必要な高さぶんの空きを確かめる。
        int height = Math.max(1, (int) Math.ceil(entity.getHeight()));
        for (int i = 0; i < height; i++) {
            if (!base.getRelative(0, i, 0).isPassable()) {
                fail(player, "&cここは狭くて生き物を出せません。広い場所で使ってください。");
                return;
            }
        }

        if (!entity.spawnAt(spawn, SpawnReason.CUSTOM)) {
            fail(player, "&cここには生き物を出せませんでした。場所を変えて試してください。");
            return;
        }

        player.getInventory().setItemInMainHand(null);
        String description = describe(entity);
        player.sendMessage(Text.prefixed("&f" + description + " &7が卵から出てきました。"));
        world.playSound(spawn, Sound.ENTITY_CHICKEN_EGG, 1.0F, 1.0F);
        world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, spawn.clone().add(0, 0.8, 0),
                12, 0.3, 0.4, 0.3, 0);
        plugin.getLogger().info("[運搬の卵] " + player.getName() + " が " + describe(spawn, description)
                + " を卵から出しました。");
    }

    // ---- 卵自体の保護 ----

    /** 卵が設置ブロックとして置かれる経路を、念のためここでも止める。 */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String perk = plugin.perkItems().perkOf(event.getItemInHand());
        if (Perks.CAPTURE_EGG.equals(perk) || Perks.CAPTURE_EGG_FILLED.equals(perk)) {
            event.setCancelled(true);
        }
    }

    /** 中身入りの卵を落としても、溶岩・炎・爆発で燃えたり消えたりしないようにする。 */
    @EventHandler
    public void onDrop(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (plugin.perkItems().hasPerk(item.getItemStack(), Perks.CAPTURE_EGG_FILLED)) {
            item.setInvulnerable(true);
            item.setUnlimitedLifetime(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item
                && plugin.perkItems().hasPerk(item.getItemStack(), Perks.CAPTURE_EGG_FILLED)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        if (plugin.perkItems().hasPerk(event.getEntity().getItemStack(), Perks.CAPTURE_EGG_FILLED)) {
            event.setCancelled(true);
        }
    }

    // ---- 判定と表示 ----

    private boolean isCapturable(Entity entity) {
        if (entity instanceof Player || entity instanceof Enemy) {
            return false;
        }
        if (entity.getScoreboardTags().contains(GUIDE_TAG)) {
            return false;
        }
        // AIを止めた生き物は、案内役などのNPCとして置かれたものとみなす。
        if (entity instanceof LivingEntity living && !living.hasAI()) {
            return false;
        }
        return entity instanceof AbstractVillager || entity instanceof Animals;
    }

    private ItemStack createFilled(Entity target, byte[] data) {
        ShopItem product = plugin.shopConfig().firstItemWithPerk(Perks.CAPTURE_EGG);
        Material material = product == null ? Material.SNIFFER_EGG : product.material();
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Text.item("&d運搬の卵 &7(中身あり)"));
        List<Component> lore = new ArrayList<>();
        lore.add(gray(Component.text("中身: ", NamedTextColor.GRAY)
                .append(Component.translatable(target.getType().translationKey(), NamedTextColor.WHITE))));
        Component name = target.customName();
        if (name != null) {
            lore.add(gray(Component.text("名前: ", NamedTextColor.GRAY)
                    .append(name.colorIfAbsent(NamedTextColor.WHITE))));
        }
        if (target instanceof Villager villager) {
            lore.add(gray(Component.text("職業: ", NamedTextColor.GRAY)
                    .append(Component.translatable("entity.minecraft.villager."
                            + villager.getProfession().key().value(), NamedTextColor.WHITE))
                    .append(Component.text(" Lv" + villager.getVillagerLevel(), NamedTextColor.WHITE))));
        }
        if (target instanceof Tameable tameable && tameable.isTamed()) {
            String owner = tameable.getOwner() == null ? null : tameable.getOwner().getName();
            if (owner != null) {
                lore.add(gray(Component.text("飼い主: " + owner, NamedTextColor.GRAY)));
            }
        }
        if (target instanceof Cat cat && cat.isTamed()) {
            lore.add(gray(Component.text("首輪: ", NamedTextColor.GRAY)
                    .append(Component.translatable("color.minecraft." + cat.getCollarColor().name().toLowerCase(),
                            NamedTextColor.WHITE))));
        } else if (target instanceof Wolf wolf && wolf.isTamed()) {
            lore.add(gray(Component.text("首輪: ", NamedTextColor.GRAY)
                    .append(Component.translatable("color.minecraft." + wolf.getCollarColor().name().toLowerCase(),
                            NamedTextColor.WHITE))));
        }
        if (target instanceof Ageable ageable && !ageable.isAdult()) {
            lore.add(gray(Component.text("子ども", NamedTextColor.GRAY)));
        }
        lore.add(Component.empty());
        lore.add(Text.item("&7地面に右クリックすると、"));
        lore.add(Text.item("&7この子が出てきて卵は消える。"));
        meta.lore(lore);

        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setMaxStackSize(1);
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(dataKey, PersistentDataType.BYTE_ARRAY, data);
        plugin.perkItems().mark(meta, Perks.CAPTURE_EGG_FILLED);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Component gray(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    /** チャットやログに出す短い呼び名。名前があればそれ、なければ種類。 */
    private static String describe(Entity entity) {
        Component name = entity.customName();
        if (name != null) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(name);
        }
        return entity.getType().getKey().getKey();
    }

    private static String describe(Location location, String what) {
        return what + " (" + location.getWorld().getName() + " " + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ() + ")";
    }

    private void fail(Player player, String message) {
        player.sendMessage(Text.prefixed(message));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6F, 1.0F);
    }

    private boolean blockedWorld(World world) {
        ShopItem product = plugin.shopConfig().firstItemWithPerk(Perks.CAPTURE_EGG);
        if (product == null || !(product.options().get("blocked-worlds") instanceof List<?> worlds)) {
            return world.getName().equals("lobby");
        }
        return worlds.contains(world.getName());
    }

    private int maxBytes() {
        ShopItem product = plugin.shopConfig().firstItemWithPerk(Perks.CAPTURE_EGG);
        return product == null ? DEFAULT_MAX_BYTES : product.option("max-bytes", DEFAULT_MAX_BYTES);
    }

    @SuppressWarnings("deprecation")
    private static byte[] serialize(Entity entity) {
        return Bukkit.getUnsafe().serializeEntity(entity);
    }

    @SuppressWarnings("deprecation")
    private static Entity deserialize(byte[] data, World world) {
        // UUID は引き継がない。卵の複製など想定外の経路があっても、同じUUIDの生き物が並ぶことを避ける。
        return Bukkit.getUnsafe().deserializeEntity(data, world, false);
    }
}
