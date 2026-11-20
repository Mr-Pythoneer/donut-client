/*
 * Donut Client — contributions © 2026 Mr_Pythoneer.
 * Part of the Donut Client project, under its existing licenses (MIT; some
 * components GPL-3.0-only — see LICENSE and src/main/resources/licenses/).
 * These licenses require this copyright/authorship notice be kept; they do not
 * permit removing it or presenting this work as your own.
 * Authorship signature: DNUT-337F9FFA-39CF-4CB3-A453-49620AC0A83E
 */
package dev.donut.client.newchunks;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;

/**
 * The NewChunks decision, as a pure state machine over packet-derived events (no game objects, so it is unit tested).
 *
 * <p>The heuristic: when the server generates a chunk, its liquids are placed as still sources with pending fluid ticks.
 * Those ticks run after the chunk has been sent, so the client then receives block updates for <em>flowing</em> liquid
 * next to a source. A chunk that already existed arrives with its liquids settled: any flowing liquid is already in the
 * chunk data. So:
 * <ul>
 *   <li>a chunk whose received data already contains flowing liquid is <b>old</b>;</li>
 *   <li>a flowing-liquid update beside a source, in a chunk not marked old, marks it <b>new</b>.</li>
 * </ul>
 * Marks are sticky for the session: a new chunk stays new when you come back to it (its settled water would otherwise
 * make it look old), and an old chunk ignores later flow. Chunks without liquids are never classified.
 */
public final class NewChunksTracker {
    public enum Mark { NEW, OLD }

    /** One packet-derived observation. {@code chunk} is {@code ChunkPos.asLong(x, z)}. */
    public sealed interface Event permits ChunkReceived, FluidUpdate { long chunk(); }

    /** A full chunk arrived; {@code flowingInData} = its data already holds flowing (non-source) liquid. */
    public record ChunkReceived(long chunk, boolean flowingInData) implements Event { }

    /** A block update set liquid; {@code flowing} = not a source, {@code besideSource} = a source is beside or above it. */
    public record FluidUpdate(long chunk, boolean flowing, boolean besideSource) implements Event { }

    /** Upper bound on remembered chunks; the oldest half is dropped past it (about 2 MB). */
    public static final int MAX_CHUNKS = 200_000;

    private final Long2ObjectOpenHashMap<Mark> marks = new Long2ObjectOpenHashMap<>();
    private int newCount, oldCount;

    public void accept(Event event) {
        switch (event) {
            case ChunkReceived received -> chunkReceived(received.chunk(), received.flowingInData());
            case FluidUpdate update -> fluidUpdate(update.chunk(), update.flowing(), update.besideSource());
        }
    }

    public void chunkReceived(long chunk, boolean flowingInData) {
        if (flowingInData && !marks.containsKey(chunk)) put(chunk, Mark.OLD);
    }

    /** Returns true when this update newly marked the chunk as new. */
    public boolean fluidUpdate(long chunk, boolean flowing, boolean besideSource) {
        if (!flowing || !besideSource || marks.containsKey(chunk)) return false;
        put(chunk, Mark.NEW);
        return true;
    }

    private void put(long chunk, Mark mark) {
        if (marks.size() >= MAX_CHUNKS) clear();
        marks.put(chunk, mark);
        if (mark == Mark.NEW) newCount++; else oldCount++;
    }

    public Mark mark(long chunk) { return marks.get(chunk); }
    public int newCount() { return newCount; }
    public int oldCount() { return oldCount; }
    public int size() { return marks.size(); }

    /** Visits every marked chunk. */
    public void forEach(java.util.function.BiConsumer<Long, Mark> action) {
        for (var entry : marks.long2ObjectEntrySet()) action.accept(entry.getLongKey(), entry.getValue());
    }

    public void clear() {
        marks.clear();
        newCount = oldCount = 0;
    }

    /** Pure replay of an event stream, for tests and documentation. */
    public static NewChunksTracker replay(List<? extends Event> events) {
        NewChunksTracker tracker = new NewChunksTracker();
        for (Event event : events) tracker.accept(event);
        return tracker;
    }
}
