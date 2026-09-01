package dev.spa.adminshop;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffectType;

/**
 * サーバー全体に一定時間だけ効く商品の一覧。
 *
 * 4種類とも「右クリックで使うと、現実の時間でN分だけサーバー全体に効く」という同じ形をしている。
 * 残り時間の管理・ボスバー・再起動をまたぐ保存・重ねがけの上限・告知は ServerBoostService が
 * まとめて受け持ち、種類ごとに違うところだけをこの表に書く。
 *
 * potion が null のものは効果の中身を専用のリスナーが実装する（作物の成長、かまどの火力）。
 * potion があるものはプレイヤーへポーション効果を配るだけで済むので、リスナーは要らない。
 */
enum BoostType {

    /** 農作物の成長を速める。効果は GrowthBoostListener が BlockGrowEvent で実装する。 */
    GROWTH(Perks.GROWTH_BOOST, "豊穣の鐘", "鳴らしました", "農作物の成長", "&a", BossBar.Color.GREEN,
            Sound.BLOCK_BELL_USE, "multiplier", 30, 2,
            "&fサーバー全体の農作物の成長が &a%s &fになります。",
            null, null),

    /** かまど・溶鉱炉・燻製器の焼き上がりを速める。効果は SmeltBoostListener が実装する。 */
    SMELT(Perks.SMELT_BOOST, "溶鉱の号鐘", "鳴らしました", "かまどの火力", "&c", BossBar.Color.RED,
            Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, "multiplier", 30, 2,
            "&fサーバー全体のかまど・溶鉱炉・燻製器が &a%s &fの速さで焼き上がります。",
            null, null),

    /** 全員の採掘速度を上げる。鳴らした人は1段階上。 */
    MINING(Perks.MINING_BOOST, "鉱脈の号鐘", "鳴らしました", "採掘速度上昇", "&b", BossBar.Color.BLUE,
            Sound.BLOCK_BEACON_ACTIVATE, "level", 30, 1,
            "&f居合わせた全員に &a採掘速度上昇 %s &fが付きます。&7(鳴らした人は1段階上)",
            PotionEffectType.HASTE, null),

    /** 全員の移動速度を上げる。吹いた人は1段階上と、落下ダメージを和らげる効果が付く。 */
    SPEED(Perks.SPEED_BOOST, "疾風の笛", "吹きました", "移動速度上昇", "&e", BossBar.Color.YELLOW,
            Sound.ITEM_GOAT_HORN_SOUND_0, "level", 20, 1,
            "&f居合わせた全員に &a移動速度上昇 %s &fが付きます。&7(吹いた人は1段階上と落下耐性)",
            PotionEffectType.SPEED, PotionEffectType.SLOW_FALLING);

    private final String perk;
    private final String itemName;
    private final String verb;
    private final String subject;
    private final String colorCode;
    private final BossBar.Color barColor;
    private final Sound sound;
    private final String levelOption;
    private final int defaultMinutes;
    private final int defaultLevel;
    private final String effectFormat;
    private final PotionEffectType potion;
    private final PotionEffectType contributorBonus;

    BoostType(String perk, String itemName, String verb, String subject, String colorCode,
              BossBar.Color barColor, Sound sound, String levelOption, int defaultMinutes,
              int defaultLevel, String effectFormat, PotionEffectType potion,
              PotionEffectType contributorBonus) {
        this.perk = perk;
        this.itemName = itemName;
        this.verb = verb;
        this.subject = subject;
        this.colorCode = colorCode;
        this.barColor = barColor;
        this.sound = sound;
        this.levelOption = levelOption;
        this.defaultMinutes = defaultMinutes;
        this.defaultLevel = defaultLevel;
        this.effectFormat = effectFormat;
        this.potion = potion;
        this.contributorBonus = contributorBonus;
    }

    /** 効果の種類から引く。ブースト商品でなければ null。 */
    static BoostType byPerk(String perk) {
        if (perk == null) {
            return null;
        }
        for (BoostType type : values()) {
            if (type.perk.equals(perk)) {
                return type;
            }
        }
        return null;
    }

    String perk() {
        return perk;
    }

    String itemName() {
        return itemName;
    }

    String verb() {
        return verb;
    }

    String subject() {
        return subject;
    }

    BossBar.Color barColor() {
        return barColor;
    }

    Sound sound() {
        return sound;
    }

    /** perk-options で強さを書くときのキー。倍率のものは multiplier、ポーション効果のものは level。 */
    String levelOption() {
        return levelOption;
    }

    int defaultMinutes() {
        return defaultMinutes;
    }

    int defaultLevel() {
        return defaultLevel;
    }

    /** ポーション効果で実装するものはその種類、専用のリスナーで実装するものは null。 */
    PotionEffectType potion() {
        return potion;
    }

    /** 使った本人にだけ追加で付けるポーション効果。ないものは null。 */
    PotionEffectType contributorBonus() {
        return contributorBonus;
    }

    /** 倍率のものは「2倍」、ポーション効果のものはゲーム内の表記に合わせて「II」と出す。 */
    String levelText(int level) {
        return potion == null ? level + "倍" : roman(level);
    }

    String effectLine(int level) {
        return effectFormat.formatted(levelText(level));
    }

    String barName(int level, String remaining) {
        return colorCode + subject + " " + levelText(level) + "  &f残り " + remaining;
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }
}
