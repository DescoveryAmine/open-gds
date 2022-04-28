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

import com.carrotsearch.hppc.sorting.IndirectSort;
import org.neo4j.gds.PropertyMappings;
import org.neo4j.gds.api.AdjacencyList;
import org.neo4j.gds.api.AdjacencyProperties;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.compress.AdjacencyCompressor;
import org.neo4j.gds.core.compress.AdjacencyCompressorFactory;
import org.neo4j.gds.core.compress.LongArrayBuffer;
import org.neo4j.gds.core.utils.AscendingLongComparator;
import org.neo4j.gds.core.utils.paged.HugeIntArray;
import org.neo4j.gds.core.utils.paged.HugeLongArray;

import java.util.Arrays;
import java.util.function.LongSupplier;

public final class RawCompressor implements AdjacencyCompressor {

    private final AdjacencyListBuilder.Allocator<long[]> adjacencyAllocator;
    private final AdjacencyListBuilder.Allocator<long[]>[] propertiesAllocators;
    private final HugeIntArray adjacencyDegrees;
    private final HugeLongArray adjacencyOffsets;
    private final HugeLongArray propertyOffsets;
    private final boolean noAggregation;
    private final Aggregation[] aggregations;

    public static AdjacencyCompressorFactory factory(
        LongSupplier nodeCountSupplier,
        AdjacencyListBuilderFactory<long[], ? extends AdjacencyList, long[], ? extends AdjacencyProperties> adjacencyListBuilderFactory,
        PropertyMappings propertyMappings,
        Aggregation[] aggregations,
        boolean noAggregation
    ) {
        @SuppressWarnings("unchecked")
        AdjacencyListBuilder<long[], ? extends AdjacencyProperties>[] propertyBuilders = new AdjacencyListBuilder[propertyMappings.numberOfMappings()];
        Arrays.setAll(propertyBuilders, i -> adjacencyListBuilderFactory.newAdjacencyPropertiesBuilder());

        return new Factory(
            nodeCountSupplier,
            adjacencyListBuilderFactory.newAdjacencyListBuilder(),
            propertyBuilders,
            noAggregation,
            aggregations
        );
    }

    private RawCompressor(
        AdjacencyListBuilder.Allocator<long[]> adjacencyAllocator,
        AdjacencyListBuilder.Allocator<long[]>[] propertiesAllocators,
        HugeIntArray adjacencyDegrees,
        HugeLongArray adjacencyOffsets,
        HugeLongArray propertyOffsets,
        boolean noAggregation,
        Aggregation[] aggregations
    ) {
        this.adjacencyAllocator = adjacencyAllocator;
        this.propertiesAllocators = propertiesAllocators;
        this.adjacencyDegrees = adjacencyDegrees;
        this.adjacencyOffsets = adjacencyOffsets;
        this.propertyOffsets = propertyOffsets;
        this.noAggregation = noAggregation;
        this.aggregations = aggregations;
    }

    @Override
    public int compress(
        long nodeId,
        byte[] targets,
        long[][] properties,
        int numberOfCompressedTargets,
        int compressedBytesSize,
        LongArrayBuffer buffer,
        ValueMapper mapper
    ) {
        if (properties != null) {
            return withProperties(
                nodeId,
                targets,
                properties,
                numberOfCompressedTargets,
                compressedBytesSize,
                buffer,
                mapper
            );
        } else {
            return withoutProperties(nodeId, targets, numberOfCompressedTargets, compressedBytesSize, buffer, mapper);
        }
    }

    @Override
    public void close() {
        adjacencyAllocator.close();
        for (var propertiesAllocator : propertiesAllocators) {
            if (propertiesAllocator != null) {
                propertiesAllocator.close();
            }
        }
    }

    private int withoutProperties(
        long nodeId,
        byte[] targets,
        int numberOfCompressedTargets,
        int compressedBytesSize,
        LongArrayBuffer buffer,
        ValueMapper mapper
    ) {
        // decompress target ids
        AdjacencyCompression.copyFrom(buffer, targets, numberOfCompressedTargets, compressedBytesSize, mapper);

        int degree = aggregate(buffer, aggregations[0]);

        long address = copy(buffer.buffer, degree, adjacencyAllocator);

        this.adjacencyDegrees.set(nodeId, degree);
        this.adjacencyOffsets.set(nodeId, address);

        return degree;
    }

    private int aggregate(LongArrayBuffer targets, Aggregation aggregation) {
        var values = targets.buffer;
        var length = targets.length;

        Arrays.sort(values, 0, length);

        if (aggregation == Aggregation.NONE) {
            return length;
        }

        int read = 1, write = 1;

        for (; read < length; ++read) {
            long value = values[read];
            if (value != values[read - 1]) {
                values[write++] = value;
            }
        }

        return write;
    }

