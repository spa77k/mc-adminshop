package dev.spa.adminshop;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/** 隔離Paper上でテスト用Playerを使い、運搬の卵の入れる・出すを実イベント経由で検証する。 */
public final class CaptureEggProbe extends JavaPlugin {
    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                runProbe();
                getLogger().info("EGG_PROBE_PASS");
            } catch (Throwable error) {
                getLogger().log(Level.SEVERE, "EGG_PROBE_FAIL", error);
            } finally {
                Bukkit.shutdown();
            }
        }, 40);
    }

    private static void check(boolean actual, String message) {
        if (!actual) throw new AssertionError(message);
    }

    private static final class User {
        final UUID id;
        final World world;
        final Location location;
        ItemStack hand = null;
        final Inventory storage = Bukkit.createInventory(null, 36);
        final PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
                PlayerInventory.class.getClassLoader(), new Class<?>[]{PlayerInventory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getItemInMainHand" -> hand == null ? new ItemStack(Material.AIR) : hand;
                    case "setItemInMainHand" -> { hand = (ItemStack) args[0]; yield null; }
                    case "getItem" -> hand;
                    case "firstEmpty" -> storage.firstEmpty();
                    case "addItem" -> storage.addItem((ItemStack[]) args[0]);
                    default -> null;
                });
        final Player player;

        User(String name, World world, Location location) {
            this.id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
            this.world = world;
            this.location = location;
            this.player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                    new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "hasPermission" -> !"adminshop.capture.bypass".equals(args[0]);
                        case "isOnline", "isValid" -> true;
                        case "getInventory" -> inventory;
                        case "getName" -> name;
                        case "getUniqueId" -> id;
                        case "getWorld" -> world;
                        case "getLocation" -> location.clone();
                        case "isSneaking" -> false;
                        case "hashCode" -> id.hashCode();
                        case "equals" -> proxy == args[0];
                        case "toString" -> name;
                        case "sendMessage" -> { Bukkit.getLogger().info("[msg " + name + "] "
                                + (args[0] instanceof Component c ? net.kyori.adventure.text.serializer.plain
                                .PlainTextComponentSerializer.plainText().serialize(c) : args[0])); yield null; }
                        default -> null;
                    });
        }
    }

    /** AdminShop はパッケージ非公開のため、別ローダーのテストからはリフレクションで判定する。 */
    private static boolean hasPerk(JavaPlugin shop, ItemStack stack, String perk) throws Exception {
        var getter = shop.getClass().getDeclaredMethod("perkItems");
        getter.setAccessible(true);
        Object items = getter.invoke(shop);
        var method = items.getClass().getDeclaredMethod("hasPerk", ItemStack.class, String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(items, stack, perk);
    }

    private static ItemStack egg(JavaPlugin shop) {
        return ((AdminShopPlugin) shop).createRewardItem("capture_egg");
    }

    private void runProbe() throws Exception {
        AdminShopPlugin shop = (AdminShopPlugin) Bukkit.getPluginManager().getPlugin("AdminShop");
        check(shop != null && shop.isEnabled(), "AdminShop enabled");
        World world = Bukkit.getWorlds().get(0);
        Location base = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1, 0.5);
        world.getChunkAt(base).load(true);
        User user = new User("EggProbe", world, base);
        User other = new User("OtherProbe", world, base);

        // --- 村人 ---
        Villager villager = (Villager) world.spawnEntity(base.clone().add(3, 0, 0), EntityType.VILLAGER);
        villager.setProfession(Villager.Profession.LIBRARIAN);
        villager.setVillagerLevel(4);
        villager.setVillagerExperience(137);
        villager.customName(Component.text("たろう"));
        MerchantRecipe recipe = new MerchantRecipe(new ItemStack(Material.DIAMOND, 3), 2, 9, true, 5, 0.25F);
        recipe.addIngredient(new ItemStack(Material.EMERALD, 7));
        villager.setRecipes(List.of(recipe));

        user.hand = egg(shop);
        check(hasPerk(shop, user.hand, "capture_egg"), "shop egg has perk");
        check(user.hand.getType() == Material.SNIFFER_EGG, "egg material");
        Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(user.player, villager, EquipmentSlot.HAND));
        check(!villager.isValid(), "villager removed after capture");
        check(hasPerk(shop, user.hand, "capture_egg_filled"), "hand holds filled egg");
        check(user.hand.getMaxStackSize() == 1, "filled egg does not stack");

        // 卵は他人のプレイヤーが使っても同じ結果になる（譲渡できる）。出す側は別のPlayerで行う。
        ItemStack filled = user.hand;
        user.hand = null;
        other.hand = filled;
        Block ground = world.getBlockAt(base.getBlockX() + 6, base.getBlockY() - 1, base.getBlockZ());
        ground.setType(Material.STONE);
        PlayerInteractEvent use = new PlayerInteractEvent(other.player, Action.RIGHT_CLICK_BLOCK, filled, ground,
                BlockFace.UP, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(use);
        check(other.hand == null, "filled egg consumed on release");
        Villager restored = world.getNearbyEntitiesByType(Villager.class, ground.getLocation(), 3).stream()
                .findFirst().orElse(null);
        check(restored != null, "villager released");
        check(restored.getProfession() == Villager.Profession.LIBRARIAN, "profession kept");
        check(restored.getVillagerLevel() == 4, "level kept");
        check(restored.getVillagerExperience() == 137, "experience kept");
        check(restored.customName() != null && "たろう".equals(net.kyori.adventure.text.serializer.plain
                .PlainTextComponentSerializer.plainText().serialize(restored.customName())), "name kept");
        check(restored.getRecipeCount() == 1, "one trade kept");
        MerchantRecipe kept = restored.getRecipe(0);
        check(kept.getResult().getType() == Material.DIAMOND && kept.getResult().getAmount() == 3, "trade result");
        check(kept.getIngredients().get(0).getType() == Material.EMERALD
                && kept.getIngredients().get(0).getAmount() == 7, "trade ingredient");
        check(kept.getUses() == 2 && kept.getMaxUses() == 9, "trade uses kept");
        check(kept.getVillagerExperience() == 5 && kept.getPriceMultiplier() == 0.25F, "trade xp kept");

        // --- 自分のペット（狼） ---
        Wolf wolf = (Wolf) world.spawnEntity(base.clone().add(0, 0, 3), EntityType.WOLF);
        wolf.setOwner(Bukkit.getOfflinePlayer(user.id));
        wolf.setTamed(true);
        wolf.setCollarColor(DyeColor.PURPLE);
        wolf.customName(Component.text("ポチ"));
        wolf.setSitting(true);
        user.hand = egg(shop);
        Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(user.player, wolf, EquipmentSlot.HAND));
        check(!wolf.isValid(), "own wolf captured");
        ItemStack wolfEgg = user.hand;
        Block ground2 = world.getBlockAt(base.getBlockX() - 6, base.getBlockY() - 1, base.getBlockZ());
        ground2.setType(Material.STONE);
        Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(user.player, Action.RIGHT_CLICK_BLOCK,
                wolfEgg, ground2, BlockFace.UP, EquipmentSlot.HAND));
        Wolf wolf2 = world.getNearbyEntitiesByType(Wolf.class, ground2.getLocation(), 3).stream()
                .findFirst().orElse(null);
        check(wolf2 != null, "wolf released");
        check(wolf2.isTamed() && user.id.equals(wolf2.getOwnerUniqueId()), "owner kept");
        check(wolf2.getCollarColor() == DyeColor.PURPLE, "collar kept");
        check(wolf2.isSitting(), "sitting kept");
        check(wolf2.customName() != null, "wolf name kept");

        // --- 他人のペットは入れられない ---
        Wolf theirs = (Wolf) world.spawnEntity(base.clone().add(0, 0, -3), EntityType.WOLF);
        theirs.setOwner(Bukkit.getOfflinePlayer(other.id));
        theirs.setTamed(true);
        user.hand = egg(shop);
        Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(user.player, theirs, EquipmentSlot.HAND));
        check(theirs.isValid(), "other's pet stays");
        check(hasPerk(shop, user.hand, "capture_egg"), "egg not consumed on denial");

        // --- 馬（鞍つき） ---
        Horse horse = (Horse) world.spawnEntity(base.clone().add(-3, 0, 3), EntityType.HORSE);
        horse.setOwner(Bukkit.getOfflinePlayer(user.id));
        horse.setTamed(true);
        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        horse.setJumpStrength(0.83);
        Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(user.player, horse, EquipmentSlot.HAND));
        check(!horse.isValid(), "own horse captured");
        Block ground3 = world.getBlockAt(base.getBlockX(), base.getBlockY() - 1, base.getBlockZ() - 8);
        ground3.setType(Material.STONE);
        Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(user.player, Action.RIGHT_CLICK_BLOCK,
                user.hand, ground3, BlockFace.UP, EquipmentSlot.HAND));
        Horse horse2 = world.getNearbyEntitiesByType(Horse.class, ground3.getLocation(), 3).stream()
                .findFirst().orElse(null);
        check(horse2 != null, "horse released");
        check(horse2.getInventory().getSaddle() != null
                && horse2.getInventory().getSaddle().getType() == Material.SADDLE, "saddle kept");
        check(Math.abs(horse2.getJumpStrength() - 0.83) < 1e-6, "jump strength kept");

        // --- 敵対モブ・案内係は入れられない ---
        Zombie zombie = (Zombie) world.spawnEntity(base.clone().add(0, 0, 8), EntityType.ZOMBIE);
        user.hand = egg(shop);
        Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(user.player, zombie, EquipmentSlot.HAND));
        check(zombie.isValid(), "zombie stays");
        Villager guide = (Villager) world.spawnEntity(base.clone().add(8, 0, 8), EntityType.VILLAGER);
        guide.addScoreboardTag("spsmc_guide");
        Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(user.player, guide, EquipmentSlot.HAND));
        check(guide.isValid(), "guide stays");

        // --- 狭い場所には出せず、卵は残る ---
        user.hand = egg(shop);
        Villager tight = (Villager) world.spawnEntity(base.clone().add(0, 0, -8), EntityType.VILLAGER);
        Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(user.player, tight, EquipmentSlot.HAND));
        ItemStack tightEgg = user.hand;
        Block low = world.getBlockAt(base.getBlockX() + 12, base.getBlockY() - 1, base.getBlockZ());
        low.setType(Material.STONE);
        world.getBlockAt(low.getX(), low.getY() + 2, low.getZ()).setType(Material.STONE);
        Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(user.player, Action.RIGHT_CLICK_BLOCK,
                tightEgg, low, BlockFace.UP, EquipmentSlot.HAND));
        check(hasPerk(shop, user.hand, "capture_egg_filled"), "egg kept when space is tight");
        check(world.getNearbyEntitiesByType(Villager.class, low.getLocation(), 3).isEmpty(), "nothing released");

        Entity[] leftover = world.getEntities().stream().filter(e -> e instanceof Villager).toArray(Entity[]::new);
        getLogger().info("EGG_PROBE villagers alive: " + leftover.length);
    }
}
