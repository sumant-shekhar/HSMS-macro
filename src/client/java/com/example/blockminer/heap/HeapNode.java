package com.example.blockminer.heap;

import java.util.Objects;

public final class HeapNode<T> {
	private final T value;
	private final double cost;

	public HeapNode(T value, double cost) {
		this.value = Objects.requireNonNull(value, "value");
		if (!Double.isFinite(cost)) {
			throw new IllegalArgumentException("Heap cost must be finite");
		}
		this.cost = cost;
	}

	public T value() {
		return value;
	}

	public double cost() {
		return cost;
	}
}
