package org.schemetools.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bounded four-neighbour, weighted routes to the nearest destination. No game or UI dependency. */
public final class GridRoutes {
    public static final int MAX_CELLS = 1024 * 1024;
    public enum State { COMPLETE, LIMIT, CANCELLED }
    public record Route(int start, int[] cells, boolean reached) { }
    public record Result(List<Route> routes, State state, int expanded) { }
    private GridRoutes() { }

    public static Result calculate(int width, int height, int[] cost, int[] starts, int[] targets,
                                   int maxExpanded, int maxSteps, long maxNanos) {
        long cells = (long)width * height;
        if (width <= 0 || height <= 0 || cells > MAX_CELLS || cost.length != cells || starts.length > 32
                || targets.length == 0 || targets.length > 1024 || maxExpanded < 1 || maxSteps < 1 || maxSteps > 8192 || maxNanos < 1)
            throw new IllegalArgumentException("invalid route bounds");
        for (int value : cost) if (value < -1 || value == 0 || value > 1_000_000) throw new IllegalArgumentException("invalid route cost");
        int size = (int)cells;
        for (int value : starts) if (value < 0 || value >= size) throw new IllegalArgumentException("invalid start");
        for (int value : targets) if (value < 0 || value >= size) throw new IllegalArgumentException("invalid target");
        long[] distance = new long[size]; Arrays.fill(distance, Long.MAX_VALUE);
        int[] next = new int[size]; Arrays.fill(next, -1);
        boolean[] settled = new boolean[size];
        Heap heap = new Heap(size, distance);
        for (int target : targets) if (distance[target] != 0) { distance[target] = 0; heap.offer(target); }
        int expanded = 0;
        State state = State.COMPLETE;
        long began = System.nanoTime();
        while (heap.size > 0) {
            if ((expanded & 1023) == 0) {
                if (Thread.currentThread().isInterrupted()) { state = State.CANCELLED; break; }
                if (System.nanoTime() - began > maxNanos) { state = State.LIMIT; break; }
            }
            if (expanded >= maxExpanded) { state = State.LIMIT; break; }
            int cell = heap.poll();
            settled[cell] = true;
            expanded++;
            int x = cell % width, y = cell / width;
            int stepCost = cost[cell];
            if (stepCost < 0) continue;
            if (x > 0) relax(cell - 1, cell, stepCost, cost, distance, next, settled, heap);
            if (x + 1 < width) relax(cell + 1, cell, stepCost, cost, distance, next, settled, heap);
            if (y > 0) relax(cell - width, cell, stepCost, cost, distance, next, settled, heap);
            if (y + 1 < height) relax(cell + width, cell, stepCost, cost, distance, next, settled, heap);
        }
        List<Route> routes = new ArrayList<>();
        for (int start : starts) {
            if (!settled[start] || cost[start] < 0) { routes.add(new Route(start, new int[0], false)); continue; }
            int[] path = new int[Math.min(maxSteps, size)];
            int length = 0, cell = start;
            while (cell >= 0 && length < path.length) {
                path[length++] = cell;
                if (distance[cell] == 0) break;
                cell = next[cell];
            }
            boolean reached = length > 0 && distance[path[length - 1]] == 0;
            routes.add(new Route(start, Arrays.copyOf(path, length), reached));
        }
        return new Result(List.copyOf(routes), state, expanded);
    }

    private static void relax(int from, int to, int stepCost, int[] cost, long[] distance, int[] next, boolean[] settled, Heap heap) {
        if (settled[from] || cost[from] < 0) return;
        long candidate = distance[to] + stepCost;
        if (candidate < distance[from]) {
            distance[from] = candidate;
            next[from] = to;
            heap.offer(from);
        }
    }

    /** Indexed heap: at most one entry per cell, even after many cost improvements. */
    private static final class Heap {
        final int[] entries, positions;
        final long[] keys;
        int size;
        Heap(int capacity, long[] keys) { entries = new int[capacity]; positions = new int[capacity]; Arrays.fill(positions, -1); this.keys = keys; }
        boolean less(int a, int b) { return keys[a] < keys[b] || keys[a] == keys[b] && a < b; }
        void offer(int value) {
            int index = positions[value];
            if (index < 0) { index = size++; entries[index] = value; positions[value] = index; }
            while (index > 0) {
                int parent = (index - 1) >>> 1;
                if (!less(value, entries[parent])) break;
                move(index, entries[parent]); index = parent;
            }
            move(index, value);
        }
        int poll() {
            int value = entries[0], last = entries[--size];
            positions[value] = -1;
            if (size > 0) {
                int index = 0;
                while (index * 2 + 1 < size) {
                    int child = index * 2 + 1;
                    if (child + 1 < size && less(entries[child + 1], entries[child])) child++;
                    if (!less(entries[child], last)) break;
                    move(index, entries[child]); index = child;
                }
                move(index, last);
            }
            return value;
        }
        void move(int index, int value) { entries[index] = value; positions[value] = index; }
    }
}
