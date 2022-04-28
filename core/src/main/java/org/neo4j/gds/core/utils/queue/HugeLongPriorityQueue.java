/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.core.utils.queue;

import com.carrotsearch.hppc.BitSet;
import org.neo4j.gds.core.utils.collection.primitive.PrimitiveLongIterable;
import org.neo4j.gds.core.utils.collection.primitive.PrimitiveLongIterator;
import org.neo4j.gds.core.utils.mem.MemoryEstimation;
import org.neo4j.gds.core.utils.mem.MemoryEstimations;
import org.neo4j.gds.core.utils.paged.HugeCursor;
import org.neo4j.gds.core.utils.paged.HugeDoubleArray;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.mem.MemoryUsage;

/**
 * A PriorityQueue specialized for longs that maintains a partial ordering of
 * its elements such that the smallest value can always be found in constant time.
 * The definition of what <i>small</i> means is up to the implementing subclass.
 * <p>
 * Put()'s and pop()'s require log(size) time but the remove() cost implemented here is linear.
 * <p>
 * <b>NOTE</b>: Iteration order is not specified.
 *
 * Implementation has been copied from https://issues.apache.org/jira/browse/SOLR-2092
 * and slightly adapted to our needs.
 */
public abstract class HugeLongPriorityQueue implements PrimitiveLongIterable {


    public static MemoryEstimation memoryEstimation() {
        return MemoryEstimations.builder(HugeLongPriorityQueue.class)
            .perNode("heap", HugeLongArray::memoryEstimation)
            .perNode("costs", HugeDoubleArray::memoryEstimation)
            .perNode("keys", MemoryUsage::sizeOfBitset)
            .build();
    }

    private final long capacity;

    private BitSet costKeys;
    private HugeLongArray heap;

    private long size = 0;

    protected HugeDoubleArray costValues;

    /**
     * Creates a new priority queue with the given capacity.
     * The size is fixed, the queue cannot shrink or grow.
     */
    protected HugeLongPriorityQueue(long capacity) {
        long heapSize;
        if (0 == capacity) {
            // We allocate 1 extra to avoid if statement in top()
            heapSize = 2;
        } else {
            // NOTE: we add +1 because all access to heap is
            // 1-based not 0-based.  heap[0] is unused.
            heapSize = capacity + 1;
        }
        this.capacity = capacity;
        this.costKeys = new BitSet(capacity);
        this.heap = HugeLongArray.newArray(heapSize);
        this.costValues = HugeDoubleArray.newArray(capacity);
    }

    /**
     * Adds an element associated with a cost to the queue in log(size) time.
     */
    public void add(long element, double cost) {
        assert element < capacity;
        addCost(element, cost);
        size++;
        heap.set(size, element);
        upHeap(size);
    }

    /**
     * Adds an element associated with a cost to the queue in log(size) time.
     * If the element was already in the queue, it's cost are updated and the
     * heap is reordered in log(size) time.
     */
    public void set(long element, double cost) {
        assert element < capacity;
        if (addCost(element, cost)) {
            update(element);
        } else {
            size++;
            heap.set(size, element);
            upHeap(size);
        }
    }

    /**
     * Returns the cost associated with the given element.
     * If the element has been popped from the queue, its
     * latest cost value is being returned.
     *
     * @return The double cost value for the element. 0.0D if the element is not found.
     */
    public double cost(long element) {
        return costValues.get(element);
    }

    /**
     * Returns true, iff the element is contained in the queue.
     */
    public boolean containsElement(long element) {
        return costKeys.get(element);
    }

    /**
     * Returns the element with the minimum cost from the queue in constant time.
     */
    public long top() {
        // We don't need to check size here: if maxSize is 0,
        // then heap is length 2 array with both entries null.
        // If size is 0 then heap[1] is already null.
        return heap.get(1);
    }

    /**
     * Removes and returns the element with the minimum cost from the queue in log(size) time.
     */
    public long pop() {
        if (size > 0) {
            long result = heap.get(1);    // save first value
            heap.set(1, heap.get(size));    // move last to first
            size--;
            downHeap(1);           // adjust heap
            removeCost(result);
            return result;
        } else {
            return -1;
        }
    }

    /**
     * Returns the number of elements currently stored in the queue.
     */
    public long size() {
        return size;
    }

