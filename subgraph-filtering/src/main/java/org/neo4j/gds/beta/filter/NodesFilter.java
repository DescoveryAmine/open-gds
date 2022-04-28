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
package org.neo4j.gds.beta.filter;

import com.carrotsearch.hppc.AbstractIterator;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.api.DefaultValue;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.api.NodeProperty;
import org.neo4j.gds.api.NodePropertyStore;
import org.neo4j.gds.beta.filter.expression.EvaluationContext;
import org.neo4j.gds.beta.filter.expression.Expression;
import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.loading.construction.GraphFactory;
import org.neo4j.gds.core.loading.construction.NodesBuilder;
import org.neo4j.gds.core.loading.nodeproperties.DoubleArrayNodePropertiesBuilder;
import org.neo4j.gds.core.loading.nodeproperties.DoubleNodePropertiesBuilder;
import org.neo4j.gds.core.loading.nodeproperties.FloatArrayNodePropertiesBuilder;
import org.neo4j.gds.core.loading.nodeproperties.InnerNodePropertiesBuilder;
import org.neo4j.gds.core.loading.nodeproperties.LongArrayNodePropertiesBuilder;
import org.neo4j.gds.core.loading.nodeproperties.LongNodePropertiesBuilder;
import org.neo4j.gds.core.utils.partition.Partition;
import org.neo4j.gds.core.utils.partition.PartitionUtils;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

final class NodesFilter {

    @ValueClass
    interface FilteredNodes {
        IdMap idMap();

        Map<NodeLabel, NodePropertyStore> propertyStores();
    }

    static FilteredNodes filterNodes(
        GraphStore graphStore,
        Expression expression,
        int concurrency,
        ExecutorService executorService,
        ProgressTracker progressTracker
    ) {
        var inputNodes = graphStore.nodes();

        var nodesBuilder = GraphFactory.initNodesBuilder()
            .concurrency(concurrency)
            .maxOriginalId(inputNodes.highestNeoId())
            .hasLabelInformation(!graphStore.nodeLabels().isEmpty())
            .build();

        var partitions = PartitionUtils
            .rangePartition(concurrency, graphStore.nodeCount(), Function.identity(), Optional.empty())
            .iterator();

        var tasks = NodeFilterTask.of(
            graphStore,
            expression,
            partitions,
            nodesBuilder,
            progressTracker
        );

        progressTracker.beginSubTask();
        ParallelUtil.runWithConcurrency(concurrency, tasks, executorService);
        progressTracker.endSubTask();

        var idMapAndProperties = nodesBuilder.build();
        var filteredIdMap = idMapAndProperties.idMap();

        progressTracker.beginSubTask();
        var filteredNodePropertyStores = filterNodeProperties(
            filteredIdMap,
            graphStore,
            concurrency,
            progressTracker
        );
        progressTracker.endSubTask();

        return ImmutableFilteredNodes.builder()
            .idMap(filteredIdMap)
            .propertyStores(filteredNodePropertyStores)
            .build();
    }

    private static Map<NodeLabel, NodePropertyStore> filterNodeProperties(
        IdMap filteredIdMap,
        GraphStore inputGraphStore,
        int concurrency,
        ProgressTracker progressTracker
    ) {
        return filteredIdMap
            .availableNodeLabels()
            .stream()
            .collect(Collectors.toMap(
                Function.identity(),
                nodeLabel -> {
                    var propertyKeys = inputGraphStore.nodePropertyKeys(nodeLabel);

                    return createNodePropertyStore(
                        inputGraphStore,
                        filteredIdMap,
                        nodeLabel,
                        propertyKeys,
                        concurrency,
                        progressTracker
                    );
                }
            ));
    }

    private static NodePropertyStore createNodePropertyStore(
        GraphStore inputGraphStore,
        IdMap filteredIdMap,
        NodeLabel nodeLabel,
        Collection<String> propertyKeys,
        int concurrency,
        ProgressTracker progressTracker
    ) {
        progressTracker.beginSubTask(filteredIdMap.nodeCount() * propertyKeys.size());

        var builder = NodePropertyStore.builder();
        var filteredNodeCount = filteredIdMap.nodeCount();
        var inputMapping = inputGraphStore.nodes();

        propertyKeys.forEach(propertyKey -> {
            var nodeProperties = inputGraphStore.nodePropertyValues(nodeLabel, propertyKey);
            var propertyState = inputGraphStore.nodePropertyState(propertyKey);

            NodePropertiesBuilder<?> nodePropertiesBuilder = getPropertiesBuilder(
                inputMapping,
                nodeProperties,
                concurrency
            );

            ParallelUtil.parallelForEachNode(
                filteredNodeCount,
                concurrency,
                filteredNode -> {
                    var inputNode = inputMapping.toMappedNodeId(filteredIdMap.toOriginalNodeId(filteredNode));
                    nodePropertiesBuilder.accept(inputNode, filteredNode);
                    progressTracker.logProgress();
                }
            );

            builder.putNodeProperty(
                propertyKey,
                NodeProperty.of(propertyKey, propertyState, nodePropertiesBuilder.build(filteredNodeCount, filteredIdMap))
            );
        });
        progressTracker.endSubTask();
        return builder.build();
    }

