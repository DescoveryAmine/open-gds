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
package org.neo4j.gds.catalog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.gds.BaseProcTest;
import org.neo4j.gds.GdsCypher;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.PropertyMapping;
import org.neo4j.gds.RelationshipProjection;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.DefaultValue;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.loading.GraphStoreCatalog;
import org.neo4j.gds.core.loading.construction.GraphFactory;
import org.neo4j.gds.core.loading.construction.RelationshipsBuilder;
import org.neo4j.gds.functions.AsNodeFunc;
import org.neo4j.values.storable.NumberType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.neo4j.gds.compat.MapUtil.map;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

class GraphStreamRelationshipPropertiesProcTest extends BaseProcTest {

    private static final String TEST_GRAPH_SAME_PROPERTIES = "testGraph";
    private static final String TEST_GRAPH_DIFFERENT_PROPERTIES = "testGraph2";

    private static final String DB_CYPHER =
        "CREATE" +
        "  (a:Label { id: 0 })" +
        ", (b:Label { id: 1 })" +
        ", (a)-[:REL1 { relProp1: 0.0, relProp2: 42.0}]->(a)" +
        ", (b)-[:REL1 { relProp1: 1.0, relProp2: 43.0}]->(b)" +
        ", (a)-[:REL2 { relProp1: 2.0, relProp2: 44.0}]->(a)" +
        ", (b)-[:REL2 { relProp1: 3.0, relProp2: 45.0}]->(b)";

    @BeforeEach
    void setup() throws Exception {
        registerProcedures(GraphProjectProc.class, GraphStreamRelationshipPropertiesProc.class);
        registerFunctions(AsNodeFunc.class);
        runQuery(DB_CYPHER);

        runQuery(GdsCypher.call(TEST_GRAPH_SAME_PROPERTIES)
            .graphProject()
            .withAnyLabel()
            .withRelationshipType("REL1")
            .withRelationshipType("REL2")
            .withRelationshipProperty("relProp1")
            .withRelationshipProperty("relProp2")
            .yields()
        );

        runQuery(GdsCypher.call(TEST_GRAPH_DIFFERENT_PROPERTIES)
            .graphProject()
            .withAnyLabel()
            .withRelationshipType("REL1", RelationshipProjection.builder()
                .type("REL1")
                .addProperties(
                    PropertyMapping.of("newRelProp1", "relProp1", 1337),
                    PropertyMapping.of("newRelProp2", "relProp2", 1337)
                )
                .build()
            )
            .withRelationshipType("REL2", RelationshipProjection.builder()
                .type("REL2")
                .addProperties(PropertyMapping.of("newRelProp1", "relProp1", 1337))
                .build()
            )
            .yields());
    }

