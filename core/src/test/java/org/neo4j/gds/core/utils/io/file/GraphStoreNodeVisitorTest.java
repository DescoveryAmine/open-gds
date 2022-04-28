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
package org.neo4j.gds.core.utils.io.file;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.api.UnionNodeProperties;
import org.neo4j.gds.api.schema.NodeSchema;
import org.neo4j.gds.core.huge.HugeGraph;
import org.neo4j.gds.core.loading.construction.GraphFactory;
import org.neo4j.gds.core.loading.construction.NodesBuilder;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.neo4j.gds.TestSupport.assertGraphEquals;

@GdlExtension
class GraphStoreNodeVisitorTest {

    public static Map<String, NodeProperties> unionNodePropertiesOrThrow(NodesBuilder.IdMapAndProperties idMapAndProperties) {
        Optional<Map<String, NodeProperties>> result;
        var nodeProperties = idMapAndProperties.nodeProperties();
        var idMap = idMapAndProperties.idMap();
        if (nodeProperties.isEmpty()) {
            result = Optional.empty();
        } else {
            Map<String, Map<NodeLabel, NodeProperties>> nodePropertiesByKeyAndLabel = new HashMap<>();
            nodeProperties.get().forEach((nodeLabel, propertiesByKey) -> {
                propertiesByKey.forEach((propertyKey, propertyValues) -> {
                    nodePropertiesByKeyAndLabel
                        .computeIfAbsent(propertyKey, __ -> new HashMap<>())
                        .put(nodeLabel, propertyValues);
                });
            });
            Map<String, NodeProperties> unionNodeProperties = nodePropertiesByKeyAndLabel
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> new UnionNodeProperties(idMap, entry.getValue())
                ));
            result = Optional.of(unionNodeProperties);
        }

        return result.orElseThrow(() -> new IllegalArgumentException("Expected node properties to be present"));
    }

    @GdlGraph
    static String DB_CYPHER = "CREATE" +
                              "  (a:A {prop1: 42L, prop2: [1.0, 2.0]})" +
                              ", (b:A {prop1: 43L, prop2: [3.0, 4.0]})" +
                              ", (c:B {prop3: 13.37D})";

    @Inject
    GraphStore graphStore;

    @Inject
    Graph graph;

    @Test
    void shouldAddNodesToNodesBuilder() {
        NodeSchema nodeSchema = graphStore.schema().nodeSchema();
        NodesBuilder nodesBuilder = GraphFactory.initNodesBuilder(nodeSchema)
            .concurrency(1)
            .maxOriginalId(graphStore.nodeCount())
            .nodeCount(graph.nodeCount())
            .build();
        GraphStoreNodeVisitor nodeVisitor = new GraphStoreNodeVisitor(nodeSchema, nodesBuilder);
        graph.forEachNode(nodeId -> {
            nodeVisitor.id(nodeId);
            Set<NodeLabel> nodeLabels = graph.nodeLabels(nodeId);
            nodeVisitor.labels(nodeLabels.stream().map(NodeLabel::name).toArray(String[]::new));
            var propertyKeys = graphStore.nodePropertyKeys(nodeLabels);
            for (String propertyKey : propertyKeys) {
                nodeVisitor.property(propertyKey, graph.nodeProperties(propertyKey).value(nodeId).asObject());
            }
            nodeVisitor.endOfEntity();
            return true;
        });

        var idMapAndProperties = nodesBuilder.build();
        var idMap = idMapAndProperties.idMap();
        var nodeProperties = unionNodePropertiesOrThrow(idMapAndProperties);
        var relationships = GraphFactory.emptyRelationships(idMap);
        HugeGraph actualGraph = GraphFactory.create(
            idMap,
            nodeSchema,
            nodeProperties,
            RelationshipType.ALL_RELATIONSHIPS,
            relationships
        );
        assertGraphEquals(graph, actualGraph);
    }
}
