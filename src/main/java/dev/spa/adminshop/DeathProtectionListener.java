package dev.spa.adminshop;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * 身代わりの護符の効果。死亡時に防具・左手・ホットバーを手元へ移し、護符を1個消費する。
 *
 * PlayerInventory のスロット番号は 0〜8 がホットバー、9〜35 が持ち物、36〜39 が防具、40 が左手。
 * 手元へ残す枠はここから選ぶ。
 */
final class DeathProtectionListener implements Listener {

    private static final int HOTBAR_FIRST = 0;
    private static final int HOTBAR_LAST = 8;
    private static final int ARMOR_FIRST = 36;
    private static final int ARMOR_LAST = 39;
    private static final int OFFHAND_SLOT = 40;

    private final AdminShopPlugin plugin;

    /** 発動した本人へリスパーン時に演出を出すための一時的な印。 */
    private final Set<UUID> pendingEffect = new HashSet<>();

    DeathProtectionListener(AdminShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        // ゲームルールや他プラグインが既に全部残すと決めているなら、護符を使わせる意味がない。
        if (event.getKeepInventory()) {
            return;
        }

        Player player = event.getEntity();
        ShopConfig config = plugin.shopConfig();

        if (!config.protectOnPvpDeath() && player.getKiller() != null) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int charmSlot = findCharmSlot(inventory);
        if (charmSlot < 0) {
            return;
        }

        List<ItemStack> drops = event.getDrops();

        // 消滅の呪いが付いた護符は落ちる物の中に現れない。その場合は発動させない。
        if (!takeCharmFromDrops(drops)) {
            return;
        }

        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack target : collectKeepTargets(inventory, config, charmSlot)) {
            // 落ちる物から取り除けたぶんだけを手元へ回す。取り除けないまま手元へ足すと、
            // 同じアイテムが地面と手元の両方に出て複製になる。
            if (takeFromDrops(drops, target)) {
                kept.add(target);
            }
        }
        event.getItemsToKeep().addAll(kept);

        pendingEffect.add(player.getUniqueId());
        announce(player, kept.size(), config);

        ActivityLog activityLog = plugin.activityLog();
        if (activityLog != null) {
            Player killer = player.getKiller();
            activityLog.logActivation(
                    player.getUniqueId(),
                    player.getName(),
                    Perks.KEEPSAKE_CHARM,
                    kept.size(),
                    player.getLocation(),
                    killer == null ? null : killer.getName());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!pendingEffect.remove(player.getUniqueId())) {
            return;
        }
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.7F, 1.4F);
        player.sendActionBar(Text.of("&e身代わりの護符が身を守った"));
    }

    private int findCharmSlot(PlayerInventory inventory) {
        PerkItems perkItems = plugin.perkItems();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (perkItems.hasPerk(inventory.getItem(slot), Perks.KEEPSAKE_CHARM)) {
                return slot;
            }
        }
        return -1;
    }

    private List<ItemStack> collectKeepTargets(PlayerInventory inventory, ShopConfig config, int charmSlot) {
        Set<Integer> slots = new LinkedHashSet<>();
        if (config.keepHotbar()) {
            for (int slot = HOTBAR_FIRST; slot <= HOTBAR_LAST; slot++) {
                slots.add(slot);
            }
        }
        if (config.keepArmor()) {
            for (int slot = ARMOR_FIRST; slot <= ARMOR_LAST; slot++) {
                slots.add(slot);
            }
        }
        if (config.keepOffhand()) {
            slots.add(OFFHAND_SLOT);
        }

        List<ItemStack> targets = new ArrayList<>();
        for (int slot : slots) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (slot == charmSlot) {
                // 発動で1個消える。1個しかなければ手元には戻らない。
                if (stack.getAmount() <= 1) {
                    continue;
                }
                ItemStack rest = stack.clone();
                rest.setAmount(stack.getAmount() - 1);
                targets.add(rest);
                continue;
            }
            targets.add(stack.clone());
        }
        return targets;
    }

    /** 落ちる物の中から護符を1個ぶん取り除く。1個も無ければ false。 */
    private boolean takeCharmFromDrops(List<ItemStack> drops) {
        PerkItems perkItems = plugin.perkItems();
        for (Iterator<ItemStack> iterator = drops.iterator(); iterator.hasNext(); ) {
            ItemStack stack = iterator.next();
            if (!perkItems.hasPerk(stack, Perks.KEEPSAKE_CHARM)) {
                continue;
            }
            if (stack.getAmount() > 1) {
                stack.setAmount(stack.getAmount() - 1);
            } else {
                iterator.remove();
            }
            return true;
        }
        return false;
    }

    /** 落ちる物から target と同じ中身を同じ数だけ取り除く。取り切れなければ false。 */
    private boolean takeFromDrops(List<ItemStack> drops, ItemStack target) {
        for (Iterator<ItemStack> iterator = drops.iterator(); iterator.hasNext(); ) {
            ItemStack stack = iterator.next();
            if (stack != null && stack.isSimilar(target) && stack.getAmount() == target.getAmount()) {
                iterator.remove();
                return true;
            }
        }

        // 数が割れている場合は、同じ中身のスタックから必要な数だけ削る。
        int remaining = target.getAmount();
        for (Iterator<ItemStack> iterator = drops.iterator(); iterator.hasNext() && remaining > 0; ) {
            ItemStack stack = iterator.next();
            if (stack == null || !stack.isSimilar(target)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - taken);
            remaining -= taken;
            if (stack.getAmount() <= 0) {
                iterator.remove();
            }
        }
        return remaining == 0;
    }

    private void announce(Player player, int keptCount, ShopConfig config) {
        player.sendMessage(Text.prefixed("&e身代わりの護符&fが砕け、"
                + "&e" + keptCount + "&f個の持ち物を手元に残しました。"));
        player.sendMessage(Text.prefixed("&7それ以外の持ち物は死んだ場所に落ちています。"));

        if (config.broadcast()) {
            plugin.getServer().broadcast(Text.prefixed(
                    "&e" + player.getName() + "&f が身代わりの護符に守られました。"));
        }
    }
}