    private static NodePropertiesBuilder<?> getPropertiesBuilder(
        IdMap idMap,
        NodeProperties inputNodeProperties,
        int concurrency
    ) {
        NodePropertiesBuilder<?> propertiesBuilder = null;

        switch (inputNodeProperties.valueType()) {
            case LONG:
                var longNodePropertiesBuilder = LongNodePropertiesBuilder.of(
                    DefaultValue.forLong(),
                    concurrency
                );
                propertiesBuilder = new NodePropertiesBuilder<>(inputNodeProperties, longNodePropertiesBuilder) {
                    @Override
                    void accept(long inputNode, long filteredNode) {

                        propertyBuilder.set(idMap.toOriginalNodeId(inputNode), inputProperties.longValue(inputNode));
                    }
                };
                break;

            case DOUBLE:
                var doubleNodePropertiesBuilder = new DoubleNodePropertiesBuilder(
                    DefaultValue.forDouble(),
                    concurrency
                );
                propertiesBuilder = new NodePropertiesBuilder<>(inputNodeProperties, doubleNodePropertiesBuilder) {
                    @Override
                    void accept(long inputNode, long filteredNode) {
                        propertyBuilder.set(idMap.toOriginalNodeId(inputNode), inputProperties.doubleValue(inputNode));
                    }
                };
                break;

            case DOUBLE_ARRAY:
                var doubleArrayNodePropertiesBuilder = new DoubleArrayNodePropertiesBuilder(
                    DefaultValue.forDoubleArray(),
                    concurrency
                );
                propertiesBuilder = new NodePropertiesBuilder<>(inputNodeProperties, doubleArrayNodePropertiesBuilder) {
                    @Override
                    void accept(long inputNode, long filteredNode) {
                        propertyBuilder.set(idMap.toOriginalNodeId(inputNode), inputProperties.doubleArrayValue(inputNode));
                    }
                };
                break;

            case FLOAT_ARRAY:
                var floatArrayNodePropertiesBuilder = new FloatArrayNodePropertiesBuilder(
                    DefaultValue.forFloatArray(),
                    concurrency
                );

                propertiesBuilder = new NodePropertiesBuilder<>(inputNodeProperties, floatArrayNodePropertiesBuilder) {
                    @Override
                    void accept(long inputNode, long filteredNode) {
                        propertyBuilder.set(idMap.toOriginalNodeId(inputNode), inputProperties.floatArrayValue(inputNode));
                    }
                };
                break;

            case LONG_ARRAY:
                var longArrayNodePropertiesBuilder = new LongArrayNodePropertiesBuilder(
                    DefaultValue.forFloatArray(),
                    concurrency
                );

                propertiesBuilder = new NodePropertiesBuilder<>(inputNodeProperties, longArrayNodePropertiesBuilder) {
                    @Override
                    void accept(long inputNode, long filteredNode) {
                        propertyBuilder.set(idMap.toOriginalNodeId(inputNode), inputProperties.longArrayValue(inputNode));
                    }
                };
                break;

            case UNKNOWN:
                throw new UnsupportedOperationException("Cannot import properties of type UNKNOWN");
        }
        return propertiesBuilder;
    }

    private NodesFilter() {}

    private static final class NodeFilterTask implements Runnable {
        private final Partition partition;
        private final Expression expression;
        private final EvaluationContext.NodeEvaluationContext nodeContext;
        private final ProgressTracker progressTracker;
        private final GraphStore graphStore;
        private final NodesBuilder nodesBuilder;

        static Iterator<NodeFilterTask> of(
            GraphStore graphStore,
            Expression expression,
            Iterator<Partition> partitions,
            NodesBuilder nodesBuilder,
            ProgressTracker progressTracker
        ) {
            return new AbstractIterator<>() {
                @Override
                protected NodeFilterTask fetch() {
                    if (!partitions.hasNext()) {
                        return done();
                    }

                    return new NodeFilterTask(
                        partitions.next(),
                        expression,
                        graphStore,
                        nodesBuilder,
                        progressTracker
                    );
                }
            };
        }

        private NodeFilterTask(
            Partition partition,
            Expression expression,
            GraphStore graphStore,
            NodesBuilder nodesBuilder,
            ProgressTracker progressTracker
        ) {
            this.partition = partition;
            this.expression = expression;
            this.graphStore = graphStore;
            this.nodesBuilder = nodesBuilder;
            this.nodeContext = new EvaluationContext.NodeEvaluationContext(graphStore);
            this.progressTracker = progressTracker;
        }

        @Override
        public void run() {
            var idMap = graphStore.nodes();
            partition.consume(node -> {
                nodeContext.init(node);
                if (expression.evaluate(nodeContext) == Expression.TRUE) {
                    var originalId = idMap.toOriginalNodeId(node);
                    NodeLabel[] labels = idMap.nodeLabels(node).toArray(NodeLabel[]::new);
                    nodesBuilder.addNode(originalId, labels);
                }
                progressTracker.logProgress();
            });
        }
    }

    private abstract static class NodePropertiesBuilder<T extends InnerNodePropertiesBuilder> {
        final NodeProperties inputProperties;
        final T propertyBuilder;

        NodePropertiesBuilder(NodeProperties inputProperties, T propertyBuilder) {
            this.inputProperties = inputProperties;
            this.propertyBuilder = propertyBuilder;
        }

        abstract void accept(long inputNode, long filteredNode);

        NodeProperties build(long size, IdMap idMap) {
            return propertyBuilder.build(size, idMap);
        }
    }
}
