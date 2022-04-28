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
package org.neo4j.gds.similarity.knn;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.AlgoBaseProc;
import org.neo4j.gds.GdsCypher;
import org.neo4j.gds.ImmutablePropertyMapping;
import org.neo4j.gds.MutateRelationshipWithPropertyTest;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.StoreLoaderBuilder;
import org.neo4j.gds.api.DefaultValue;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.loading.GraphStoreCatalog;

import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.gds.TestSupport.assertGraphEquals;
import static org.neo4j.gds.TestSupport.fromGdl;

class KnnMutateProcTest extends KnnProcTest<KnnMutateConfig>
    implements MutateRelationshipWithPropertyTest<Knn, KnnMutateConfig, Knn.Result> {

    @Override
    public String mutateRelationshipType() {
        return "SIMILAR";
    }

    @Override
    public String mutateProperty() {
        return "score";
    }

    @Override
    public ValueType mutatePropertyType() {
        return ValueType.DOUBLE;
    }

    @Override
    public String expectedMutatedGraph() {
        return "  (a { knn: 1.0 } )" +
               ", (b { knn: 2.0 } )" +
               ", (c { knn: 5.0 } )" +
               ", (a)-[]->(b)" +
               ", (a)-[:SIMILAR {w: 0.5}]->(b)" +
               ", (b)-[:SIMILAR {w: 0.5}]->(a)" +
               ", (c)-[:SIMILAR {w: 0.25}]->(b)";
    }

    @Override
    public Class<? extends AlgoBaseProc<Knn, Knn.Result, KnnMutateConfig, ?>> getProcedureClazz() {
        return KnnMutateProc.class;
    }

    @Override
    public KnnMutateConfig createConfig(CypherMapWrapper mapWrapper) {
        return KnnMutateConfig.of(mapWrapper);
    }

    @Override
    public CypherMapWrapper createMinimalConfig(CypherMapWrapper mapWrapper) {
        var map = super.createMinimalConfig(mapWrapper);
        if (!map.containsKey("mutateProperty")) {
            map = map.withString("mutateProperty", mutateProperty());
        }
        if (!map.containsKey("mutateRelationshipType")) {
            map = map.withString("mutateRelationshipType", mutateRelationshipType());
        }
        return map;
    }

    @Test
    void shouldMutateResults() {
        String query = GdsCypher.call(GRAPH_NAME)
            .algo("gds.knn")
            .mutateMode()
            .addParameter("sudo", true)
            .addParameter("nodeProperties", List.of("knn"))
            .addParameter("topK", 1)
            .addParameter("randomSeed", 42)
            .addParameter("concurrency", 1)
            .addParameter("mutateRelationshipType", mutateRelationshipType())
            .addParameter("mutateProperty", mutateProperty())
            .yields();

        runQueryWithRowConsumer(query, row -> {
            assertEquals(3, row.getNumber("nodesCompared").longValue());
            assertEquals(37, row.getNumber("nodePairsConsidered").longValue());
            assertEquals(true, row.getBoolean("didConverge"));
            assertEquals(1, row.getNumber("ranIterations").longValue());

            assertEquals(3, row.getNumber("relationshipsWritten").longValue());
            assertUserInput(row, "mutateRelationshipType", "SIMILAR");
            assertUserInput(row, "mutateProperty", "score");
            assertThat("Missing computeMillis", -1L, lessThan(row.getNumber("computeMillis").longValue()));
            assertThat("Missing preProcessingMillis", -1L, lessThan(row.getNumber("preProcessingMillis").longValue()));
            assertThat("Missing mutateMillis", -1L, lessThan(row.getNumber("mutateMillis").longValue()));

            Map<String, Double> distribution = (Map<String, Double>) row.get("similarityDistribution");
            assertThat("Missing min", -1.0, lessThan(distribution.get("min")));
            assertThat("Missing max", -1.0, lessThan(distribution.get("max")));
            assertThat("Missing mean", -1.0, lessThan(distribution.get("mean")));
            assertThat("Missing stdDev", -1.0, lessThan(distribution.get("stdDev")));
            assertThat("Missing p1", -1.0, lessThan(distribution.get("p1")));
            assertThat("Missing p5", -1.0, lessThan(distribution.get("p5")));
            assertThat("Missing p10", -1.0, lessThan(distribution.get("p10")));
            assertThat("Missing p25", -1.0, lessThan(distribution.get("p25")));
            assertThat("Missing p50", -1.0, lessThan(distribution.get("p50")));
            assertThat("Missing p75", -1.0, lessThan(distribution.get("p75")));
            assertThat("Missing p90", -1.0, lessThan(distribution.get("p90")));
            assertThat("Missing p95", -1.0, lessThan(distribution.get("p95")));
            assertThat("Missing p99", -1.0, lessThan(distribution.get("p99")));
            assertThat("Missing p100", -1.0, lessThan(distribution.get("p100")));

            assertThat(
                "Missing postProcessingMillis",
                -1L,
                equalTo(row.getNumber("postProcessingMillis").longValue())
            );
        });
    }


    @Override
    @Test
    @Disabled("This test does not work for KNN")
    public void testGraphMutationOnFilteredGraph() {}

    @Test
    void shouldMutateUniqueRelationships() {
        var graphName = "undirectedGraph";

        var graphCreateQuery = GdsCypher.call(graphName)
            .graphProject()
            .withAnyLabel()
            .withNodeProperty("knn")
            .withRelationshipType("IGNORE", Orientation.UNDIRECTED)
            .yields();

        runQuery(graphCreateQuery);

        var query = GdsCypher.call(graphName)
            .algo("gds.knn")
            .mutateMode()
            .addParameter("sudo", true)
            .addParameter("nodeProperties", List.of("knn"))
            .addParameter("topK", 1)
            .addParameter("concurrency", 1)
            .addParameter("randomSeed", 42)
            .addParameter("mutateRelationshipType", "SIMILAR")
            .addParameter("mutateProperty", "score")
            .yields("relationshipsWritten");

        runQueryWithRowConsumer(query, row -> assertEquals(3, row.getNumber("relationshipsWritten").longValue()));
    }

    @Override
    public void setupStoreLoader(StoreLoaderBuilder storeLoaderBuilder, Map<String, Object> config) {
        var nodeProperties = config.get("nodeProperties");
        if (nodeProperties != null) {
            Iterable<String> properties = (List<String>) nodeProperties;
            for (String property : properties) {
                runQuery(
                    graphDb(),
                    "CALL db.createProperty($prop)",
                    Map.of("prop", property)
                );
                storeLoaderBuilder.addNodeProperty(
                    ImmutablePropertyMapping.builder()
                        .propertyKey(property)
                        .defaultValue(DefaultValue.forDouble())
                        .build()
                );
            }
        }
    }

    @Test
    void shouldMutateWithFilteredNodes() {
        String nodeCreateQuery =
            "CREATE " +
            "  (alice:Person {age: 24})" +
            " ,(carol:Person {age: 24})" +
            " ,(eve:Person {age: 67})" +
            " ,(dave:Foo {age: 48})" +
            " ,(bob:Foo {age: 48})";

        runQuery(nodeCreateQuery);

        String createQuery = GdsCypher.call("graph")
            .graphProject()
            .withNodeLabel("Person")
            .withNodeLabel("Foo")
            .withNodeProperty("age")
            .withAnyRelationshipType()
            .yields();
        runQuery(createQuery);

        String relationshipType = "SIMILAR";
        String relationshipProperty = "score";

        String algoQuery = GdsCypher.call("graph")
            .algo("gds.knn")
            .mutateMode()
            .addParameter("nodeLabels", List.of("Foo"))
            .addParameter("nodeProperties", List.of("age"))
            .addParameter("mutateRelationshipType", relationshipType)
            .addParameter("mutateProperty", relationshipProperty).yields();
        runQuery(algoQuery);

        Graph mutatedGraph = GraphStoreCatalog.get(getUsername(), db.databaseId(), "graph").graphStore().getUnion();

        assertGraphEquals(
            fromGdl(
                nodeCreateQuery +
                "(dave)-[:SIMILAR {score: 1.0}]->(bob)" +
                "(bob)-[{score: 1.0}]->(dave)"
            ),
            mutatedGraph
        );
    }

    @Test
    void shouldMutateWithSimilarityThreshold() {
        String relationshipType = "SIMILAR";
        String relationshipProperty = "score";

        String nodeCreateQuery =
            "CREATE " +
            "  (alice:Person {age: 23})" +
            " ,(carol:Person {age: 24})" +
            " ,(eve:Person {age: 34})" +
            " ,(bob:Person {age: 30})";

        runQuery(nodeCreateQuery);

        String createQuery = GdsCypher.call("graph")
            .graphProject()
            .withNodeLabel("Person")
            .withNodeProperty("age")
            .withAnyRelationshipType()
            .yields();
        runQuery(createQuery);

        String algoQuery = GdsCypher.call("graph")
            .algo("gds.knn")
            .mutateMode()
            .addParameter("nodeProperties", List.of("age"))
            .addParameter("similarityCutoff", 0.14)
            .addParameter("mutateRelationshipType", relationshipType)
            .addParameter("mutateProperty", relationshipProperty).yields();
        runQuery(algoQuery);

        Graph mutatedGraph = GraphStoreCatalog.get(getUsername(), db.databaseId(), "graph").graphStore().getUnion();

        assertEquals(6, mutatedGraph.relationshipCount());

    }

    @Test
    void shouldMutateWithFilteredNodesAndMultipleProperties() {
        String nodeCreateQuery =
            "CREATE " +
            "  (alice:Person {age: 24, knn: 24})" +
            " ,(carol:Person {age: 24, knn: 24})" +
            " ,(eve:Person {age: 67, knn: 67})" +
            " ,(dave:Foo {age: 48, knn: 24})" +
            " ,(bob:Foo {age: 48, knn: 48} )";

        runQuery(nodeCreateQuery);

        String createQuery = GdsCypher.call("graph")
            .graphProject()
            .withNodeLabel("Person")
            .withNodeLabel("Foo")
            .withNodeProperty("age")
            .withNodeProperty("knn")

            .withAnyRelationshipType()
            .yields();
        runQuery(createQuery);

        String relationshipType = "SIMILAR";
        String relationshipProperty = "score";

        String algoQuery = GdsCypher.call("graph")
            .algo("gds.knn")
            .mutateMode()
            .addParameter("nodeLabels", List.of("Foo"))
            .addParameter("nodeProperties", List.of("age", "knn"))
            .addParameter("mutateRelationshipType", relationshipType)
            .addParameter("mutateProperty", relationshipProperty).yields();
        runQuery(algoQuery);

        Graph mutatedGraph = GraphStoreCatalog.get(getUsername(), db.databaseId(), "graph").graphStore().getUnion();

        assertGraphEquals(
            fromGdl(
                nodeCreateQuery +
                "(dave)-[:SIMILAR {score: 0.52}]->(bob)" +
                "(bob)-[{score: 0.52}]->(dave)"
            ),
            mutatedGraph
        );//  0.5 * (1/(1+(48-48)) + 0.5 *(1/(1+(48-24)) = 0.52
    }

}
