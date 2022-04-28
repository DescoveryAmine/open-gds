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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.AlgoBaseProc;
import org.neo4j.gds.GdsCypher;
import org.neo4j.gds.ImmutablePropertyMapping;
import org.neo4j.gds.NonReleasingTaskRegistry;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.StoreLoaderBuilder;
import org.neo4j.gds.WriteRelationshipWithPropertyTest;
import org.neo4j.gds.api.DefaultValue;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.loading.GraphStoreCatalog;
import org.neo4j.gds.core.utils.progress.GlobalTaskStore;
import org.neo4j.gds.core.utils.progress.TaskRegistry;
import org.neo4j.gds.core.utils.progress.tasks.Task;

import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.gds.TestSupport.assertGraphEquals;
import static org.neo4j.gds.TestSupport.fromGdl;

class KnnWriteProcTest extends KnnProcTest<KnnWriteConfig> implements WriteRelationshipWithPropertyTest<Knn, KnnWriteConfig, Knn.Result> {

    @Override
    public Class<? extends AlgoBaseProc<Knn, Knn.Result, KnnWriteConfig, ?>> getProcedureClazz() {
        return KnnWriteProc.class;
    }

    @Override
    public KnnWriteConfig createConfig(CypherMapWrapper mapWrapper) {
        return KnnWriteConfig.of(mapWrapper);
    }

    @Override
    public CypherMapWrapper createMinimalConfig(CypherMapWrapper mapWrapper) {
        var map = super.createMinimalConfig(mapWrapper);
        if (!map.containsKey("writeProperty")) {
            map = map.withString("writeProperty", writeProperty());
        }
        if (!map.containsKey("writeRelationshipType")) {
            map = map.withString("writeRelationshipType", writeRelationshipType());
        }
        return map;
    }

    @Test
    void shouldWriteResults() {
        String query = GdsCypher.call(GRAPH_NAME)
            .algo("gds.knn")
            .writeMode()
            .addParameter("sudo", true)
            .addParameter("nodeProperties", List.of("knn"))
            .addParameter("topK", 1)
            .addParameter("concurrency", 1)
            .addParameter("randomSeed", 42)
            .addParameter("writeRelationshipType", "SIMILAR")
            .addParameter("writeProperty", "score")
            .yields();

        runQueryWithRowConsumer(query, row -> {
            assertEquals(3, row.getNumber("nodesCompared").longValue());
            assertEquals(3, row.getNumber("relationshipsWritten").longValue());
            assertEquals(37, row.getNumber("nodePairsConsidered").longValue());
            assertEquals(true, row.getBoolean("didConverge"));
            assertEquals(1, row.getNumber("ranIterations").longValue());

            assertUserInput(row, "writeRelationshipType", "SIMILAR");
            assertUserInput(row, "writeProperty", "score");
            assertThat("Missing computeMillis", -1L, lessThan(row.getNumber("computeMillis").longValue()));
            assertThat("Missing preProcessingMillis", -1L, lessThan(row.getNumber("preProcessingMillis").longValue()));
            assertThat("Missing writeMillis", -1L, lessThan(row.getNumber("writeMillis").longValue()));

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

        String resultGraphName = "simGraph";
        String loadQuery = GdsCypher.call(resultGraphName)
            .graphProject()
            .withAnyLabel()
            .withNodeProperty("id")
            .withRelationshipType("SIMILAR")
            .withRelationshipProperty("score")
            .yields();

        runQuery(loadQuery);

        assertGraphEquals(
            fromGdl("(a {id: 1})-[:SIMILAR {w: 0.5}]->(b {id: 2}), (b)-[:SIMILAR {w: 0.5}]->(a), (c {id: 3})-[:SIMILAR {w: 0.25}]->(b)"),
            GraphStoreCatalog.get(getUsername(), namedDatabaseId(), resultGraphName).graphStore().getUnion()
        );
    }

    @Test
    void shouldWriteUniqueRelationships() {
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
            .writeMode()
            .addParameter("sudo", true)
            .addParameter("nodeProperties", List.of("knn"))
            .addParameter("topK", 1)
            .addParameter("concurrency", 1)
            .addParameter("randomSeed", 42)
            .addParameter("writeRelationshipType", "SIMILAR")
            .addParameter("writeProperty", "score")
            .yields("relationshipsWritten");

        runQueryWithRowConsumer(query, row -> assertEquals(3, row.getNumber("relationshipsWritten").longValue()));
    }

    @Test
    void testProgressTracking() {
        var graphName = "undirectedGraph";

        var graphCreateQuery = GdsCypher.call(graphName)
            .graphProject()
            .withAnyLabel()
            .withNodeProperty("knn")
            .withRelationshipType("IGNORE", Orientation.UNDIRECTED)
            .yields();

        runQuery(graphCreateQuery);

        applyOnProcedure(proc -> {
            var pathProc = ((KnnWriteProc) proc);

            var taskStore = new GlobalTaskStore();

            pathProc.taskRegistryFactory = () -> new NonReleasingTaskRegistry(new TaskRegistry(
                getUsername(),
                taskStore
            ));

            pathProc.write("undirectedGraph", createMinimalConfig(CypherMapWrapper.empty()).toMap());

            Assertions.assertThat(taskStore.taskStream().map(Task::description)).containsExactlyInAnyOrder(
                "KnnWriteProc :: WriteRelationships",
                "Knn"
            );
        });
    }

    @Test
    void shouldWriteWithFilteredNodes() {
        runQuery("CREATE (alice:Person {name: 'Alice', age: 24})" +
                 "CREATE (carol:Person {name: 'Carol', age: 24})" +
                 "CREATE (eve:Person {name: 'Eve', age: 67})" +
                 "CREATE (dave:Foo {name: 'Dave', age: 48})" +
                 "CREATE (bob:Foo {name: 'Bob', age: 48})");

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
            .writeMode()
            .addParameter("nodeLabels", List.of("Foo"))
            .addParameter("nodeProperties", List.of("age"))
            .addParameter("writeRelationshipType", relationshipType)
            .addParameter("writeProperty", relationshipProperty).yields();
        runQuery(algoQuery);

        Graph knnGraph = new StoreLoaderBuilder()
            .api(db)
            .addNodeLabel("Person")
            .addNodeLabel("Foo")
            .addRelationshipType(relationshipType)
            .addRelationshipProperty(relationshipProperty, relationshipProperty, DefaultValue.DEFAULT, Aggregation.NONE)
            .build()
            .graph();

        assertGraphEquals(
            fromGdl("(alice:Person)" +
                    "(carol:Person)" +
                    "(eve:Person)" +
                    "(dave:Foo)" +
                    "(bob:Foo)" +
                    "(dave)-[:SIMILAR {score: 1.0}]->(bob)" +
                    "(bob)-[:SIMILAR {score: 1.0}]->(dave)"
            ),
            knnGraph
        );
    }

    @Override
    public String writeRelationshipType() {
        return "KNN_REL";
    }

    @Override
    public String writeProperty() {
        return "similarity";
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
}
