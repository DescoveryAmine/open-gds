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
package org.neo4j.gds.impl.walking;

import org.neo4j.gds.Algorithm;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.RelationshipIterator;
import org.neo4j.gds.api.Relationships;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.huge.HugeGraph;
import org.neo4j.gds.core.loading.construction.GraphFactory;
import org.neo4j.gds.core.loading.construction.RelationshipsBuilder;
import org.neo4j.gds.core.utils.paged.HugeLongArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.msbfs.ANPStrategy;
import org.neo4j.gds.msbfs.BfsConsumer;
import org.neo4j.gds.msbfs.BfsSources;
import org.neo4j.gds.msbfs.MultiSourceBFS;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

public class CollapsePath extends Algorithm<Relationships> {

    private final Graph[] graphs;
    private final long nodeCount;
    private final CollapsePathConfig config;
    private final ExecutorService executorService;

    public CollapsePath(
        Graph[] graphs,
        CollapsePathConfig config,
        ExecutorService executorService
    ) {
        super(ProgressTracker.NULL_TRACKER);
        this.graphs = graphs;
        this.nodeCount = graphs[0].nodeCount();
        this.config = config;
        this.executorService = executorService;
    }

    @Override
    public Relationships compute() {
        RelationshipsBuilder relImporter = GraphFactory.initRelationshipsBuilder()
            .nodes(((HugeGraph) graphs[0]).idMap())
            .orientation(Orientation.NATURAL)
            .aggregation(Aggregation.NONE)
            .concurrency(config.concurrency())
            .executorService(executorService)
            .build();

        var traversalConsumer = config.allowSelfLoops()
            ? new TraversalConsumer(relImporter, graphs.length)
            : new NoLoopTraversalConsumer(relImporter, graphs.length);

        AtomicLong batchOffset = new AtomicLong(0);

        var tasks = ParallelUtil.tasks(config.concurrency(), () -> () -> {
            var currentOffset = 0L;
            long[] startNodes = new long[64];
            var localGraphs = new Graph[graphs.length];

            for (int i = 0; i < graphs.length; i++) {
                localGraphs[i] = graphs[i].concurrentCopy();
            }

            while ((currentOffset = batchOffset.getAndAdd(64)) < nodeCount) {
                if (currentOffset + 64 >= nodeCount) {
                    startNodes = new long[(int) (nodeCount - currentOffset)];
                }
                for (long j = currentOffset; j < Math.min(currentOffset + 64, nodeCount); j++) {
                    startNodes[(int) (j - currentOffset)] = j;
                }

                var multiSourceBFS = TraversalToEdgeMSBFSStrategy.initializeMultiSourceBFS(
                    localGraphs,
                    traversalConsumer,
                    config.allowSelfLoops(),
                    startNodes
                );

                multiSourceBFS.run();
            }

        });

        ParallelUtil.runWithConcurrency(config.concurrency(), tasks, Pools.DEFAULT);

        return relImporter.build();
    }

    @Override
    public void release() {

    }

    private static class TraversalToEdgeMSBFSStrategy extends ANPStrategy {

        static MultiSourceBFS initializeMultiSourceBFS(
            Graph[] graphs,
            BfsConsumer perNodeAction,
            boolean allowSelfLoops,
            long[] startNodes
        ) {
            return new MultiSourceBFS(
                graphs[0].nodeCount(),
                graphs[0],
                new TraversalToEdgeMSBFSStrategy(graphs, perNodeAction),
                false,
                allowSelfLoops,
                startNodes
            );
        }

        private final Graph[] graphs;

        TraversalToEdgeMSBFSStrategy(Graph[] graphs, BfsConsumer perNodeAction) {
            super(perNodeAction);
            this.graphs = graphs;
        }

        @Override
        protected boolean stopTraversal(boolean hasNext, int depth) {
            return !hasNext || depth >= graphs.length;
        }

        @Override
        protected void prepareNextVisit(
            RelationshipIterator relationships,
            long nodeVisit,
            long nodeId,
            HugeLongArray nextSet,
            int depth
        ) {
            graphs[depth].forEachRelationship(
                nodeId,
                (src, tgt) -> {
                    nextSet.or(tgt, nodeVisit);
                    return true;
                }
            );
        }
    }

    private static class TraversalConsumer implements BfsConsumer {
        final int targetDepth;
        final RelationshipsBuilder relImporter;

        private TraversalConsumer(RelationshipsBuilder relImporter, int targetDepth) {
            this.relImporter = relImporter;
            this.targetDepth = targetDepth;
        }

        @Override
        public void accept(long targetNode, int depth, BfsSources sourceNode) {
            if (depth == targetDepth) {
                while (sourceNode.hasNext()) {
                    relImporter.addFromInternal(sourceNode.next(), targetNode);
                }
            }
        }
    }

    private static final class NoLoopTraversalConsumer extends TraversalConsumer {

        private NoLoopTraversalConsumer(RelationshipsBuilder relImporter, int targetDepth) {
            super(relImporter, targetDepth);
        }

        @Override
        public void accept(long targetNode, int depth, BfsSources sourceNode) {
            if (depth == targetDepth) {
                while (sourceNode.hasNext()) {
                    var sourceNodeId = sourceNode.next();
                    if (sourceNodeId != targetNode) {
                        relImporter.addFromInternal(sourceNodeId, targetNode);
                    }
                }
            }
        }
    }
}