    private int aggregateWithProperties(LongArrayBuffer targets, long[][] properties, Aggregation[] aggregations) {
        var values = targets.buffer;
        var length = targets.length;

        int[] order = IndirectSort.mergesort(0, length, new AscendingLongComparator(values));

        long[] outValues = new long[length];
        long[][] outProperties = new long[properties.length][length];

        int firstSortIdx = order[0];
        long value = values[firstSortIdx];
        long delta;

        outValues[0] = value;
        for (int i = 0; i < properties.length; i++) {
            outProperties[i][0] = properties[i][firstSortIdx];
        }

        int in = 1, out = 1;

        if (noAggregation) {
            for (; in < length; ++in) {
                int sortIdx = order[in];

                for (int i = 0; i < properties.length; i++) {
                    outProperties[i][out] = properties[i][sortIdx];
                }

                outValues[out++] = values[sortIdx];
            }
        } else {
            for (; in < length; ++in) {
                int sortIdx = order[in];
                delta = values[sortIdx] - value;
                value = values[sortIdx];

                if (delta > 0L) {
                    for (int i = 0; i < properties.length; i++) {
                        outProperties[i][out] = properties[i][sortIdx];
                    }
                    outValues[out++] = value;
                } else {
                    for (int i = 0; i < properties.length; i++) {
                        Aggregation aggregation = aggregations[i];
                        int existingIdx = out - 1;
                        long[] outProperty = outProperties[i];
                        double existingProperty = Double.longBitsToDouble(outProperty[existingIdx]);
                        double newProperty = Double.longBitsToDouble(properties[i][sortIdx]);
                        newProperty = aggregation.merge(existingProperty, newProperty);
                        outProperty[existingIdx] = Double.doubleToLongBits(newProperty);
                    }
                }
            }
        }

        System.arraycopy(outValues, 0, values, 0, out);
        for (int i = 0; i < outProperties.length; i++) {
            System.arraycopy(outProperties[i], 0, properties[i], 0, out);
        }

        return out;

    }

    private int withProperties(
        long nodeId,
        byte[] targets,
        long[][] uncompressedProperties,
        int numberOfCompressedTargets,
        int compressedBytesSize,
        LongArrayBuffer buffer,
        ValueMapper mapper
    ) {
        AdjacencyCompression.copyFrom(buffer, targets, numberOfCompressedTargets, compressedBytesSize, mapper);

        int degree = aggregateWithProperties(buffer, uncompressedProperties, aggregations);

        long address = copy(buffer.buffer, degree, adjacencyAllocator);

        copyProperties(uncompressedProperties, degree, nodeId, propertyOffsets);

        this.adjacencyDegrees.set(nodeId, degree);
        this.adjacencyOffsets.set(nodeId, address);

        return degree;
    }

    private void copyProperties(long[][] properties, int degree, long nodeId, HugeLongArray offsets) {
        long address = 0;
        for (int i = 0; i < properties.length; i++) {
            long[] property = properties[i];
            var propertiesAllocator = propertiesAllocators[i];
            // the address should be the same for every property, because we do not compress and thus the address is
            // bound by the degree.
            address = copy(property, degree, propertiesAllocator);
        }
        offsets.set(nodeId, address);
    }

    private long copy(long[] data, int degree, AdjacencyListBuilder.Allocator<long[]> allocator) {
        return allocator.write(data, degree);
    }

    private static final class Factory extends AbstractAdjacencyCompressorFactory<long[], long[]> {

        Factory(
            LongSupplier nodeCountSupplier,
            AdjacencyListBuilder<long[], ? extends AdjacencyList> adjacencyBuilder,
            AdjacencyListBuilder<long[], ? extends AdjacencyProperties>[] propertyBuilders,
            boolean noAggregation,
            Aggregation[] aggregations
        ) {
            super(
                nodeCountSupplier,
                adjacencyBuilder,
                propertyBuilders,
                noAggregation,
                aggregations
            );
        }

        @Override
        @SuppressWarnings("unchecked")
        public RawCompressor createCompressor() {
            return new RawCompressor(
                adjacencyBuilder.newAllocator(),
                Arrays
                    .stream(propertyBuilders)
                    .map(AdjacencyListBuilder::newAllocator)
                    .toArray(AdjacencyListBuilder.Allocator[]::new),
                adjacencyDegrees,
                adjacencyOffsets,
                propertyOffsets,
                noAggregation,
                aggregations
            );
        }
    }
}
