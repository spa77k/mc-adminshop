package dev.spa.adminshop.event;

import java.util.Map;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** サーバーブーストの開始または延長が確定したときに発火する。 */
public final class ServerBoostActivatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Map<String, String> placeholders;

    public ServerBoostActivatedEvent(String player, String boost, int addedMinutes,
                                     int remainingMinutes, int level, boolean extension) {
        placeholders = Map.of("player", player, "boost", boost,
                "added_minutes", String.valueOf(addedMinutes),
                "remaining_minutes", String.valueOf(remainingMinutes),
                "level", String.valueOf(level), "action", extension ? "延長" : "発動");
    }

    public String getNotifyKind() { return "boost.activated"; }
    public Map<String, String> getNotifyPlaceholders() { return placeholders; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
