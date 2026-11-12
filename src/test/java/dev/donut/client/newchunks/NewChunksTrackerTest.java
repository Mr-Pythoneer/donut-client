package dev.donut.client.newchunks;

import static org.junit.jupiter.api.Assertions.*;

import dev.donut.client.newchunks.NewChunksTracker.ChunkReceived;
import dev.donut.client.newchunks.NewChunksTracker.FluidUpdate;
import dev.donut.client.newchunks.NewChunksTracker.Mark;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

/** The NewChunks heuristic as a pure function over synthetic packet event streams. */
class NewChunksTrackerTest {
    private static final long A = ChunkPos.asLong(0, 0), B = ChunkPos.asLong(1, 0), C = ChunkPos.asLong(-4, 9), D = ChunkPos.asLong(30, -30);

    @Test void freshlyGeneratedChunksAreMarkedNewByFlowNextToASource() {
        var tracker = NewChunksTracker.replay(List.of(
                new ChunkReceived(A, false),               // generated: sources only, fluid ticks pending
                new FluidUpdate(A, true, true),            // the first tick spreads water beside its source
                new FluidUpdate(A, true, true)));
        assertEquals(Mark.NEW, tracker.mark(A));
        assertEquals(1, tracker.newCount());
    }

    @Test void chunksArrivingWithSettledFlowAreOld() {
        var tracker = NewChunksTracker.replay(List.of(
                new ChunkReceived(B, true),                // flowing water already in the chunk data
                new FluidUpdate(B, true, true)));          // later flow (a bucket, a broken block) changes nothing
        assertEquals(Mark.OLD, tracker.mark(B));
        assertEquals(0, tracker.newCount());
        assertEquals(1, tracker.oldCount());
    }

    @Test void updatesThatAreNotFlowNextToASourceAreIgnored() {
        var tracker = NewChunksTracker.replay(List.of(
                new ChunkReceived(C, false),
                new FluidUpdate(C, false, true),           // a source block placed
                new FluidUpdate(C, true, false),           // flow far from any source (draining)
                new ChunkReceived(D, false)));             // a chunk with no liquid at all
        assertNull(tracker.mark(C));
        assertNull(tracker.mark(D), "Chunks without liquid stay unclassified");
        assertEquals(0, tracker.size());
    }

    @Test void marksAreStickyForTheSession() {
        // Come back to a new chunk: it now arrives with its settled flow, but it was seen being generated.
        var tracker = NewChunksTracker.replay(List.of(
                new ChunkReceived(A, false), new FluidUpdate(A, true, true),
                new ChunkReceived(A, true),
                new ChunkReceived(B, true), new ChunkReceived(B, false), new FluidUpdate(B, true, true)));
        assertEquals(Mark.NEW, tracker.mark(A));
        assertEquals(Mark.OLD, tracker.mark(B));
        assertFalse(tracker.fluidUpdate(A, true, true), "Already new: not reported again");
        // After a reset (disconnect, dimension change) the same chunk is judged afresh: settled flow means old.
        tracker.clear();
        tracker.chunkReceived(A, true);
        assertEquals(Mark.OLD, tracker.mark(A));
    }

    @Test void memoryIsBounded() {
        var tracker = new NewChunksTracker();
        for (int i = 0; i < NewChunksTracker.MAX_CHUNKS + 10; i++) tracker.chunkReceived(ChunkPos.asLong(i, 7), true);
        assertTrue(tracker.size() <= NewChunksTracker.MAX_CHUNKS);
    }
}
