package com.kingsandmonsters;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only presentation settings. */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOW_CUSTOM_TOOLTIPS = BUILDER
            .comment("Show Kings & Monsters custom tooltip text, fonts, colors, and borders. Vanilla item attributes and enchantments remain visible when disabled.")
            .define("showCustomTooltips", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }

    public static boolean showCustomTooltips() {
        return !SPEC.isLoaded() || SHOW_CUSTOM_TOOLTIPS.get();
    }
}
