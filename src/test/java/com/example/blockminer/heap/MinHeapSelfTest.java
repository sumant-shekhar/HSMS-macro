package com.example.blockminer.heap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class MinHeapSelfTest {
	private MinHeapSelfTest() {
	}

	public static void main(String[] args) {
		Random random = new Random(0xB10C5E1L);
		for (int trial = 0; trial < 2_000; trial++) {
			int count = 3 + random.nextInt(28);
			MinHeap<Integer> heap = new MinHeap<>(count);
			List<Double> expected = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				double cost = random.nextDouble() * 1_000.0 - 500.0;
				expected.add(cost);
				heap.add(i, cost);
			}

			Collections.sort(expected);
			for (double expectedCost : expected) {
				HeapNode<Integer> actual = heap.pollNode();
				require(Double.compare(actual.cost(), expectedCost) == 0,
						"heap produced an out-of-order cost in trial " + trial);
			}
			require(heap.isEmpty(), "heap should be empty after draining trial " + trial);
		}

		MinHeap<Integer> duplicates = new MinHeap<>(0);
		duplicates.add(1, 4.0);
		duplicates.add(2, 4.0);
		duplicates.add(3, -1.0);
		require(duplicates.pollNode().cost() == -1.0, "negative minimum should be returned first");
		require(duplicates.pollNode().cost() == 4.0, "duplicate costs should remain valid");
		require(duplicates.pollNode().cost() == 4.0, "duplicate costs should remain valid");
		System.out.println("MinHeap self-test passed (2,000 randomized trials and duplicate-cost checks).");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
