package com.lacuna.mod.village;

import net.minecraft.util.StringRepresentable;

/** The three village guilds (spec 18.4). */
public enum GuildType implements StringRepresentable {
    SMITHS("smiths"), GEMCUTTERS("gemcutters"), SCHOLARS("scholars");

    private final String name;

    GuildType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static GuildType byName(String name) {
        for (GuildType g : values()) {
            if (g.name.equals(name)) {
                return g;
            }
        }
        return null;
    }
}
