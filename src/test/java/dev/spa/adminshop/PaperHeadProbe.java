package dev.spa.adminshop;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.logging.Level;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** 隔離Paper上でテスト用Playerを使い、42種類の一覧と購入を検証する。 */
public final class PaperHeadProbe extends JavaPlugin {
    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                runProbe();
                getLogger().info("HEAD_PROBE_PASS");
            } catch (Throwable error) {
                getLogger().log(Level.SEVERE, "HEAD_PROBE_FAIL", error);
            } finally {
                Bukkit.shutdown();
            }
        }, 40);
    }

    private static void check(boolean actual, String message) {
        if (!actual) throw new AssertionError(message);
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static Object invoke(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(object, args);
    }

    private static final class User {
        final UUID id = UUID.nameUUIDFromBytes("HeadProbe".getBytes(StandardCharsets.UTF_8));
        final Inventory storage = Bukkit.createInventory(null, 36);
        Inventory menu;
        final PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
                PlayerInventory.class.getClassLoader(), new Class<?>[]{PlayerInventory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "firstEmpty" -> storage.firstEmpty();
                    case "addItem" -> storage.addItem((ItemStack[]) args[0]);
                    case "getStorageContents" -> storage.getContents();
                    default -> null;
                });
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission", "isOnline", "isValid" -> true;
                    case "getInventory" -> inventory;
                    case "openInventory" -> { menu = (Inventory) args[0]; yield null; }
                    case "getName" -> "HeadProbe";
                    case "getUniqueId" -> id;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "HeadProbe";
                    case "sendMessage", "playSound", "closeInventory" -> null;
                    default -> null;
                });
    }

    private void runProbe() throws Exception {
        JavaPlugin shop = (JavaPlugin) Bukkit.getPluginManager().getPlugin("AdminShop");
        check(shop != null && shop.isEnabled(), "AdminShop enabled");
        Object catalog = field(shop, "headCatalog");
        Collection<?> heads = (Collection<?>) invoke(catalog, "items", new Class<?>[0]);
        check(heads.size() == 42, "42 heads loaded");
        for (Object head : heads) {
            ItemStack stack = (ItemStack) invoke(head, "create", new Class<?>[0]);
            check(stack.getType() == Material.PLAYER_HEAD, "all entries are player heads");
            SkullMeta meta = (SkullMeta) stack.getItemMeta();
            check(meta.getOwnerProfile() != null && meta.getOwnerProfile().getTextures().getSkin() != null,
                    "all entries have fixed skins");
        }
        Object pig = invoke(catalog, "item", new Class<?>[]{String.class}, "MHF_Pig");
        ItemStack pigStack = (ItemStack) invoke(pig, "create", new Class<?>[0]);
        check(pigStack.getType() == Material.PLAYER_HEAD, "pig is player head");
        SkullMeta skull = (SkullMeta) pigStack.getItemMeta();
        check(skull.getOwnerProfile() != null && skull.getOwnerProfile().getTextures().getSkin() != null,
                "fixed pig skin");

        User user = new User();
        Economy economy = Bukkit.getServicesManager().getRegistration(Economy.class).getProvider();
        economy.createPlayerAccount(user.player);
        shop.getCommand("shop").execute(user.player, "shop", new String[0]);
        check(user.menu != null, "shop opened");
        Object holder = user.menu.getHolder();
        int headSlot = (Integer) invoke(holder, "headSlot", new Class<?>[0]);
        check(headSlot >= 0 && user.menu.getItem(headSlot).getType() == Material.PLAYER_HEAD,
                "head category button");

        Class<?> listenerClass = Class.forName("dev.spa.adminshop.GuiListener", true,
                shop.getClass().getClassLoader());
        var constructor = listenerClass.getDeclaredConstructor(shop.getClass());
        constructor.setAccessible(true);
        Object listener = constructor.newInstance(shop);
        invoke(listener, "handleShopClick", new Class<?>[]{Player.class, holder.getClass(), int.class},
                user.player, user.menu.getHolder(), headSlot);
        check(user.menu.getSize() == 54 && user.menu.getItem(49).getType() == Material.ARROW,
                "head list and back button");
        long icons = Arrays.stream(user.menu.getContents())
                .filter(item -> item != null && item.getType() == Material.PLAYER_HEAD).count();
        check(icons == 42, "42 head icons");

        int pigSlot = 0;
        for (Object head : heads) {
            if (head == pig) break;
            pigSlot++;
        }
        check(pigSlot < 42, "pig in catalog");
        invoke(listener, "handleHeadShopClick", new Class<?>[]{Player.class, int.class}, user.player, pigSlot);
        check(user.menu.getItem(13).getType() == Material.PLAYER_HEAD, "confirmation icon");

        Object config = field(shop, "shopConfig");
        double price = (Double) invoke(config, "headPrice", new Class<?>[0]);
        check(price == 50.0D, "50S price");
        economy.depositPlayer(user.player, 100.0D);
        double before = economy.getBalance(user.player);
        invoke(listener, "handleHeadConfirmClick",
                new Class<?>[]{Player.class, user.menu.getHolder().getClass(), int.class},
                user.player, user.menu.getHolder(), 11);
        check(economy.getBalance(user.player) == before - 50.0D, "50S withdrawn");
        check(Arrays.stream(user.storage.getContents()).anyMatch(item -> item != null
                && item.getType() == Material.PLAYER_HEAD), "head delivered");
    }
}
