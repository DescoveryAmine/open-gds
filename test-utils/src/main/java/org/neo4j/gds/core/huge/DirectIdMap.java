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
package org.neo4j.gds.core.huge;

import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.core.utils.LazyBatchCollection;
import org.neo4j.gds.core.utils.collection.primitive.PrimitiveLongCollections;
import org.neo4j.gds.core.utils.collection.primitive.PrimitiveLongIterable;
import org.neo4j.gds.core.utils.collection.primitive.PrimitiveLongIterator;

import java.util.Collection;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.LongPredicate;

public final class DirectIdMap implements IdMap {
    private final long nodeCount;

    public DirectIdMap(long nodeCount) {
        this.nodeCount = nodeCount;
    }

    @Override
    public long toMappedNodeId(long nodeId) {
        return nodeId;
    }

    @Override
    public long toOriginalNodeId(long nodeId) {
        return nodeId;
    }

    @Override
    public long toRootNodeId(long nodeId) {
        return nodeId;
    }

    @Override
    public long highestNeoId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(final long nodeId) {
        return nodeId < nodeCount;
    }

    @Override
    public long nodeCount() {
        return nodeCount;
    }

    @Override
    public OptionalLong rootNodeCount() {
        return OptionalLong.of(nodeCount);
    }

    @Override
    public Collection<PrimitiveLongIterable> batchIterables(long batchSize) {
        return LazyBatchCollection.of(
            nodeCount(),
            batchSize,
            IdIterable::new
        );
    }

    @Override
    public Set<NodeLabel> nodeLabels(long nodeId) {
        return Set.of();
    }

    @Override
    public void forEachNodeLabel(long nodeId, NodeLabelConsumer consumer) {

    }

    @Override
    public Set<NodeLabel> availableNodeLabels() {
        return Set.of();
    }

    @Override
    public boolean hasLabel(long nodeId, NodeLabel label) {
        return false;
    }

    @Override
    public IdMap rootIdMap() {
        return this;
    }

    @Override
    public void forEachNode(LongPredicate consumer) {
        for (long i = 0; i < nodeCount; i++) {
            var shouldContinue = consumer.test(i);
            if (!shouldContinue) break;
        }
    }

    @Override
    public PrimitiveLongIterator nodeIterator() {
        return PrimitiveLongCollections.range(0, nodeCount);
    }
}
