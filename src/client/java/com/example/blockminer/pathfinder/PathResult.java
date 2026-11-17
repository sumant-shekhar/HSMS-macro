package com.example.blockminer.pathfinder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PathResult {
    private final List<PathNode> nodes;

    public PathResult(PathNode endNode) {
        List<PathNode> reversed = new ArrayList<>();
        for (PathNode node = endNode; node != null; node = node.parentNode) {
            reversed.add(node);
        }
        Collections.reverse(reversed);
        nodes = List.copyOf(reversed);
    }

    public List<PathNode> getNodes() {
        return nodes;
    }

    public double getCost() {
        return nodes.get(nodes.size() - 1).costSoFar;
    }
}
