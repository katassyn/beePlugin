package org.maks.beesPlugin.hive;

public enum Tier {
    I(1),
    II(2),
    III(3);

    private final int level;

    Tier(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public static Tier fromLevel(int level) {
        for (Tier t : values()) {
            if (t.level == level) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown tier: " + level);
    }
}
