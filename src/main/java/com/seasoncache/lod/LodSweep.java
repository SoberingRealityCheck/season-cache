package com.seasoncache.lod;

import com.mojang.brigadier.Command;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiLevelLoadEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.common.wrappers.block.AbstractDhTintGetter;
import com.seibel.distanthorizons.core.level.ClientLevelModule;
import com.seibel.distanthorizons.core.level.DhClientLevel;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.render.QuadTree.LodRenderSection;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Recolors Distant Horizons LODs when the Serene Seasons season changes.
 *
 * Why this exists: DH builds LOD buffers from block + biome data and bakes
 * the tint colors in at build time. Serene Seasons changes the tint every
 * sub-season. Nothing tells DH, so far LODs keep last season's colors.
 *
 * What we do on a season change (or on joining a world):
 *   1. clear DH's color caches
 *   2. rebuild every rendered LOD section, nearest first, a few per tick
 *
 * DH keeps the old buffer on screen until the new one is ready, so there is
 * no flash and no ring. It only costs CPU while the sweep runs.
 *
 * Client only. The server stores block + biome, never colors.
 *
 * Idea from ItsThatNova's "Serene Seasons X Distant Horizons" (MIT).
 * This is a rewrite for 26.2 and shares no code with it.
 */
public final class LodSweep {

    static final Logger LOG = LoggerFactory.getLogger("seasoncache");

    // Ticks to wait before sweeping, so a burst of changes becomes one sweep.
    // Joining a world waits longer: DH is still loading and the season state
    // may not have synced from the server yet. Guesses. Tune by watching.
    static final int SEASON_DELAY_TICKS = 10;
    static final int JOIN_DELAY_TICKS = 60;

    // The first sweep leaves some sections on old colors. Seen in testing: far grass
    // stays on the previous season until a second sweep. Guess at why: DH finishes a
    // rebuild it had already started with the old colors and drops our request.
    // So after the queue drains we wait, clear again, and sweep once more.
    // The wait is a guess. Tune by watching.
    static final int SWEEPS_PER_CHANGE = 2;
    static final int SETTLE_DELAY_TICKS = 400; // 20 seconds

    // Sections we hand to DH per tick. DH rebuilds on its own threads, this
    // just stops us queuing thousands at once. Guess. Tune by watching.
    //
    // We go through DhClientLevel.clientside.reloadPos(), DH's own queue.
    // It is safe to call from the client thread and DH retries busy sections
    // itself. The catch: reloadPos also queues the 4 neighbors (DH does that
    // for edge culling), so a full sweep does some sections up to 5 times.
    // We pay that CPU to avoid poking LodRenderSection from outside the
    // quadtree tick. The count is low because each call fans out.
    static final int SECTIONS_PER_TICK = 4;

    private final ArrayDeque<Long> pending = new ArrayDeque<>();
    private ClientLevelModule target = null;
    private Season.SubSeason lastSubSeason = null;

    private Integer lastEpoch = null;

    // Set from DH's event thread, read on the client tick. -1 means nothing armed.
    private volatile int armedTicks = -1;

    // Sweeps still owed for the latest change, and the wait before the next one.
    private int sweepsLeft = 0;
    private int settleTicks = -1;

    /** Called from SeasonCacheClient, and only when Distant Horizons is installed. */
    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        DhApiEventRegister.on(DhApiLevelLoadEvent.class, new DhApiLevelLoadEvent() {
            @Override
            public void onLevelLoad(DhApiEventParam<EventParam> param) {
                if (param.value == null || param.value.levelWrapper == null) return;
                if (!isOverworld(param.value.levelWrapper)) return;
                LOG.info("DH overworld loaded. Arming a sweep in {} ticks.", JOIN_DELAY_TICKS);
                armedTicks = JOIN_DELAY_TICKS;
            }
        });

        SnowTint.register();

