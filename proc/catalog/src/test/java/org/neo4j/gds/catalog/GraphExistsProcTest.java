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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.BaseProcTest;
import org.neo4j.gds.core.loading.GraphStoreCatalog;

import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.neo4j.gds.compat.MapUtil.map;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

class GraphExistsProcTest extends BaseProcTest {
    private static final String DB_CYPHER = "CREATE (:A)-[:REL]->(:A)";

    @BeforeEach
    void setup() throws Exception {
        registerProcedures(GraphProjectProc.class, GraphExistsProc.class);
        registerFunctions(GraphExistsFunc.class);

        runQuery(DB_CYPHER);
    }

    @AfterEach
    void tearDown() {
        GraphStoreCatalog.removeAllLoadedGraphs();
    }

    @ParameterizedTest(name = "Existing Graphs: {0}, Lookup: {1}, Exists: {2}")
    @MethodSource("graphNameExistsCombinations")
    void shouldReportOnGraphExistenceProc(String graphNameToCreate, String graphNameToCheck, boolean exists) {
        runQuery("CALL gds.graph.project($name, 'A', 'REL')", map("name", graphNameToCreate));

        assertCypherResult(
            "CALL gds.graph.exists($graphName)",
            map("graphName", graphNameToCheck),
            singletonList(
                map("graphName", graphNameToCheck, "exists", exists)
            )
        );
    }

    @ParameterizedTest(name = "Existing Graphs: {0}, Lookup: {1}, Exists: {2}")
    @MethodSource("graphNameExistsCombinations")
    void shouldReportOnGraphExistenceFunc(String graphNameToCreate, String graphNameToCheck, boolean exists) {
        runQuery("CALL gds.graph.project($name, 'A', 'REL')", map("name", graphNameToCreate));

        assertCypherResult(
            "RETURN gds.graph.exists($graphName) AS exists",
            map("graphName", graphNameToCheck),
            singletonList(
                map("exists", exists)
            )
        );
    }

    @ParameterizedTest
    @MethodSource("org.neo4j.gds.catalog.GraphProjectProcTest#invalidGraphNames")
    void failsOnInvalidGraphName(String invalidName) {
        assertError(
            "CALL gds.graph.exists($graphName)",
            map("graphName", invalidName),
            formatWithLocale("`graphName` can not be null or blank, but it was `%s`", invalidName)
        );
        assertError(
            "RETURN gds.graph.exists($graphName)",
            map("graphName", invalidName),
            formatWithLocale("`graphName` can not be null or blank, but it was `%s`", invalidName)
        );
    }

    static Stream<Arguments> graphNameExistsCombinations() {
        return Stream.of(
            arguments("g", "g", true),
            arguments("graph", "graph", true),
            arguments("graph", "graph1", false),
            arguments("g", "a", false)
        );
    }
}
