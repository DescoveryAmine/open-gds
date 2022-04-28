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
package org.neo4j.gds.ml.linkmodels.pipeline.predict;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.neo4j.gds.AlgoBaseProc;
import org.neo4j.gds.GdsCypher;
import org.neo4j.gds.Orientation;

import java.util.List;
import java.util.Map;

class LinkPredictionPipelineStreamProcTest extends LinkPredictionPipelineProcTestBase {

    @Override
    Class<? extends AlgoBaseProc<?, ?, ?, ?>> getProcedureClazz() {
        return LinkPredictionPipelineStreamProc.class;
    }

    @ParameterizedTest
    @CsvSource(value = {"1, N", "4, M"})
    void shouldPredictWithTopN(int concurrency, String nodeLabel) {
        var nodeCount = 5L;
        assertCypherResult("CALL gds.graph.list('g') YIELD nodeCount RETURN nodeCount",
            List.of(Map.of("nodeCount", 2 * nodeCount))
        );
        var labelOffset = nodeLabel.equals("N") ? 0 : nodeCount;
        assertCypherResult(
            "CALL gds.beta.pipeline.linkPrediction.predict.stream('g', {" +
            " nodeLabels: [$nodeLabel]," +
            " modelName: 'model'," +
            " threshold: 0," +
            " topN: $topN," +
            " concurrency:" +
            " $concurrency" +
            "})" +
            "YIELD node1, node2, probability" +
            " RETURN node1, node2, probability" +
            " ORDER BY probability DESC, node1",
            Map.of("topN", 3, "concurrency", concurrency, "nodeLabel", nodeLabel),
            List.of(
                Map.of("node1", 0L + labelOffset, "node2", 4L + labelOffset, "probability", .49750002083312506),
                Map.of("node1", 1L + labelOffset, "node2", 4L + labelOffset, "probability", .11815697780926955),
                Map.of("node1", 0L + labelOffset, "node2", 1L + labelOffset, "probability", .1150667320455498)
            )
        );
    }

    @Test
    void requiresUndirectedGraph() {
        runQuery(projectQuery("g2", Orientation.NATURAL));

        var query = GdsCypher
            .call("g2")
            .algo("gds.beta.pipeline.linkPrediction.predict")
            .streamMode()
            .addParameter("modelName", "model")
            .addParameter("threshold", 0.5)
            .addParameter("topN", 9)
            .yields();

        assertError(query, "Procedure requires relationship projections to be UNDIRECTED.");
    }

    @Test
    void estimate() {
        assertCypherResult(
            "CALL gds.beta.pipeline.linkPrediction.predict.stream.estimate('g', {" +
            " modelName: 'model'," +
            " threshold: 0," +
            " topN: $topN" +
            "})" +
            "YIELD requiredMemory",
            Map.of("topN", 3),
            List.of(
                Map.of("requiredMemory", "644 Bytes")
            )
        );
    }
}