        // Manual trigger, for watching it work: /ssdh reload
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("ssdh")
                        .then(ClientCommands.literal("reload").executes(ctx -> {
                            int n = startSweep();
                            ctx.getSource().sendFeedback(Component.literal("Seasonal LODs: queued " + n + " sections"));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(ClientCommands.literal("snow")
                                .executes(ctx -> {
                                    ctx.getSource().sendFeedback(Component.literal(snowReport()));
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(ClientCommands.literal("on").executes(ctx -> setSnow(ctx.getSource(), true)))
                                .then(ClientCommands.literal("off").executes(ctx -> setSnow(ctx.getSource(), false)))
                                .then(ClientCommands.literal("reset").executes(ctx -> {
                                    SnowTint.resetCounters();
                                    ctx.getSource().sendFeedback(Component.literal("Snow counters reset"));
                                    return Command.SINGLE_SUCCESS;
                                })))));

        LOG.info("Seasonal LODs loaded.");
    }

    private void onTick(Minecraft mc) {
        if (mc.level == null) {
            SeasonMetaTexture.INSTANCE.clear();
            lastSubSeason = null;
            lastEpoch = null;
            armedTicks = -1;
            sweepsLeft = 0;
            settleTicks = -1;
            pending.clear();
            target = null;
            return;
        }

        Season.SubSeason now = currentSubSeason(mc);
        SeasonMetaTexture.INSTANCE.update(now); // shaders read the season from this
        if (now != null) {
            // First sight after joining is not a change. The join sweep covers it.
            if (lastSubSeason != null && now != lastSubSeason) {
                LOG.info("Season changed {} -> {}. Arming a sweep.", lastSubSeason, now);
                armedTicks = SEASON_DELAY_TICKS;
            }
            lastSubSeason = now;
        }

        // Season Cache restarts its snow map on a season change. The bits arrive after
        // the season does, so sweep again when its epoch moves. Same delay as a season change.
        Integer epoch = SnowTint.epoch();
        if (epoch != null && lastEpoch != null && !epoch.equals(lastEpoch)) {
            LOG.info("Season Cache epoch {} -> {}. Arming a sweep.", lastEpoch, epoch);
            armedTicks = SEASON_DELAY_TICKS;
        }
        lastEpoch = epoch;

        int armed = armedTicks;
        if (armed >= 0) {
            armedTicks = armed - 1;
            if (armed == 0) {
                settleTicks = -1;
                sweepsLeft = startSweep() > 0 ? SWEEPS_PER_CHANGE - 1 : 0;
            }
        }

        // A DH update can rename the internals we touch. Log it and stop
        // sweeping. Never take the client tick down with us.
        try {
            pump();
        } catch (LinkageError | Exception e) {
            LOG.error("Sweep failed. Giving up until the next season change.", e);
            pending.clear();
            sweepsLeft = 0;
        }

        // Queue drained and another sweep is owed: wait, then clear and sweep again.
        // A new season change (armedTicks >= 0) restarts everything, so skip then.
        if (sweepsLeft > 0 && pending.isEmpty() && armedTicks < 0) {
            if (settleTicks < 0) {
                settleTicks = SETTLE_DELAY_TICKS;
            } else if (--settleTicks == 0) {
                settleTicks = -1;
                sweepsLeft--;
                LOG.info("Settle sweep, {} left after this one.", sweepsLeft);
                startSweep();
            }
        }
    }

    private int setSnow(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src, boolean on) {
        SnowTint.enabled = on;
        int n = startSweep(); // new setting only shows after a rebuild
        src.sendFeedback(Component.literal("Snow tint " + (on ? "on" : "off") + ". Queued " + n + " sections."));
        return Command.SINGLE_SUCCESS;
    }

    private String snowReport() {
        return "Snow tint " + (SnowTint.enabled ? "on" : "off")
                + " | color events " + SnowTint.calls.get()
                + " | in snowy chunks " + SnowTint.snowy.get()
                + " | recolored " + SnowTint.applied.get()
                + " | no server bit yet " + SnowTint.unknown.get()
                + " | epoch " + SnowTint.epoch();
    }

    private static Season.SubSeason currentSubSeason(Minecraft mc) {
        try {
            ISeasonState state = SeasonHelper.getSeasonState(mc.level);
            return state == null ? null : state.getSubSeason();
        } catch (Exception e) {
            return null;
        }
    }

    // Serene Seasons only runs in the overworld by default. Same assumption as upstream.
    private static boolean isOverworld(IDhApiLevelWrapper level) {
        return "minecraft:overworld".equals(level.getDimensionName());
    }

    private static IDhApiLevelWrapper findOverworld() {
        var proxy = DhApi.Delayed.worldProxy;
        if (proxy == null) return null;
        for (IDhApiLevelWrapper level : proxy.getAllLoadedLevelWrappers()) {
            if (isOverworld(level)) return level;
        }
        return null;
    }

    /** Clear caches, then queue every rendered section, nearest first. Returns how many. */
    private int startSweep() {
        Minecraft mc = Minecraft.getInstance();
        IDhApiLevelWrapper level = findOverworld();
        if (level == null || mc.player == null) {
            LOG.warn("Sweep skipped: no DH overworld level or no player yet.");
            return 0;
        }

        // Both caches hold colors computed for the old season.
        AbstractDhTintGetter.clear();
        if (level instanceof IClientLevelWrapper clientLevel) clientLevel.clearBlockColorCache();

        var sections = new ArrayList<LodRenderSection>();
        try {
            if (!(level instanceof ILevelWrapper wrapper)) return 0;
            if (!(wrapper.getDhLevel() instanceof DhClientLevel dhLevel)) return 0;
            ClientLevelModule.ClientRenderState state = dhLevel.clientside.ClientRenderStateRef.get();
            if (state == null) {
                LOG.warn("Sweep skipped: DH has no render state yet.");
                return 0;
            }
            state.quadtree.populateListWithEnabledRenderSections(sections);
            target = dhLevel.clientside;
        } catch (LinkageError | Exception e) {
            LOG.error("Could not list DH sections. Colors will fix themselves as DH rebuilds.", e);
            return 0;
        }

        var player = new DhBlockPos2D((int) mc.player.getX(), (int) mc.player.getZ());
        sections.sort(Comparator.comparingInt(s -> DhSectionPos.getManhattanBlockDistance(s.pos, player)));

        pending.clear();
        for (LodRenderSection s : sections) pending.add(s.pos);
        LOG.info("Sweep started: {} sections.", sections.size());
        return sections.size();
    }

    /** Hand up to SECTIONS_PER_TICK positions to DH's reload queue. */
    private void pump() {
        if (target == null) return;
        for (int budget = SECTIONS_PER_TICK; budget > 0 && !pending.isEmpty(); budget--) {
            target.reloadPos(pending.pollFirst());
        }
    }
}
