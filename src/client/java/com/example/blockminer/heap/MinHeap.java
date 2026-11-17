package com.example.blockminer.heap;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public final class MinHeap<T> {
	private final List<HeapNode<T>> items;

	public MinHeap(int initialCapacity) {
		if (initialCapacity < 0) {
			throw new IllegalArgumentException("Initial capacity cannot be negative");
		}
		items = new ArrayList<>(initialCapacity);
	}

	public void add(T value, double cost) {
		add(new HeapNode<>(value, cost));
	}

	public void add(HeapNode<T> node) {
		items.add(node);
		heapUp(items.size() - 1);
	}

	public T poll() {
		return pollNode().value();
	}

	public HeapNode<T> pollNode() {
		if (items.isEmpty()) {
			throw new NoSuchElementException("Cannot poll an empty min-heap");
		}

		HeapNode<T> minimum = items.getFirst();
		HeapNode<T> last = items.removeLast();
		if (!items.isEmpty()) {
			items.set(0, last);
			heapDown(0);
		}
		return minimum;
	}

	public boolean isEmpty() {
		return items.isEmpty();
	}

	public int size() {
		return items.size();
	}

	public List<T> getBlocks() {
		List<HeapNode<T>> ordered = new ArrayList<>(items);
		ordered.sort((left, right) -> Double.compare(left.cost(), right.cost()));
		return ordered.stream().map(HeapNode::value).toList();
	}

	public double getCost(T value) {
		return items.stream()
				.filter(node -> node.value().equals(value))
				.mapToDouble(HeapNode::cost)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Value not found in heap"));
	}

	private void heapUp(int index) {
		while (index > 0) {
			int parent = (index - 1) >>> 1;
			if (items.get(parent).cost() <= items.get(index).cost()) {
				return;
			}
			swap(parent, index);
			index = parent;
		}
	}

	private void heapDown(int index) {
		while (true) {
			int left = (index << 1) + 1;
			if (left >= items.size()) {
				return;
			}

			int right = left + 1;
			int smallerChild = right < items.size()
					&& items.get(right).cost() < items.get(left).cost()
					? right
					: left;
			if (items.get(index).cost() <= items.get(smallerChild).cost()) {
				return;
			}
			swap(index, smallerChild);
			index = smallerChild;
		}
	}

	private void swap(int first, int second) {
		HeapNode<T> value = items.get(first);
		items.set(first, items.get(second));
		items.set(second, value);
	}
}
