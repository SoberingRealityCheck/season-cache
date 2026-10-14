package com.seasoncache.lod;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import sereneseasons.api.season.Season;

/**
 * Two 1x1 textures a shader can sample to read the current season. No text to parse.
 * LEGACY: only Nova Reimagined reads these. Delete when the pack drops Nova.
 * Ported from the original Serene Seasons x Distant Horizons (ItsThatNova, MIT).
 * Our first 26.2 rewrite dropped this by mistake. Nova Reimagined reads both.
 *
 * ssdh:season_meta  RGBA = spring / summer / autumn / winter (one is 1.0)
 * ssdh:season_phase RGB  = early / mid / late (one is 1.0)
 *
 * Pixels are ABGR ints, same as the original: red is the low byte.
 */
final class SeasonMetaTexture {
    static final SeasonMetaTexture INSTANCE = new SeasonMetaTexture();
    static final Identifier META_ID = Identifier.fromNamespaceAndPath("ssdh", "season_meta");
    static final Identifier PHASE_ID = Identifier.fromNamespaceAndPath("ssdh", "season_phase");

    private DynamicTexture meta;
    private DynamicTexture phase;
    private int lastMeta = Integer.MIN_VALUE;
    private int lastPhase = Integer.MIN_VALUE;

    private SeasonMetaTexture() {}

    private void ensureRegistered() {
        var tm = Minecraft.getInstance().getTextureManager();
        if (tm == null) return;
        if (meta == null) {
            meta = new DynamicTexture(() -> "ssdh_season_meta", new NativeImage(NativeImage.Format.RGBA, 1, 1, false));
            tm.register(META_ID, meta);
        }
        if (phase == null) {
            phase = new DynamicTexture(() -> "ssdh_season_phase", new NativeImage(NativeImage.Format.RGBA, 1, 1, false));
            tm.register(PHASE_ID, phase);
        }
    }

    /** Zero both. Used outside a world. */
    void clear() {
        set(0, 0);
    }

    void update(Season.SubSeason sub) {
        if (sub == null) {
            clear();
            return;
        }
        int season = switch (sub.getSeason()) {
            case SPRING -> 0x000000FF;
            case SUMMER -> 0x0000FF00;
            case AUTUMN -> 0x00FF0000;
            case WINTER -> 0xFF000000;
        };
        int ph = switch (sub) {
            case EARLY_SPRING, EARLY_SUMMER, EARLY_AUTUMN, EARLY_WINTER -> 0x000000FF;
            case MID_SPRING, MID_SUMMER, MID_AUTUMN, MID_WINTER -> 0x0000FF00;
            case LATE_SPRING, LATE_SUMMER, LATE_AUTUMN, LATE_WINTER -> 0x00FF0000;
        };
        set(season, ph);
    }

    // Only touch the GPU when a value changes.
    private void set(int metaPacked, int phasePacked) {
        ensureRegistered();
        if (meta != null && metaPacked != lastMeta) {
            meta.getPixels().setPixelABGR(0, 0, metaPacked);
            meta.upload();
            lastMeta = metaPacked;
        }
        if (phase != null && phasePacked != lastPhase) {
            phase.getPixels().setPixelABGR(0, 0, phasePacked);
            phase.upload();
            lastPhase = phasePacked;
        }
    }
}
