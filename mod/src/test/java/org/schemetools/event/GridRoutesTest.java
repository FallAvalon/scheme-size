package org.schemetools.event;

import org.schemetools.shared.GridRoutes;
import java.util.Arrays;

public final class GridRoutesTest {
    private static GridRoutes.Result route(int width, int[] cost, int start, int target, int steps) {
        return GridRoutes.calculate(width, cost.length / width, cost, new int[]{start}, new int[]{target}, cost.length, steps, 2_000_000_000L);
    }
    public static void main(String[] args) {
        int[] costs = new int[25]; Arrays.fill(costs, 1);
        // Wall with one gap: a shortest path must use the gap rather than crossing the wall.
        for (int y = 0; y < 4; y++) costs[y * 5 + 2] = -1;
        int[] unchanged = costs.clone();
        var found = route(5, costs, 0, 4, 100).routes().get(0);
        check(found.reached() && found.cells().length == 13, "wall detour");
        check(Arrays.stream(found.cells()).allMatch(c -> costs[c] > 0), "impassable cells avoided");
        check(Arrays.equals(costs, unchanged), "input not mutated");
        costs[22] = -1;
        check(route(5, costs, 0, 4, 100).routes().get(0).cells().length == 0, "unreachable route");
        Arrays.fill(costs, 1); costs[1] = 100;
        var weighted = route(5, costs, 0, 4, 100).routes().get(0);
        check(weighted.reached() && Arrays.stream(weighted.cells()).noneMatch(c -> c == 1), "weighted detour");
        check(!route(5, costs, 0, 4, 2).routes().get(0).reached(), "step limit is not reported as reached");
        var limit = GridRoutes.calculate(5, 5, costs, new int[]{0}, new int[]{24}, 1, 100, 2_000_000_000L);
        check(limit.state() == GridRoutes.State.LIMIT && !limit.routes().get(0).reached(), "expansion budget");
        Thread.currentThread().interrupt();
        check(route(5, costs, 0, 4, 100).state() == GridRoutes.State.CANCELLED, "cancellation");
        Thread.interrupted();
        int[] row = {1, 1, 1, 1, 1};
        var multiple = GridRoutes.calculate(5, 1, row, new int[]{1, 3}, new int[]{0, 4}, 5, 5, 2_000_000_000L);
        check(multiple.routes().stream().allMatch(r -> r.reached() && r.cells().length == 2), "nearest of multiple targets");
        System.out.println("GridRoutesTest passed");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
