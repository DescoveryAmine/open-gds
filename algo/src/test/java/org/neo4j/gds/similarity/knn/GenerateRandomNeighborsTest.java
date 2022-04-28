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

import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import org.eclipse.collections.api.tuple.primitive.IntIntPair;
import org.neo4j.gds.api.nodeproperties.LongNodeProperties;
import org.neo4j.gds.core.utils.paged.HugeObjectArray;
import org.neo4j.gds.core.utils.partition.Partition;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;

import java.util.SplittableRandom;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateRandomNeighborsTest extends RandomNodeCountAndKValues {

    @Property(tries = 50)
    void neighborsForKEqualsNMinus1startWithEachOtherAsNeighbors(
        @ForAll @From("n and k") IntIntPair nAndK
    ) {
        int nodeCount = nAndK.getOne();
        int k = nAndK.getTwo();

        var allNeighbors = HugeObjectArray.newArray(
            NeighborList.class,
            nodeCount
        );

        var nodeProperties = new LongNodeProperties() {
            @Override
            public long longValue(long nodeId) {
                return nodeId;
            }

            @Override
            public long size() {
                return nodeCount;
            }
        };

        var similarityComputer = SimilarityComputer.ofProperty(nodeProperties, "myProperty");

        var random = new SplittableRandom();
        var generateRandomNeighbors = new GenerateRandomNeighbors(
            new UniformKnnSampler(random, nodeCount),
            random,
            similarityComputer,
            new KnnNeighborFilter(nodeCount),
            allNeighbors,
            k,
            k,
            Partition.of(0, nodeCount),
            ProgressTracker.NULL_TRACKER
        );

        generateRandomNeighbors.run();

        var possibleNeighbors = LongStream.range(0, nodeCount).toArray();
        for (int nodeId = 0; nodeId < nodeCount; nodeId++) {
            var neighbors = allNeighbors.get(nodeId);
            assertThat(neighbors.elements().toArray())
                .doesNotContain(nodeId)
                .hasSize(Math.min(k, nodeCount - 1))
                .containsAnyOf(possibleNeighbors)
                .doesNotHaveDuplicates();
        }
    }
}
