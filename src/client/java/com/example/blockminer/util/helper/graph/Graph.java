package com.example.blockminer.util.helper.graph;

import com.google.gson.annotations.Expose;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public final class Graph<T> {
	@Expose
	public final Map<T, List<T>> map = new HashMap<>();

	public void add(T source) {
		Objects.requireNonNull(source, "source");
		if (map.isEmpty()) {
			map.computeIfAbsent(source, ignored -> new ArrayList<>());
		}
	}

	public void add(T source, T target, boolean bidirectional) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(target, "target");
		map.computeIfAbsent(source, ignored -> new ArrayList<>()).add(target);
		map.computeIfAbsent(target, ignored -> new ArrayList<>());
		if (bidirectional) {
			map.get(target).add(source);
		}
	}

	public void update(T oldNode, T newNode) {
		if (oldNode == null || newNode == null) {
			throw new IllegalArgumentException("Nodes cannot be null");
		}
		if (oldNode.equals(newNode)) {
			return;
		}
		List<T> oldEdges = map.remove(oldNode);
		if (oldEdges != null) {
			map.put(newNode, oldEdges);
		}
		map.replaceAll((key, edges) -> edges.stream()
				.map(edge -> edge.equals(oldNode) ? newNode : edge)
				.collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
	}

	public void remove(T node) {
		Objects.requireNonNull(node, "node");
		map.remove(node);
		map.values().forEach(edges -> edges.removeIf(node::equals));
	}

	public List<T> findPath(T start, T end) {
		if (start == null || end == null || !map.containsKey(start) || !map.containsKey(end)) {
			return List.of();
		}
		Queue<T> queue = new ArrayDeque<>();
		Set<T> visited = new HashSet<>();
		Map<T, T> parent = new HashMap<>();
		queue.add(start);
		visited.add(start);
		parent.put(start, null);

		while (!queue.isEmpty()) {
			T current = queue.remove();
			if (current.equals(end)) {
				LinkedList<T> path = new LinkedList<>();
				for (T node = end; node != null; node = parent.get(node)) {
					path.addFirst(node);
				}
				return path;
			}
			for (T neighbour : map.get(current)) {
				if (visited.add(neighbour)) {
					queue.add(neighbour);
					parent.put(neighbour, current);
				}
			}
		}
		return List.of();
	}
}
