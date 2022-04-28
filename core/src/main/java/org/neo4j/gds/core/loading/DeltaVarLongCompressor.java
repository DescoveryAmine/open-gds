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

import org.neo4j.gds.PropertyMappings;
import org.neo4j.gds.api.AdjacencyList;
import org.neo4j.gds.api.AdjacencyProperties;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.compress.AdjacencyCompressor;
import org.neo4j.gds.core.compress.AdjacencyCompressorFactory;
import org.neo4j.gds.core.compress.LongArrayBuffer;
import org.neo4j.gds.core.utils.paged.HugeIntArray;
import org.neo4j.gds.core.utils.paged.HugeLongArray;

import java.util.Arrays;
import java.util.function.LongSupplier;

public final class DeltaVarLongCompressor implements AdjacencyCompressor {

    private final AdjacencyListBuilder.Allocator<byte[]> adjacencyAllocator;
    private final AdjacencyListBuilder.Allocator<long[]>[] propertiesAllocators;
    private final HugeIntArray adjacencyDegrees;
    private final HugeLongArray adjacencyOffsets;
    private final HugeLongArray propertyOffsets;
    private final boolean noAggregation;
    private final Aggregation[] aggregations;

    public static AdjacencyCompressorFactory factory(
        LongSupplier nodeCountSupplier,
        AdjacencyListBuilderFactory<byte[], ? extends AdjacencyList, long[], ? extends AdjacencyProperties> adjacencyListBuilderFactory,
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

    private DeltaVarLongCompressor(
        AdjacencyListBuilder.Allocator<byte[]> adjacencyAllocator,
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
            return applyVariableDeltaEncodingWithProperties(nodeId, targets, properties, numberOfCompressedTargets, compressedBytesSize, buffer, mapper);
        } else {
            return applyVariableDeltaEncodingWithoutProperties(nodeId, targets, numberOfCompressedTargets, compressedBytesSize, buffer, mapper);
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

    private int applyVariableDeltaEncodingWithoutProperties(
        long nodeId,
        byte[] semiCompressedBytesDuringLoading,
        int numberOfCompressedTargets,
        int compressedByteSize,
        LongArrayBuffer buffer,
        ValueMapper mapper
    ) {
        AdjacencyCompression.copyFrom(
            buffer,
            semiCompressedBytesDuringLoading,
            numberOfCompressedTargets,
            compressedByteSize,
            mapper
        );
        int degree = AdjacencyCompression.applyDeltaEncoding(buffer, aggregations[0]);

        // since we might have to map to larger ids
        // we can no longer guarantee that we fit into the buffer
        if (mapper != ZigZagLongDecoding.Identity.INSTANCE) {
            semiCompressedBytesDuringLoading = AdjacencyCompression.ensureBufferSize(
                buffer,
                semiCompressedBytesDuringLoading
            );
        }

        int requiredBytes = AdjacencyCompression.compress(buffer, semiCompressedBytesDuringLoading);

        long address = copyIds(semiCompressedBytesDuringLoading, requiredBytes);

        this.adjacencyDegrees.set(nodeId, degree);
        this.adjacencyOffsets.set(nodeId, address);

        return degree;
    }

    private int applyVariableDeltaEncodingWithProperties(
        long nodeId,
        byte[] semiCompressedBytesDuringLoading,
        long[][] uncompressedPropertiesPerProperty,
        int numberOfCompressedTargets,
        int compressedByteSize,
        LongArrayBuffer buffer,
        ValueMapper mapper
    ) {
        // decompress semiCompressed into full uncompressed long[] (in buffer)
        // ordered by whatever order they've been read
        AdjacencyCompression.copyFrom(buffer, semiCompressedBytesDuringLoading, numberOfCompressedTargets, compressedByteSize, mapper);
        // buffer contains uncompressed, unsorted target list

        int degree = AdjacencyCompression.applyDeltaEncoding(
            buffer,
            uncompressedPropertiesPerProperty,
            aggregations,
            noAggregation
        );
        // targets are sorted and delta encoded
        // buffer contains sorted target list
        // values are delta encoded except for the first one
        // values are still uncompressed

        // since we might have to map to larger ids
        // we can no longer guarantee that we fit into the buffer
        if (mapper != ZigZagLongDecoding.Identity.INSTANCE) {
            semiCompressedBytesDuringLoading = AdjacencyCompression.ensureBufferSize(
                buffer,
                semiCompressedBytesDuringLoading
            );
        }

        int requiredBytes = AdjacencyCompression.compress(buffer, semiCompressedBytesDuringLoading);
        // values are now vlong encoded in the array storage (semiCompressed)

        var address = copyIds(semiCompressedBytesDuringLoading, requiredBytes);
        // values are in the final adjacency list

        copyProperties(uncompressedPropertiesPerProperty, degree, nodeId, propertyOffsets);

        this.adjacencyDegrees.set(nodeId, degree);
        this.adjacencyOffsets.set(nodeId, address);

        return degree;
    }

    private long copyIds(byte[] targets, int requiredBytes) {
        return adjacencyAllocator.write(targets, requiredBytes);
    }

    private void copyProperties(long[][] properties, int degree, long nodeId, HugeLongArray offsets) {
        long address = 0;
        for (int i = 0; i < properties.length; i++) {
            long[] property = properties[i];
            var propertiesAllocator = propertiesAllocators[i];
            // the address should be the same for every property, because we do not compress and thus the address is
            // bound by the degree.
            address = propertiesAllocator.write(property, degree);
        }
        offsets.set(nodeId, address);
    }

    private static final class Factory extends AbstractAdjacencyCompressorFactory<byte[], long[]> {

        Factory(
            LongSupplier nodeCountSupplier,
            AdjacencyListBuilder<byte[], ? extends AdjacencyList> adjacencyBuilder,
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
        public DeltaVarLongCompressor createCompressor() {
            return new DeltaVarLongCompressor(
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
