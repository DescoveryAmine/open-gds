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
package org.neo4j.gds.ml.splitting;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.api.PropertyCursor;
import org.neo4j.gds.api.Relationships;
import org.neo4j.gds.beta.generator.RandomGraphGenerator;
import org.neo4j.gds.beta.generator.RelationshipDistribution;
import org.neo4j.gds.config.RandomGraphGeneratorConfig;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.huge.HugeGraph;
import org.neo4j.gds.core.loading.construction.GraphFactory;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.extension.TestGraph;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.gds.ml.splitting.DirectedEdgeSplitter.NEGATIVE;
import static org.neo4j.gds.ml.splitting.DirectedEdgeSplitter.POSITIVE;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

@GdlExtension
class UndirectedEdgeSplitterTest extends EdgeSplitterBaseTest {

    @GdlGraph(orientation = Orientation.UNDIRECTED)
    static String gdl = "(:A)-[:T {foo: 5} ]->(:A)-[:T {foo: 5} ]->(:A)-[:T {foo: 5} ]->(:A)-[:T {foo: 5} ]->(:A)-[:T {foo: 5} ]->(:A)";

    @Inject
    TestGraph graph;

    @Test
    void split() {
        var splitter = new UndirectedEdgeSplitter(Optional.of(1337L), 1.0);

        // select 20%, which is 1 (undirected) rels in this graph
        var result = splitter.split(graph, .2);

        var remainingRels = result.remainingRels();
        // 1 positive selected reduces remaining
        assertEquals(8L, remainingRels.topology().elementCount());
        assertEquals(Orientation.UNDIRECTED, remainingRels.topology().orientation());
        assertFalse(remainingRels.topology().isMultiGraph());
        assertThat(remainingRels.properties()).isNotEmpty();

        var selectedRels = result.selectedRels();
        assertThat(selectedRels.topology()).satisfies(topology -> {
            // it selected 2,5 (neg) and 3,2 (pos) relationships
            assertEquals(2L, topology.elementCount());
            assertRelExists(topology, 2, 5);
            assertRelExists(topology, 3, 2);
            assertEquals(Orientation.NATURAL, topology.orientation());
            assertFalse(topology.isMultiGraph());
        });

        var selectedProperties = selectedRels.properties().get();
        assertEquals(2, selectedProperties.elementCount());
        assertRelProperties(selectedProperties, 3, POSITIVE);
        assertRelProperties(selectedProperties, 2, NEGATIVE);
    }


    @Test
    void splitWithNegativeRatio() {
        var splitter = new UndirectedEdgeSplitter(Optional.of(1337L), 2.0);

        // select 20%, which is 1 (undirected) rels in this graph
        var result = splitter.split(graph, .2);

        var remainingRels = result.remainingRels();
        // 1 positive selected reduces remaining
        assertEquals(8L, remainingRels.topology().elementCount());
        assertEquals(Orientation.UNDIRECTED, remainingRels.topology().orientation());
        assertFalse(remainingRels.topology().isMultiGraph());
        assertThat(remainingRels.properties()).isNotEmpty();

        var selectedRels = result.selectedRels();
        assertThat(selectedRels.topology()).satisfies(topology -> {
            // it selected 2,5 (neg), 4,2 (neg) and 3,2 (pos) relationships
            assertEquals(3L, topology.elementCount());
            assertRelExists(topology, 3, 2);
            assertRelExists(topology, 2, 5);
            assertRelExists(topology, 4, 2);
            assertEquals(Orientation.NATURAL, topology.orientation());
            assertFalse(topology.isMultiGraph());
        });
        assertThat(selectedRels.properties()).isPresent().get().satisfies(p -> {
            assertEquals(3L, p.elementCount());
            assertRelProperties(p, 3, POSITIVE);
            assertRelProperties(p, 2, NEGATIVE);
            assertRelProperties(p, 4, NEGATIVE);
        });
    }

    @Test
    void negativeEdgesShouldNotOverlapMasterGraph() {
        var huuuuugeDenseGraph = RandomGraphGenerator.builder()
            .nodeCount(100)
            .averageDegree(95)
            .relationshipDistribution(RelationshipDistribution.UNIFORM)
            .seed(123L)
            .aggregation(Aggregation.SINGLE)
            .orientation(Orientation.UNDIRECTED)
            .allowSelfLoops(RandomGraphGeneratorConfig.AllowSelfLoops.NO)
            .build()
            .generate();

        var splitter = new UndirectedEdgeSplitter(Optional.of(42L), 1);
        var splitResult = splitter.split(huuuuugeDenseGraph, 0.9);
        var graph = GraphFactory.create(
            huuuuugeDenseGraph.idMap(),
            splitResult.remainingRels()
        );
        var nestedSplit = splitter.split(graph, huuuuugeDenseGraph, 0.9);
        Relationships nestedHoldout = nestedSplit.selectedRels();
        HugeGraph nestedHoldoutGraph = GraphFactory.create(graph, nestedHoldout);
        nestedHoldoutGraph.forEachNode(nodeId -> {
            nestedHoldoutGraph.forEachRelationship(nodeId, Double.NaN, (src, trg, val) -> {
                if (Double.compare(val, NEGATIVE) == 0) {
                    assertFalse(
                        huuuuugeDenseGraph.exists(src, trg),
                        formatWithLocale("Sampled negative edge %d,%d is an edge of the master graph.", src, trg)
                    );
                }
                return true;
            } );
            return true;
        });
    }

