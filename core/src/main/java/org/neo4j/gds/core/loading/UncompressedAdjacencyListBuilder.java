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
package org.neo4j.gds.core.loading;

import org.neo4j.gds.core.huge.UncompressedAdjacencyList;
import org.neo4j.gds.core.utils.paged.HugeIntArray;
import org.neo4j.gds.core.utils.paged.HugeLongArray;

import java.util.Arrays;

public final class UncompressedAdjacencyListBuilder implements AdjacencyListBuilder<long[], UncompressedAdjacencyList> {

    private final BumpAllocator<long[]> builder;

    UncompressedAdjacencyListBuilder() {
        this.builder = new BumpAllocator<>(Factory.INSTANCE);
    }

    @Override
    public Allocator newAllocator() {
        return new Allocator(this.builder.newLocalAllocator());
    }

    @Override
    public UncompressedAdjacencyList build(HugeIntArray degrees, HugeLongArray offsets) {
        var intoPages = builder.intoPages();
        reorder(intoPages, offsets, degrees);
        return new UncompressedAdjacencyList(intoPages, degrees, offsets);
    }

    private enum Factory implements BumpAllocator.Factory<long[]> {
        INSTANCE;

        @Override
        public long[][] newEmptyPages() {
            return new long[0][];
        }

        @Override
        public long[] newPage(int length) {
            return new long[length];
        }

        @Override
        public long[] copyOfPage(long[] longs, int length) {
            return Arrays.copyOf(longs, length);
        }

        @Override
        public int lengthOfPage(long[] longs) {
            return longs.length;
        }
    }

    public static final class Allocator implements AdjacencyListBuilder.Allocator<long[]> {

        private final BumpAllocator.LocalAllocator<long[]> allocator;

        private Allocator(BumpAllocator.LocalAllocator<long[]> allocator) {
            this.allocator = allocator;
        }

        @Override
        public void close() {
        }

        @Override
        public long write(long[] properties, int length) {
            return allocator.insert(properties, length);
        }
    }
}
