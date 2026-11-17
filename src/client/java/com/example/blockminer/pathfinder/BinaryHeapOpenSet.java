package com.example.blockminer.pathfinder;

import java.util.Arrays;

public final class BinaryHeapOpenSet {
    private PathNode[] items;
    private int size;

    public BinaryHeapOpenSet() {
        this(1024);
    }

    public BinaryHeapOpenSet(int initialCapacity) {
        if (initialCapacity < 1) {
            throw new IllegalArgumentException("initialCapacity must be positive");
        }
        items = new PathNode[Math.max(2, initialCapacity + 1)];
    }

    public void add(PathNode node) {
        if (node.heapPosition != -1) {
            throw new IllegalArgumentException("node is already in an open set");
        }
        ensureCapacity(size + 1);
        items[++size] = node;
        node.heapPosition = size;
        siftUp(size);
    }

    public void relocate(PathNode node) {
        int index = node.heapPosition;
        if (index < 1 || index > size || items[index] != node) {
            throw new IllegalArgumentException("node is not in this open set");
        }
        siftUp(index);
        siftDown(node.heapPosition);
    }

    public PathNode poll() {
        if (size == 0) {
            throw new IllegalStateException("cannot poll an empty open set");
        }
        PathNode minimum = items[1];
        PathNode last = items[size];
        items[size--] = null;
        minimum.heapPosition = -1;
        if (size > 0) {
            items[1] = last;
            last.heapPosition = 1;
            siftDown(1);
        }
        return minimum;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    private void ensureCapacity(int requiredSize) {
        if (requiredSize >= items.length) {
            items = Arrays.copyOf(items, Math.max(items.length * 2, requiredSize + 1));
        }
    }

    private void siftUp(int index) {
        PathNode node = items[index];
        while (index > 1) {
            int parentIndex = index >>> 1;
            PathNode parent = items[parentIndex];
            if (node.totalCost >= parent.totalCost) {
                break;
            }
            items[index] = parent;
            parent.heapPosition = index;
            index = parentIndex;
        }
        items[index] = node;
        node.heapPosition = index;
    }

    private void siftDown(int index) {
        PathNode node = items[index];
        while ((index << 1) <= size) {
            int childIndex = index << 1;
            int rightIndex = childIndex + 1;
            if (rightIndex <= size && items[rightIndex].totalCost < items[childIndex].totalCost) {
                childIndex = rightIndex;
            }
            if (node.totalCost <= items[childIndex].totalCost) {
                break;
            }
            items[index] = items[childIndex];
            items[index].heapPosition = index;
            index = childIndex;
        }
        items[index] = node;
        node.heapPosition = index;
    }
}