    @Test
    void shouldProduceDeterministicResult() {
        var graph = RandomGraphGenerator.builder()
            .nodeCount(100)
            .averageDegree(95)
            .relationshipDistribution(RelationshipDistribution.UNIFORM)
            .seed(123L)
            .aggregation(Aggregation.SINGLE)
            .orientation(Orientation.UNDIRECTED)
            .allowSelfLoops(RandomGraphGeneratorConfig.AllowSelfLoops.NO)
            .build()
            .generate();

        var splitResult1 = new UndirectedEdgeSplitter(Optional.of(12L), 1).split(graph, 0.5);
        var splitResult2 = new UndirectedEdgeSplitter(Optional.of(12L), 1).split(graph, 0.5);
        var remainingAreEqual = relationshipsAreEqual(
            graph,
            splitResult1.remainingRels(),
            splitResult2.remainingRels()
        );
        assertTrue(remainingAreEqual);

        var holdoutAreEqual = relationshipsAreEqual(
            graph,
            splitResult1.selectedRels(),
            splitResult2.selectedRels()
        );
        assertTrue(holdoutAreEqual);
    }

    @Test
    void shouldProduceNonDeterministicResult() {
        var graph = RandomGraphGenerator.builder()
            .nodeCount(100)
            .averageDegree(95)
            .relationshipDistribution(RelationshipDistribution.UNIFORM)
            .seed(123L)
            .aggregation(Aggregation.SINGLE)
            .orientation(Orientation.UNDIRECTED)
            .allowSelfLoops(RandomGraphGeneratorConfig.AllowSelfLoops.NO)
            .build()
            .generate();

        var splitResult1 = new UndirectedEdgeSplitter(Optional.empty(), 1.0).split(graph, 0.5);
        var splitResult2 = new UndirectedEdgeSplitter(Optional.empty(), 1.0).split(graph, 0.5);
        var remainingAreEqual = relationshipsAreEqual(
            graph,
            splitResult1.remainingRels(),
            splitResult2.remainingRels()
        );
        assertFalse(remainingAreEqual);

        var holdoutAreEqual = relationshipsAreEqual(
            graph,
            splitResult1.selectedRels(),
            splitResult2.selectedRels()
        );
        assertFalse(holdoutAreEqual);
    }

    @Test
    void negativeEdgeSampling() {
        var splitter = new UndirectedEdgeSplitter(Optional.of(42L), 1.0);

        var sum = 0;
        for (int i = 0; i < 100; i++) {
            var prev = splitter.samplesPerNode(i, 1000 - sum, 100 - i);
            sum += prev;
        }

        assertEquals(1000, sum);
    }

    @Test
    void samplesWithinBounds() {
        var splitter = new UndirectedEdgeSplitter(Optional.of(42L), 1.0);

        assertEquals(1, splitter.samplesPerNode(1, 100, 10));
        assertEquals(1, splitter.samplesPerNode(100, 1, 1));
    }

    @Test
    void shouldPreserveRelationshipWeights() {
        var splitter = new UndirectedEdgeSplitter(Optional.of(42L), 1.0);
        EdgeSplitter.SplitResult split = splitter.split(graph, 0.01);
        var maybeProp = split.remainingRels().properties();
        assertThat(maybeProp).isPresent();
        graph.forEachNode(nodeId -> {
            PropertyCursor propertyCursor = maybeProp.get().propertiesList().propertyCursor(nodeId);
            while (propertyCursor.hasNextLong()) {
                assertThat(Double.longBitsToDouble(propertyCursor.nextLong())).isEqualTo(5.0);
            }
            return true;
        });
    }

    private boolean relationshipsAreEqual(IdMap mapping, Relationships r1, Relationships r2) {
        var fallbackValue = -0.66;
        if (r1.topology().elementCount() != r2.topology().elementCount()) {
            return false;
        }
        var g1 = GraphFactory.create(mapping, r1);
        var g2 = GraphFactory.create(mapping, r2);
        var equalSoFar = new AtomicBoolean(true);
        g1.forEachNode(nodeId -> {
            g1.forEachRelationship(nodeId, fallbackValue, (source, target, val) -> {
                var g2Property = g2.relationshipProperty(source, target, fallbackValue);
                if ((!g2.exists(source, target)) || (Double.compare(g2Property, val) != 0))  {
                    equalSoFar.set(false);
                }
                return equalSoFar.get();
            });
            return equalSoFar.get();
        });
        return equalSoFar.get();
    }
}
