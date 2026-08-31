package dev.spa.adminshop;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;

/**
 * ショップに並ぶ商品ひとつぶんの定義。config.yml の items 以下から読み込む。
 *
 * @param perk    この商品が持つ効果の種類。アイテムの PersistentDataContainer にも同じ値を書き込み、
 *                効果の判定はその値だけを見る。名前やロアでは判定しないため、金床で改名しても偽造できない
 * @param options 効果ごとの細かい設定。効果の実装側が読む。持続時間や倍率など、商品によって変えたい値を入れる
 */
record ShopItem(String id, int slot, Material material, String displayName, List<String> lore,
                double price, int amount, boolean glow, String perk, Map<String, Object> options) {

    int option(String key, int fallback) {
        return options.get(key) instanceof Number number ? number.intValue() : fallback;
    }
}