    @AfterEach
    void tearDown() {
        GraphStoreCatalog.removeAllLoadedGraphs();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // no labels -> defaults to PROJECT_ALL
        "CALL gds.graph.streamRelationshipProperties(" +
        "   '%s', " +
        "   ['relProp1', 'relProp2']" +
        ") YIELD sourceNodeId, targetNodeId, relationshipType, relationshipProperty, propertyValue " +
        "RETURN gds.util.asNode(sourceNodeId).id AS source, gds.util.asNode(targetNodeId).id AS target, relationshipType, relationshipProperty, propertyValue",
        // explicit PROJECT_ALL
        "CALL gds.graph.streamRelationshipProperties(" +
        "   '%s', " +
        "   ['relProp1', 'relProp2'], " +
        "   ['*']" +
        ") YIELD sourceNodeId, targetNodeId, relationshipType, relationshipProperty, propertyValue " +
        "RETURN gds.util.asNode(sourceNodeId).id AS source, gds.util.asNode(targetNodeId).id AS target, relationshipType, relationshipProperty, propertyValue"
    })
    void streamLoadedRelationshipProperties(String graphStreamQueryTemplate) {
        String graphStreamQuery = formatWithLocale(graphStreamQueryTemplate, TEST_GRAPH_SAME_PROPERTIES);

        assertCypherResult(graphStreamQuery, List.of(
            map("source", 0L, "target", 0L, "relationshipType", "REL1", "relationshipProperty", "relProp1", "propertyValue", 0D),
            map("source", 0L, "target", 0L, "relationshipType", "REL1", "relationshipProperty", "relProp2", "propertyValue", 42D),
            map("source", 0L, "target", 0L, "relationshipType", "REL2", "relationshipProperty", "relProp1", "propertyValue", 2D),
            map("source", 0L, "target", 0L, "relationshipType", "REL2", "relationshipProperty", "relProp2", "propertyValue", 44D),

            map("source", 1L, "target", 1L, "relationshipType", "REL1", "relationshipProperty", "relProp1", "propertyValue", 1D),
            map("source", 1L, "target", 1L, "relationshipType", "REL1", "relationshipProperty", "relProp2", "propertyValue", 43D),
            map("source", 1L, "target", 1L, "relationshipType", "REL2", "relationshipProperty", "relProp1", "propertyValue", 3D),
            map("source", 1L, "target", 1L, "relationshipType", "REL2", "relationshipProperty", "relProp2", "propertyValue", 45D)
        ));
    }

    @Test
    void streamLoadedRelationshipPropertiesForType() {
        String graphStreamQuery = formatWithLocale(
            "CALL gds.graph.streamRelationshipProperties(" +
            "   '%s', " +
            "   ['relProp1', 'relProp2'], " +
            "   ['REL1']" +
            ") YIELD sourceNodeId, targetNodeId, relationshipType, relationshipProperty, propertyValue " +
            "RETURN gds.util.asNode(sourceNodeId).id AS source, gds.util.asNode(targetNodeId).id AS target, relationshipType, relationshipProperty, propertyValue",
            TEST_GRAPH_SAME_PROPERTIES
        );

        assertCypherResult(graphStreamQuery, List.of(
            map("source", 0L, "target", 0L, "relationshipType", "REL1", "relationshipProperty", "relProp1", "propertyValue", 0D),
            map("source", 0L, "target", 0L, "relationshipType", "REL1", "relationshipProperty", "relProp2", "propertyValue", 42D),
            map("source", 1L, "target", 1L, "relationshipType", "REL1", "relationshipProperty", "relProp1", "propertyValue", 1D),
            map("source", 1L, "target", 1L, "relationshipType", "REL1", "relationshipProperty", "relProp2", "propertyValue", 43D)
        ));
    }

    @Test
    void streamLoadedRelationshipPropertiesForTypeSubset() {
        String graphStreamQuery = formatWithLocale(
            "CALL gds.graph.streamRelationshipProperties(" +
            "   '%s', " +
            "   ['newRelProp1', 'newRelProp2']" +
            ") YIELD sourceNodeId, targetNodeId, relationshipType, relationshipProperty, propertyValue " +
            "RETURN gds.util.asNode(sourceNodeId).id AS source, gds.util.asNode(targetNodeId).id AS target, relationshipType, relationshipProperty, propertyValue",
            TEST_GRAPH_DIFFERENT_PROPERTIES
        );

        assertCypherResult(graphStreamQuery, List.of(
            map("source", 0L, "target", 0L, "relationshipType", "REL1", "relationshipProperty", "newRelProp1", "propertyValue", 0D),
            map("source", 0L, "target", 0L, "relationshipType", "REL1", "relationshipProperty", "newRelProp2", "propertyValue", 42D),
            map("source", 0L, "target", 0L, "relationshipType", "REL2", "relationshipProperty", "newRelProp1", "propertyValue", 2D),

            map("source", 1L, "target", 1L, "relationshipType", "REL1", "relationshipProperty", "newRelProp1", "propertyValue", 1D),
            map("source", 1L, "target", 1L, "relationshipType", "REL1", "relationshipProperty", "newRelProp2", "propertyValue", 43D),
            map("source", 1L, "target", 1L, "relationshipType", "REL2", "relationshipProperty", "newRelProp1", "propertyValue", 3D)
        ));
    }

    @Test
    void streamMutatedRelationshipProperties() {
        GraphStore graphStore = GraphStoreCatalog.get(getUsername(), db.databaseId(), TEST_GRAPH_SAME_PROPERTIES).graphStore();

        RelationshipsBuilder relImporter = GraphFactory.initRelationshipsBuilder()
            .nodes(graphStore.nodes())
            .orientation(Orientation.NATURAL)
            .addPropertyConfig(Aggregation.NONE, DefaultValue.forDouble())
            .build();

        relImporter.addFromInternal(0, 1, 23D);

        graphStore.addRelationshipType(RelationshipType.of("NEW_REL"), Optional.of("newRelProp3"), Optional.of(NumberType.FLOATING_POINT), relImporter.build());

        String graphStreamQuery = formatWithLocale(
            "CALL gds.graph.streamRelationshipProperties(" +
            "   '%s', " +
            "   ['newRelProp3']" +
            ") YIELD sourceNodeId, targetNodeId, relationshipType, relationshipProperty, propertyValue " +
            "RETURN gds.util.asNode(sourceNodeId).id AS source, gds.util.asNode(targetNodeId).id AS target, relationshipType, relationshipProperty, propertyValue",
            TEST_GRAPH_SAME_PROPERTIES
        );

        assertCypherResult(graphStreamQuery, List.of(
            map("source", 0L, "target", 1L, "relationshipType", "NEW_REL", "relationshipProperty", "newRelProp3", "propertyValue", 23D)
        ));
    }

    @Test
    void shouldFailOnNonExistingRelationshipProperties() {
        assertError("CALL gds.graph.streamRelationshipProperties($graph, ['newRelProp1', 'newRelProp2', 'newRelProp3'])",
                Map.of("graph", TEST_GRAPH_SAME_PROPERTIES),
            "Expecting at least one relationship projection to contain property key(s) ['newRelProp1', 'newRelProp2', 'newRelProp3']."
        );
    }

    @Test
    void shouldFailOnNonExistingRelationshipPropertiesForSpecificType() {
        assertError("CALL gds.graph.streamRelationshipProperties($graph, ['relProp1', 'relProp2', 'relProp3'], ['REL1'] )",
            Map.of("graph", TEST_GRAPH_SAME_PROPERTIES),
            "Expecting all specified relationship projections to have all given properties defined. " +
            "Could not find property key(s) ['relProp3'] for label REL1. Defined keys: ['relProp1', 'relProp2']"
        );
    }

    @Test
    void streamLoadedRelationshipPropertyForType() {
        String graphStreamQuery = formatWithLocale(
            "CALL gds.graph.streamRelationshipProperty(" +
            "   '%s', " +
            "   'relProp1', " +
            "   ['REL1']" +
            ") YIELD sourceNodeId, targetNodeId, relationshipType, propertyValue " +
            "RETURN gds.util.asNode(sourceNodeId).id AS source, gds.util.asNode(targetNodeId).id AS target, relationshipType, propertyValue",
            TEST_GRAPH_SAME_PROPERTIES
        );

        assertCypherResult(graphStreamQuery, List.of(
            map("source", 0L, "target", 0L, "relationshipType", "REL1", "propertyValue", 0D),
            map("source", 1L, "target", 1L, "relationshipType", "REL1", "propertyValue", 1D)
        ));
    }

    @Test
    void streamLoadedRelationshipPropertyForTypeSubset() {
        String graphStreamQuery = formatWithLocale(
            "CALL gds.graph.streamRelationshipProperty(" +
            "   '%s', " +
            "   'newRelProp2'" +
            ") YIELD sourceNodeId, targetNodeId, relationshipType, propertyValue " +
            "RETURN gds.util.asNode(sourceNodeId).id AS source, gds.util.asNode(targetNodeId).id AS target, relationshipType, propertyValue",
            TEST_GRAPH_DIFFERENT_PROPERTIES
        );

        assertCypherResult(graphStreamQuery, List.of(
            map("source", 0L, "target", 0L, "relationshipType", "REL1", "propertyValue", 42D),
            map("source", 1L, "target", 1L, "relationshipType", "REL1", "propertyValue", 43D)
        ));
    }

    @Test
    void streamMutatedNodeProperty() {
        GraphStore graphStore = GraphStoreCatalog.get(getUsername(), db.databaseId(), TEST_GRAPH_SAME_PROPERTIES).graphStore();

        RelationshipsBuilder relImporter = GraphFactory.initRelationshipsBuilder()
            .nodes(graphStore.nodes())
            .orientation(Orientation.NATURAL)
            .addPropertyConfig(Aggregation.NONE, DefaultValue.forDouble())
            .build();

        relImporter.addFromInternal(0, 1, 23D);

        graphStore.addRelationshipType(RelationshipType.of("NEW_REL"), Optional.of("newRelProp3"), Optional.of(NumberType.FLOATING_POINT), relImporter.build());

        String graphStreamQuery = formatWithLocale(
            "CALL gds.graph.streamRelationshipProperty(" +
            "   '%s', " +
            "   'newRelProp3'" +
            ") YIELD sourceNodeId, targetNodeId, relationshipType, propertyValue " +
            "RETURN gds.util.asNode(sourceNodeId).id AS source, gds.util.asNode(targetNodeId).id AS target, relationshipType, propertyValue",
            TEST_GRAPH_SAME_PROPERTIES
        );

        assertCypherResult(graphStreamQuery, List.of(
            map("source", 0L, "target", 1L, "relationshipType", "NEW_REL", "propertyValue", 23D)
        ));
    }

    @Test
    void shouldFailOnNonExistingRelationshipProperty() {
        assertError(
            "CALL gds.graph.streamRelationshipProperty($graph, 'relProp3')",
            Map.of("graph", TEST_GRAPH_SAME_PROPERTIES),
            "Expecting at least one relationship projection to contain property key(s) ['relProp3']."
        );
    }

    @Test
    void shouldFailOnNonExistingRelationshipPropertyForSpecificType() {
        assertError(
            "CALL gds.graph.streamRelationshipProperty($graph, 'relProp3', ['REL1'])",
            Map.of("graph", TEST_GRAPH_SAME_PROPERTIES),
            "Expecting all specified relationship projections to have all given properties defined. " +
            "Could not find property key(s) ['relProp3'] for label REL1. Defined keys: ['relProp1', 'relProp2']"
        );
    }

    @Test
    void shouldFailOnDisjunctCombinationsOfRelationshipTypeAndProperty() {
        assertError(
            "CALL gds.graph.streamRelationshipProperties($graph, ['newRelProp1', 'newRelProp2'], ['REL2'])",
            Map.of("graph", TEST_GRAPH_DIFFERENT_PROPERTIES),
            "Expecting all specified relationship projections to have all given properties defined. " +
            "Could not find property key(s) ['newRelProp2'] for label REL2. Defined keys: ['newRelProp1']"
        );
    }
}