    /**
     * Removes all entries from the queue, releases all buffers.
     * The queue can no longer be used afterwards.
     */
    public void release() {
        size = 0;
        heap = null;
        costKeys = null;
        costValues.release();
    }

    /**
     * Defines the ordering of the queue.
     * Returns true iff {@code a} is strictly less than {@code b}.
     * <p>
     * The default behavior assumes a min queue, where the value with smallest cost is on top.
     * To implement a max queue, return {@code b < a}.
     * The resulting order is not stable.
     */
    protected abstract boolean lessThan(long a, long b);

    private boolean addCost(long element, double cost) {
        double oldCost = costValues.get(element);
        boolean elementExists = costKeys.get(element);
        costKeys.set(element);
        costValues.set(element, cost);
        return oldCost != cost || !elementExists;
    }

    /**
     * @return true iff there are currently no elements stored in the queue.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Removes all entries from the queue.
     */
    public void clear() {
        size = 0;
        costKeys.clear();
    }

    private long findElementPosition(long element) {
        long limit = size + 1;
        HugeLongArray data = heap;
        HugeCursor<long[]> cursor = data.initCursor(data.newCursor(), 1, limit);
        while (cursor.next()) {
            long[] internalArray = cursor.array;
            int i = cursor.offset;
            int localLimit = cursor.limit - 4;
            for (; i <= localLimit; i += 4) {
                if (internalArray[i] == element) return i + cursor.base;
                if (internalArray[i + 1] == element) return i + 1 + cursor.base;
                if (internalArray[i + 2] == element) return i + 2 + cursor.base;
                if (internalArray[i + 3] == element) return i + 3 + cursor.base;
            }
            for (; i < cursor.limit; ++i) {
                if (internalArray[i] == element) return i + cursor.base;
            }
        }
        return 0;
    }

    private boolean upHeap(long origPos) {
        long i = origPos;
        // save bottom node
        long node = heap.get(i);
        // find parent of current node
        long j = i >>> 1;
        while (j > 0 && lessThan(node, heap.get(j))) {
            // shift parents down
            heap.set(i, heap.get(j));
            i = j;
            // find new parent of swapped node
            j = j >>> 1;
        }
        // install saved node
        heap.set(i, node);
        return i != origPos;
    }

    private void downHeap(long i) {
        // save top node
        long node = heap.get(i);
        // find smallest child of top node
        long j = i << 1;
        long k = j + 1;
        if (k <= size && lessThan(heap.get(k), heap.get(j))) {
            j = k;
        }
        while (j <= size && lessThan(heap.get(j), node)) {
            // shift up child
            heap.set(i, heap.get(j));
            i = j;
            // find smallest child of swapped node
            j = i << 1;
            k = j + 1;
            if (k <= size && lessThan(heap.get(k), heap.get(j))) {
                j = k;
            }
        }
        // install saved node
        heap.set(i, node);
    }

    private void update(long element) {
        long pos = findElementPosition(element);
        if (pos != 0) {
            if (!upHeap(pos) && pos < size) {
                downHeap(pos);
            }
        }
    }

    private void removeCost(long element) {
        costKeys.clear(element);
    }

    @Override
    public PrimitiveLongIterator iterator() {
        return new PrimitiveLongIterator() {

            int i = 1;

            @Override
            public boolean hasNext() {
                return i <= size;
            }

            /**
             * @throws ArrayIndexOutOfBoundsException when the iterator is exhausted.
             */
            @Override
            public long next() {
                return heap.get(i++);
            }
        };
    }

    /**
     * Returns a non growing min priority queue,
     * i.e. the element with the lowest priority is always on top.
     */
    public static HugeLongPriorityQueue min(long capacity) {
        return new HugeLongPriorityQueue(capacity) {
            @Override
            protected boolean lessThan(long a, long b) {
                return costValues.get(a) < costValues.get(b);
            }
        };
    }

    /**
     * Returns a non growing max priority queue,
     * i.e. the element with the highest priority is always on top.
     */
    public static HugeLongPriorityQueue max(long capacity) {
        return new HugeLongPriorityQueue(capacity) {
            @Override
            protected boolean lessThan(long a, long b) {
                return costValues.get(a) > costValues.get(b);
            }
        };
    }

}
