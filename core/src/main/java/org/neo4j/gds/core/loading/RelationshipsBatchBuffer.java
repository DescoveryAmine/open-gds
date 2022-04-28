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

import org.neo4j.gds.api.PartialIdMap;
import org.neo4j.gds.compat.PropertyReference;

import static org.neo4j.gds.utils.ExceptionUtil.validateSourceNodeIsLoaded;
import static org.neo4j.gds.utils.ExceptionUtil.validateTargetNodeIsLoaded;
import static org.neo4j.token.api.TokenConstants.ANY_RELATIONSHIP_TYPE;


public final class RelationshipsBatchBuffer extends RecordsBatchBuffer<RelationshipReference> {

    private final PartialIdMap idMap;
    private final int type;
    private final boolean throwOnUnMappedNodeIds;

    private final long[] relationshipReferences;
    private final PropertyReference[] propertyReferences;

    private final long[] bufferCopy;
    private final long[] relationshipReferencesCopy;
    private final PropertyReference[] propertyReferencesCopy;
    private final int[] histogram;

    public RelationshipsBatchBuffer(
        final PartialIdMap idMap,
        final int type,
        int capacity
    ) {
        this(idMap, type, capacity, true);
    }

    RelationshipsBatchBuffer(
        final PartialIdMap idMap,
        final int type,
        int capacity,
        boolean throwOnUnMappedNodeIds
    ) {
        // For relationships: the buffer is divided into 4-long blocks
        // for each rel: source, target, rel-id, prop-id
        super(Math.multiplyExact(2, capacity));
        this.idMap = idMap;
        this.type = type;
        this.throwOnUnMappedNodeIds = throwOnUnMappedNodeIds;
        this.relationshipReferences = new long[capacity];
        this.propertyReferences = new PropertyReference[capacity];
        bufferCopy = RadixSort.newCopy(buffer);
        relationshipReferencesCopy = RadixSort.newCopy(relationshipReferences);
        propertyReferencesCopy = RadixSort.newCopy(propertyReferences);
        histogram = RadixSort.newHistogram(capacity);
    }

    @Override
    public void offer(final RelationshipReference record) {
        if ((type == ANY_RELATIONSHIP_TYPE) || (type == record.typeTokenId())) {
            long source = idMap.toMappedNodeId(record.sourceNodeReference());
            long target = idMap.toMappedNodeId(record.targetNodeReference());

            if (throwOnUnMappedNodeIds) {
                validateSourceNodeIsLoaded(source, record.sourceNodeReference());
                validateTargetNodeIsLoaded(target, record.targetNodeReference());
            }
            else if (source == -1 || target == -1) {
                return;
            }

            add(source, target, record.relationshipId(), record.propertiesReference());
        }
    }

    public void add(long sourceId, long targetId) {
        int position = this.length;
        long[] buffer = this.buffer;
        buffer[position] = sourceId;
        buffer[1 + position] = targetId;
        this.length = 2 + position;
    }

    public void add(long sourceId, long targetId, long relationshipReference, PropertyReference propertyReference) {
        int position = this.length;
        long[] buffer = this.buffer;
        buffer[position] = sourceId;
        buffer[1 + position] = targetId;
        this.relationshipReferences[position >> 1] = relationshipReference;
        this.propertyReferences[position >> 1] = propertyReference;
        this.length = 2 + position;
    }

    public long[] sortBySource() {
        RadixSort.radixSort(
            buffer,
            bufferCopy,
            relationshipReferences,
            relationshipReferencesCopy,
            propertyReferences,
            propertyReferencesCopy,
            histogram,
            length
        );
        return buffer;
    }

    public long[] sortByTarget() {
        RadixSort.radixSort2(
            buffer,
            bufferCopy,
            relationshipReferences,
            relationshipReferencesCopy,
            propertyReferences,
            propertyReferencesCopy,
            histogram,
            length
        );
        return buffer;
    }

    long[] relationshipReferences() {
        return this.relationshipReferences;
    }

    PropertyReference[] propertyReferences() {
        return this.propertyReferences;
    }

    public long[] spareLongs() {
        return bufferCopy;
    }

    public int[] spareInts() {
        return histogram;
    }
}
