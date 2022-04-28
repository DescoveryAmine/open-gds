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
package org.neo4j.gds.impl.similarity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.gds.results.SimilarityResult;
import org.neo4j.gds.core.utils.TerminationFlag;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimilarityStreamGeneratorTest {

    private static final SimilarityComputer<CategoricalInput> ALL_PAIRS_COMPUTER = (decoder, source, target, cutoff) ->
            similarityResult(source.id, target.id, true, false);

    private static SimilarityResult similarityResult(
            long sourceId,
            long targetId,
            boolean bidirectional,
            boolean reversed) {
        return new SimilarityResult(sourceId, targetId, -1, -1, -1, 0.7, bidirectional, reversed);
    }

    private static final SimilarityComputer<CategoricalInput> COMPUTER = (decoder, source, target, cutoff) ->
            similarityResult(source.id, target.id, false, false);

    private static final Supplier<RleDecoder> DECODER = () -> null;

    @ParameterizedTest(name = "concurrency = {0}")
    @ValueSource(ints = {1, 2, 8})
    void allPairs(int concurrency) {

        SimilarityStreamGenerator<CategoricalInput> generator = new SimilarityStreamGenerator<>(
                terminationFlag(),
                concurrency,
                DECODER,
                ALL_PAIRS_COMPUTER);

        CategoricalInput[] ids = new CategoricalInput[3];
        ids[0] = new CategoricalInput(0, new long[]{});
        ids[1] = new CategoricalInput(1, new long[]{});
        ids[2] = new CategoricalInput(2, new long[]{});

        Stream<SimilarityResult> stream = generator.stream(ids, -1.0, 0);

        List<SimilarityResult> rows = stream.collect(Collectors.toList());
        assertEquals(3, rows.size());

        assertThat(rows, hasItems(similarityResult(0, 1, true, false)));
        assertThat(rows, hasItems(similarityResult(0, 2, true, false)));
        assertThat(rows, hasItems(similarityResult(1, 2, true, false)));
    }

    @ParameterizedTest(name = "concurrency = {0}")
    @ValueSource(ints = {1, 2, 8})
    void allPairsTopK(int concurrency) {

        SimilarityStreamGenerator<CategoricalInput> generator = new SimilarityStreamGenerator<>(
                terminationFlag(),
                concurrency,
                DECODER,
                ALL_PAIRS_COMPUTER);

        CategoricalInput[] ids = new CategoricalInput[3];
        ids[0] = new CategoricalInput(0, new long[]{});
        ids[1] = new CategoricalInput(1, new long[]{});
        ids[2] = new CategoricalInput(2, new long[]{});

        Stream<SimilarityResult> stream = generator.stream(ids, -1.0, 1);

        List<SimilarityResult> rows = stream.collect(Collectors.toList());
        assertEquals(3, rows.size());

        assertThat(rows, hasItems(similarityResult(0, 1, true, false)));
        assertThat(rows, hasItems(similarityResult(1, 0, true, true)));
        assertThat(rows, hasItems(similarityResult(2, 0, true, true)));
    }

    @ParameterizedTest(name = "concurrency = {0}")
    @ValueSource(ints = {1, 2, 8})
    void sourceSpecifiedTargetSpecified(int concurrency) {

        SimilarityStreamGenerator<CategoricalInput> generator = new SimilarityStreamGenerator<>(
                terminationFlag(),
                concurrency,
                DECODER,
                COMPUTER);

        CategoricalInput[] ids = new CategoricalInput[3];
        ids[0] = new CategoricalInput(0, new long[]{});
        ids[1] = new CategoricalInput(1, new long[]{});
        ids[2] = new CategoricalInput(2, new long[]{});

        int[] sourceIndexIds = new int[]{0};
        int[] targetIndexIds = new int[]{1, 2};
        Stream<SimilarityResult> stream = generator.stream(ids, sourceIndexIds, targetIndexIds, -1.0, 0);

        List<SimilarityResult> rows = stream.collect(Collectors.toList());
        assertEquals(2, rows.size());

        assertThat(rows, hasItems(similarityResult(0, 1, false, false)));
        assertThat(rows, hasItems(similarityResult(0, 2, false, false)));
    }

    @ParameterizedTest(name = "concurrency = {0}")
    @ValueSource(ints = {1, 2, 8})
    void sourceSpecifiedTargetSpecifiedTopK(int concurrency) {
        SimilarityStreamGenerator<CategoricalInput> generator = new SimilarityStreamGenerator<>(
                terminationFlag(),
                concurrency,
                DECODER,
                COMPUTER);


        CategoricalInput[] ids = new CategoricalInput[3];
        ids[0] = new CategoricalInput(0, new long[]{});
        ids[1] = new CategoricalInput(1, new long[]{});
        ids[2] = new CategoricalInput(2, new long[]{});

        int[] sourceIndexIds = new int[]{1};
        int[] targetIndexIds = new int[]{0, 2};
        Stream<SimilarityResult> stream = generator.stream(ids, sourceIndexIds, targetIndexIds, -1.0, 1);

        List<SimilarityResult> rows = stream.collect(Collectors.toList());
        assertEquals(1, rows.size());

        assertThat(rows, hasItems(similarityResult(1, 0, false, false)));
    }

    @ParameterizedTest(name = "concurrency = {0}")
    @ValueSource(ints = {1, 2, 8})
    void sourceSpecifiedTargetNotSpecified(int concurrency) {
        SimilarityStreamGenerator<CategoricalInput> generator = new SimilarityStreamGenerator<>(
                terminationFlag(),
                concurrency,
                DECODER,
                COMPUTER);


        CategoricalInput[] ids = new CategoricalInput[4];
        ids[0] = new CategoricalInput(0, new long[]{});
        ids[1] = new CategoricalInput(1, new long[]{});
        ids[2] = new CategoricalInput(2, new long[]{});
        ids[3] = new CategoricalInput(3, new long[]{});

        int[] sourceIndexIds = new int[]{1, 3};
        int[] targetIndexIds = new int[]{};

        Stream<SimilarityResult> stream = generator.stream(ids, sourceIndexIds, targetIndexIds, -1.0, 0);

        List<SimilarityResult> rows = stream.collect(Collectors.toList());

        assertEquals(6, rows.size());

        assertThat(rows, hasItems(similarityResult(1, 0, false, false)));
        assertThat(rows, hasItems(similarityResult(1, 2, false, false)));
        assertThat(rows, hasItems(similarityResult(1, 3, false, false)));
        assertThat(rows, hasItems(similarityResult(3, 0, false, false)));
        assertThat(rows, hasItems(similarityResult(3, 1, false, false)));
        assertThat(rows, hasItems(similarityResult(3, 2, false, false)));
    }

    @ParameterizedTest(name = "concurrency = {0}")
    @ValueSource(ints = {1, 2, 8})
    void sourceSpecifiedTargetNotSpecifiedTopK(int concurrency) {
        SimilarityStreamGenerator<CategoricalInput> generator = new SimilarityStreamGenerator<>(
                terminationFlag(),
                concurrency,
                DECODER,
                COMPUTER);


        CategoricalInput[] ids = new CategoricalInput[4];
        ids[0] = new CategoricalInput(0, new long[]{});
        ids[1] = new CategoricalInput(1, new long[]{});
        ids[2] = new CategoricalInput(2, new long[]{});
        ids[3] = new CategoricalInput(3, new long[]{});

        int[] sourceIndexIds = new int[]{1, 3};
        int[] targetIndexIds = new int[]{};

        Stream<SimilarityResult> stream = generator.stream(ids, sourceIndexIds, targetIndexIds, -1.0, 1);

        List<SimilarityResult> rows = stream.collect(Collectors.toList());

        assertEquals(2, rows.size());

        assertThat(rows, hasItems(similarityResult(1, 0, false, false)));
        assertThat(rows, hasItems(similarityResult(3, 0, false, false)));
    }

    @ParameterizedTest(name = "concurrency = {0}")
    @ValueSource(ints = {1, 2, 8})
    void sourceNotSpecifiedTargetSpecified(int concurrency) {
        SimilarityStreamGenerator<CategoricalInput> generator = new SimilarityStreamGenerator<>(
                terminationFlag(),
                concurrency,
                DECODER,
                COMPUTER);


        CategoricalInput[] ids = new CategoricalInput[4];
        ids[0] = new CategoricalInput(0, new long[]{});
        ids[1] = new CategoricalInput(1, new long[]{});
        ids[2] = new CategoricalInput(2, new long[]{});
        ids[3] = new CategoricalInput(3, new long[]{});

        int[] sourceIndexIds = new int[]{};
        int[] targetIndexIds = new int[]{2, 3};

        Stream<SimilarityResult> stream = generator.stream(ids, sourceIndexIds, targetIndexIds, -1.0, 0);

        List<SimilarityResult> rows = stream.collect(Collectors.toList());

        assertEquals(6, rows.size());

        assertThat(rows, hasItems(similarityResult(0, 3, false, false)));
        assertThat(rows, hasItems(similarityResult(1, 3, false, false)));
        assertThat(rows, hasItems(similarityResult(2, 3, false, false)));

        assertThat(rows, hasItems(similarityResult(0, 2, false, false)));
        assertThat(rows, hasItems(similarityResult(1, 2, false, false)));
        assertThat(rows, hasItems(similarityResult(2, 3, false, false)));
    }

    @ParameterizedTest(name = "concurrency = {0}")
    @ValueSource(ints = {1, 2, 8})
    void sourceNotSpecifiedTargetSpecifiedTopK(int concurrency) {
        SimilarityStreamGenerator<CategoricalInput> generator = new SimilarityStreamGenerator<>(
                terminationFlag(),
                concurrency,
                DECODER,
                COMPUTER);


        CategoricalInput[] ids = new CategoricalInput[4];
        ids[0] = new CategoricalInput(0, new long[]{});
        ids[1] = new CategoricalInput(1, new long[]{});
        ids[2] = new CategoricalInput(2, new long[]{});
        ids[3] = new CategoricalInput(3, new long[]{});

        int[] sourceIndexIds = new int[]{};
        int[] targetIndexIds = new int[]{2, 3};

        Stream<SimilarityResult> stream = generator.stream(ids, sourceIndexIds, targetIndexIds, -1.0, 1);


        List<SimilarityResult> rows = stream.collect(Collectors.toList());

        assertEquals(4, rows.size());
        assertThat(rows, hasItems(similarityResult(0, 2, false, false)));
        assertThat(rows, hasItems(similarityResult(1, 2, false, false)));
        assertThat(rows, hasItems(similarityResult(2, 3, false, false)));
        assertThat(rows, hasItems(similarityResult(3, 2, false, false)));
    }

    @ParameterizedTest(name = "concurrency = {0}")
    @ValueSource(ints = {1, 2, 8})
    void sourceTargetOverlap(int concurrency) {
        SimilarityStreamGenerator<CategoricalInput> generator = new SimilarityStreamGenerator<>(
                terminationFlag(),
                concurrency,
                DECODER,
                COMPUTER);

        CategoricalInput[] ids = new CategoricalInput[4];
        ids[0] = new CategoricalInput(5, new long[]{});
        ids[1] = new CategoricalInput(6, new long[]{});
        ids[2] = new CategoricalInput(7, new long[]{});
        ids[3] = new CategoricalInput(8, new long[]{});

        int[] sourceIndexIds = new int[]{0, 1, 2};
        int[] targetIndexIds = new int[]{1, 2};

        Stream<SimilarityResult> stream = generator.stream(ids, sourceIndexIds, targetIndexIds, -1.0, 0);


        List<SimilarityResult> rows = stream.collect(Collectors.toList());

        assertEquals(4, rows.size());

        assertThat(rows, hasItems(similarityResult(5, 6, false, false)));
        assertThat(rows, hasItems(similarityResult(5, 7, false, false)));
        assertThat(rows, hasItems(similarityResult(6, 7, false, false)));
        assertThat(rows, hasItems(similarityResult(7, 6, false, false)));
    }

    @ParameterizedTest(name = "concurrency = {0}")
    @ValueSource(ints = {1, 2, 8})
    void sourceTargetOverlapTopK(int concurrency) {
        SimilarityStreamGenerator<CategoricalInput> generator = new SimilarityStreamGenerator<>(
                terminationFlag(),
                concurrency,
                DECODER,
                COMPUTER);

        CategoricalInput[] ids = new CategoricalInput[4];
        ids[0] = new CategoricalInput(5, new long[]{});
        ids[1] = new CategoricalInput(6, new long[]{});
        ids[2] = new CategoricalInput(7, new long[]{});
        ids[3] = new CategoricalInput(8, new long[]{});

        int[] sourceIndexIds = new int[]{0, 1, 2};
        int[] targetIndexIds = new int[]{1, 2};

        Stream<SimilarityResult> stream = generator.stream(ids, sourceIndexIds, targetIndexIds, -1.0, 1);

        List<SimilarityResult> rows = stream.collect(Collectors.toList());

        assertEquals(3, rows.size());
        assertThat(rows, hasItems(similarityResult(5, 6, false, false)));
        assertThat(rows, hasItems(similarityResult(6, 7, false, false)));
        assertThat(rows, hasItems(similarityResult(7, 6, false, false)));
    }

    private TerminationFlag terminationFlag() {
        TerminationFlag terminationFlag = mock(TerminationFlag.class);
        when(terminationFlag.running()).thenReturn(true);
        return terminationFlag;
    }
}
