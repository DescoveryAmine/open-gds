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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.gds.ElementProjection;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.core.GraphDimensions;
import org.neo4j.gds.core.ImmutableGraphDimensions;
import org.neo4j.gds.core.utils.mem.MemoryRange;
import org.neo4j.gds.core.utils.mem.MemoryTree;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SplitRelationshipsTest {

    @Test
    void estimate() {
        var graphDimensions = GraphDimensions.of(1, 10_000);
        var config = SplitRelationshipsMutateConfigImpl
            .builder()
            .negativeSamplingRatio(1.0)
            .holdoutRelationshipType("HOLDOUT")
            .remainingRelationshipType("REST")
            .holdoutFraction(0.3)
            .relationshipTypes(List.of(ElementProjection.PROJECT_ALL))
            .build();

        MemoryTree actualEstimate = SplitRelationships.estimate(config)
            .estimate(graphDimensions, config.concurrency());

        assertThat(actualEstimate.memoryUsage())
            .withFailMessage("expected: " + actualEstimate.memoryUsage().max)
            .isEqualTo(MemoryRange.of(208_000));
    }

    public static Stream<Arguments> withTypesParams() {
        return Stream.of(
            Arguments.of(List.of("TYPE1"), MemoryRange.of(208)),
            Arguments.of(List.of("TYPE2"), MemoryRange.of(416)),
            Arguments.of(List.of("*"), MemoryRange.of(1_248)),
            Arguments.of(List.of("TYPE1", "TYPE2", "TYPE3"), MemoryRange.of(1_248)),
            Arguments.of(List.of("TYPE1", "TYPE3"), MemoryRange.of(832))
        );
    }

    @ParameterizedTest
    @MethodSource("withTypesParams")
    void estimateWithTypes(List<String> relTypes, MemoryRange expectedMemory) {
        var nodeCount = 100;
        var relationshipCounts = Map.of(
            RelationshipType.of("TYPE1"), 10L,
            RelationshipType.of("TYPE2"), 20L,
            RelationshipType.of("TYPE3"), 30L
        );

        var graphDimensions = ImmutableGraphDimensions.builder()
            .nodeCount(nodeCount)
            .relationshipCounts(relationshipCounts)
            .relCountUpperBound(relationshipCounts.values().stream().mapToLong(Long::longValue).sum())
            .build();

        var config = SplitRelationshipsMutateConfigImpl
            .builder()
            .negativeSamplingRatio(1.0)
            .holdoutRelationshipType("HOLDOUT")
            .remainingRelationshipType("REST")
            .holdoutFraction(0.3)
            .relationshipTypes(relTypes)
            .build();

        MemoryTree actualEstimate = SplitRelationships.estimate(config)
            .estimate(graphDimensions, config.concurrency());

        assertThat(actualEstimate.memoryUsage())
            .withFailMessage("expected: " + actualEstimate.memoryUsage().max)
            .isEqualTo(expectedMemory);
    }

    @Test
    void estimateIndependentOfNodeCount() {
        var config = SplitRelationshipsMutateConfigImpl
            .builder()
            .negativeSamplingRatio(1.0)
            .holdoutRelationshipType("HOLDOUT")
            .remainingRelationshipType("REST")
            .holdoutFraction(0.3)
            .relationshipTypes(List.of(ElementProjection.PROJECT_ALL))
            .build();

        var graphDimensions = GraphDimensions.of(1, 10_000);
        MemoryTree actualEstimate = SplitRelationships.estimate(config)
            .estimate(graphDimensions, config.concurrency());
        assertThat(actualEstimate.memoryUsage()).isEqualTo(MemoryRange.of(208_000));

        graphDimensions = GraphDimensions.of(100_000, 10_000);
        actualEstimate = SplitRelationships.estimate(config)
            .estimate(graphDimensions, config.concurrency());
        assertThat(actualEstimate.memoryUsage()).isEqualTo(MemoryRange.of(208_000));
    }

    @Test
    void estimateDifferentSamplingRatios() {
        var graphDimensions = GraphDimensions.of(1, 10_000);

        var configBuilder = SplitRelationshipsMutateConfigImpl
            .builder()
            .holdoutRelationshipType("HOLDOUT")
            .remainingRelationshipType("REST")
            .holdoutFraction(0.3)
            .relationshipTypes(List.of(ElementProjection.PROJECT_ALL));

        var config = configBuilder.negativeSamplingRatio(1.0).build();
        MemoryTree actualEstimate = SplitRelationships.estimate(config)
            .estimate(graphDimensions, config.concurrency());
        assertThat(actualEstimate.memoryUsage())
            .withFailMessage("expected: " + actualEstimate.memoryUsage().max)
            .isEqualTo(MemoryRange.of(208_000));

        config = configBuilder.negativeSamplingRatio(2.0).build();
        actualEstimate = SplitRelationships.estimate(config)
            .estimate(graphDimensions, config.concurrency());
        assertThat(actualEstimate.memoryUsage())
            .withFailMessage("expected: " + actualEstimate.memoryUsage().max)
            .isEqualTo(MemoryRange.of(256_000));
    }

    @Test
    void estimateDifferentHoldoutFractions() {
        var graphDimensions = GraphDimensions.of(1, 10_000);

        var configBuilder = SplitRelationshipsMutateConfigImpl
            .builder()
            .holdoutRelationshipType("HOLDOUT")
            .remainingRelationshipType("REST")
            .negativeSamplingRatio(1.0)
            .relationshipTypes(List.of(ElementProjection.PROJECT_ALL));

        var config = configBuilder.holdoutFraction(0.3).build();
        MemoryTree actualEstimate = SplitRelationships.estimate(config)
            .estimate(graphDimensions, config.concurrency());
        assertThat(actualEstimate.memoryUsage())
            .withFailMessage("expected: " + actualEstimate.memoryUsage().max)
            .isEqualTo(MemoryRange.of(208_000));

        config = configBuilder.holdoutFraction(0.1).build();
        actualEstimate = SplitRelationships.estimate(config)
            .estimate(graphDimensions, config.concurrency());
        assertThat(actualEstimate.memoryUsage())
            .withFailMessage("expected: " + actualEstimate.memoryUsage().max)
            .isEqualTo(MemoryRange.of(176_000));
    }
}
