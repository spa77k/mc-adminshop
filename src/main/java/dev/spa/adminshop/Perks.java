package dev.spa.adminshop;

import java.util.Set;

/**
 * 実装済みの効果の一覧。
 *
 * 商品の追加は config.yml だけで済むが、効果そのものは Java 側の実装が要る。
 * 未実装の perk を config.yml に書いた場合、その商品は読み飛ばして起動時に警告を出す。
 *
 * このうち末尾の4つはサーバー全体に一定時間だけ効くもので、種類ごとの違いは BoostType にまとめ、
 * 残り時間やボスバーの管理は ServerBoostService が共通で受け持つ。
 */
final class Perks {

    /** 死亡時に防具・左手・ホットバーを手元に残し、護符自身は消える。 */
    static final String KEEPSAKE_CHARM = "keepsake_charm";

    /** 使うと、サーバー全体の農作物の成長が一定時間だけ速くなる。 */
    static final String GROWTH_BOOST = "growth_boost";

    /** 使うと、サーバー全体のかまど・溶鉱炉・燻製器の焼き上がりが一定時間だけ速くなる。 */
    static final String SMELT_BOOST = "smelt_boost";

    /** 使うと、居合わせた全員に一定時間だけ採掘速度上昇が付く。 */
    static final String MINING_BOOST = "mining_boost";

    /** 使うと、居合わせた全員に一定時間だけ移動速度上昇が付く。 */
    static final String SPEED_BOOST = "speed_boost";

    private static final Set<String> KNOWN =
            Set.of(KEEPSAKE_CHARM, GROWTH_BOOST, SMELT_BOOST, MINING_BOOST, SPEED_BOOST);

    private Perks() {
    }

    static boolean isKnown(String perk) {
        return perk != null && KNOWN.contains(perk);
    }
}
