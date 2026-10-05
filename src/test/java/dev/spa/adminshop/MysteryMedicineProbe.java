package dev.spa.adminshop;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.random.RandomGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

/** 隔離Paperで抽選範囲と消費処理を検証する。実クライアントの操作は別途確認する。 */
public final class MysteryMedicineProbe extends JavaPlugin {
    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                runProbe();
                getLogger().info("MEDICINE_PROBE_PASS");
            } catch (Throwable error) {
                getLogger().log(Level.SEVERE, "MEDICINE_PROBE_FAIL", error);
            } finally {
                Bukkit.shutdown();
            }
        }, 40);
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    private static final class User {
        final ItemStack[] hands = {new ItemStack(Material.AIR), new ItemStack(Material.AIR)};
        PotionEffect effect;
        int calls;
        final UUID id = UUID.nameUUIDFromBytes("MedicineProbe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        final PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
                PlayerInventory.class.getClassLoader(), new Class<?>[]{PlayerInventory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getItem" -> hands[args[0] == EquipmentSlot.HAND ? 0 : 1];
                    case "setItem" -> {
                        hands[args[0] == EquipmentSlot.HAND ? 0 : 1] = (ItemStack) args[1];
                        yield null;
                    }
                    default -> null;
                });
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getInventory" -> inventory;
                    case "isDead" -> false;
                    case "getUniqueId" -> id;
                    case "getName" -> "MedicineProbe";
                    case "addPotionEffect" -> { effect = (PotionEffect) args[0]; calls++; yield true; }
                    case "sendMessage" -> null;
                    default -> null;
                });
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private void runProbe() throws Exception {
        JavaPlugin shop = (JavaPlugin) Bukkit.getPluginManager().getPlugin("AdminShop");
        check(shop != null && shop.isEnabled(), "AdminShop enabled");
        Object config = call(shop, "shopConfig", new Class<?>[0]);
        Object product = call(config, "item", new Class<?>[]{String.class}, "mystery_medicine");
        check(product != null, "medicine loaded");
        check((double) call(product, "price", new Class<?>[0]) == 100.0, "price 100S");
        ItemStack medicine = (ItemStack) call(shop, "createRewardItem",
                new Class<?>[]{String.class}, "mystery_medicine");
        check(medicine.getType() == Material.POTION, "drinkable potion");
        check(((PotionMeta) medicine.getItemMeta()).getBasePotionType() == PotionType.WATER,
                "no fixed vanilla effect before drinking");
        check(new org.bukkit.NamespacedKey("adminshop", "mystery_medicine")
                .equals(medicine.getItemMeta().getItemModel()), "custom texture model on genuine medicine");

        Class<?> listenerType = Class.forName("dev.spa.adminshop.MysteryMedicineListener", true,
                shop.getClass().getClassLoader());
        Method roll = listenerType.getDeclaredMethod("roll", RandomGenerator.class);
        roll.setAccessible(true);
        Set<String> effects = new HashSet<>();
        Set<Integer> levels = new HashSet<>();
        Set<Integer> durations = new HashSet<>();
        Random random = new Random(771005);
        for (int i = 0; i < 20000; i++) {
            PotionEffect effect = (PotionEffect) roll.invoke(null, random);
            check(!effect.getType().isInstant(), "no instant effects");
            check(effect.getDuration() >= 1200 && effect.getDuration() <= 6000, "60-300 seconds");
            check(effect.getAmplifier() >= 0 && effect.getAmplifier() <= 2, "levels I-III");
            effects.add(effect.getType().getKey().getKey());
            levels.add(effect.getAmplifier());
            durations.add(effect.getDuration());
        }
        check(effects.size() == 28 && effects.contains("regeneration") && effects.contains("wither"),
                "28 good and bad vanilla effects");
        check(levels.size() == 3 && durations.contains(1200) && durations.contains(6000),
                "both duration endpoints and all levels reached");

        var constructor = listenerType.getDeclaredConstructor(shop.getClass());
        constructor.setAccessible(true);
        Object listener = constructor.newInstance(shop);
        User user = new User();
        for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            int index = hand == EquipmentSlot.HAND ? 0 : 1;
            user.hands[index] = medicine.clone();
            user.hands[index].setAmount(3);
            int before = user.calls;
            PlayerItemConsumeEvent event = new PlayerItemConsumeEvent(user.player, medicine.clone(), hand);
            call(listener, "onConsume", new Class<?>[]{PlayerItemConsumeEvent.class}, event);
            check(event.isCancelled(), "no vanilla double consumption");
            check(user.hands[index].getAmount() == 2 && user.calls == before + 1,
                    "one item consumed and one effect applied in either hand");
        }
        int before = user.calls;
        PlayerItemConsumeEvent cancelled = new PlayerItemConsumeEvent(user.player, medicine, EquipmentSlot.HAND);
        cancelled.setCancelled(true);
        call(listener, "onConsume", new Class<?>[]{PlayerItemConsumeEvent.class}, cancelled);
        check(user.calls == before && user.hands[0].getAmount() == 2, "cancelled drink untouched");

        ItemStack ordinary = new ItemStack(Material.POTION);
        user.hands[0] = ordinary;
        PlayerItemConsumeEvent plain = new PlayerItemConsumeEvent(user.player, ordinary, EquipmentSlot.HAND);
        call(listener, "onConsume", new Class<?>[]{PlayerItemConsumeEvent.class}, plain);
        check(!plain.isCancelled() && user.calls == before && user.hands[0].getAmount() == 1,
                "ordinary potion untouched");
        user.hands[0] = medicine.clone();
        PlayerItemConsumeEvent last = new PlayerItemConsumeEvent(user.player, medicine, EquipmentSlot.HAND);
        call(listener, "onConsume", new Class<?>[]{PlayerItemConsumeEvent.class}, last);
        check(user.hands[0].getAmount() == 0 && user.calls == before + 1, "last item disappears");
    }
}
