package dev.spa.adminshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** config.yml に書かれた &コード付きの文字列を Adventure の Component へ直す。 */
final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    static Component of(String legacy) {
        return LEGACY.deserialize(legacy);
    }

    /** アイテム名とロアは何もしないと斜体になるため、明示的に解除する。 */
    static Component item(String legacy) {
        return LEGACY.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    static Component prefixed(String legacy) {
        return LEGACY.deserialize("&8[&6ショップ&8] &r" + legacy);
    }
}
