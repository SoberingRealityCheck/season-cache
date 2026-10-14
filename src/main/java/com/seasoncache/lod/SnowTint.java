package com.seasoncache.lod;

import com.seasoncache.api.SeasonCacheClientApi;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBlockColorOverrideEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Paints snow on far terrain without a shader.
 *
 * Season Cache streams one bit per chunk: snowy or not. DH asks us for the color
 * of each block it puts in a LOD. In a snowy chunk we push grass, leaves and the
 * like toward white. Default Complementary needs nothing from us, because DH
 * colors its own LODs.
 *
 * PROTOTYPE. Things we do not know yet, so watch /ssdh snow in game:
 *  - Does DH fire the event on a rebuild (our sweep), or only when it first builds
 *    a LOD from chunk data? If the counters stay at 0 after a sweep, it is the latter.
 *  - Is the block list right? We cannot tell a column's top block from a cliff face
 *    here, so we only whiten block types that sit on top (grass, leaves, ferns).
 *  - The data is one bit per chunk, so snow edges land on chunk borders.
 *
 * Only loaded when Distant Horizons is installed (see SeasonCacheClient).
 */
final class SnowTint {

    // Snow white with a hint of blue, and how far we pull toward it. Guesses. Tune by watching.
    private static final int SNOW_R = 240, SNOW_G = 245, SNOW_B = 250;
    private static final float STRENGTH = 0.85f;

    // Words in a block's serial string that mean "snow would sit on this". Guess.
    // The serial string format is DH's own, so we match by substring and cache the answer.
    private static final String[] TOP_BLOCKS = {
            "grass_block", "short_grass", "tall_grass", "fern", "leaves", "podzol", "mycelium", "bush", "azalea"
    };

    private static final Map<String, Boolean> WHITEN = new ConcurrentHashMap<>();

    // For /ssdh snow. Calls is every event. Snowy is calls in a chunk the server calls snowy.
    // Applied is calls we actually recolored. Unknown is calls where the server has no bit yet.
    static final AtomicLong calls = new AtomicLong();
    static final AtomicLong snowy = new AtomicLong();
    static final AtomicLong applied = new AtomicLong();
    static final AtomicLong unknown = new AtomicLong();

    static volatile boolean enabled = true;

    private SnowTint() {
    }

    static void register() {
        DhApiEventRegister.on(DhApiBlockColorOverrideEvent.class, new DhApiBlockColorOverrideEvent() {
            @Override
            public void onBlockColorOverridden(DhApiEventParam<EventParam> param) {
                if (!enabled || param.value == null) return;
                tint(param.value);
            }
        });
    }

    private static void tint(DhApiBlockColorOverrideEvent.EventParam p) {
        calls.incrementAndGet();

        IDhApiLevelWrapper level = p.getLevelWrapper();
        if (level == null || !"minecraft:overworld".equals(level.getDimensionName())) return;

        Boolean isSnowy = SeasonCacheClientApi.getChunkSnowState(
                Level.OVERWORLD, p.getBlockPosX() >> 4, p.getBlockPosZ() >> 4);
        if (isSnowy == null) {
            unknown.incrementAndGet();
            return;
        }
        if (!isSnowy) return;
        snowy.incrementAndGet();

        if (p.getBlockStateWrapper() == null) return;
        if (!WHITEN.computeIfAbsent(p.getBlockStateWrapper().getSerialString(), SnowTint::isTopBlock)) return;

        p.setColor(mix(p.getRed(), SNOW_R), mix(p.getGreen(), SNOW_G), mix(p.getBlue(), SNOW_B));
        applied.incrementAndGet();
    }

    private static boolean isTopBlock(String serial) {
        for (String word : TOP_BLOCKS) {
            if (serial.contains(word)) return true;
        }
        return false;
    }

    private static int mix(int from, int to) {
        int v = Math.round(from + (to - from) * STRENGTH);
        return Math.max(0, Math.min(255, v));
    }

    /** Season Cache's epoch. A new one means the server restarted the snow map, so colors are stale. */
    static Integer epoch() {
        return SeasonCacheClientApi.currentEpoch(Level.OVERWORLD);
    }

    static void resetCounters() {
        calls.set(0);
        snowy.set(0);
        applied.set(0);
        unknown.set(0);
    }
}
