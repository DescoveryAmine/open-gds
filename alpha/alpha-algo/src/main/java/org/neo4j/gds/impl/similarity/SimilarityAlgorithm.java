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

import org.neo4j.gds.Algorithm;
import org.neo4j.gds.core.ProcedureConstants;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.results.SimilarityResult;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public abstract class SimilarityAlgorithm<ME extends SimilarityAlgorithm<ME, INPUT>, INPUT extends SimilarityInput> extends Algorithm<SimilarityAlgorithmResult> {

    private static int[] indexesFor(long[] inputIds, List<Long> sourceIds, String key) {
        try {
            return SimilarityInput.indexes(inputIds, sourceIds);
        } catch(IllegalArgumentException exception) {
            String message = formatWithLocale("%s: %s", formatWithLocale("Missing node ids in '%s' list ", key), exception.getMessage());
            throw new RuntimeException(new IllegalArgumentException(message));
        }
    }

    final SimilarityConfig config;
    final GraphDatabaseAPI api;

    public SimilarityAlgorithm(
        SimilarityConfig config,
        GraphDatabaseAPI api,
        ProgressTracker progressTracker
    ) {
        super(progressTracker);
        this.config = config;
        this.api = api;
    }

    abstract INPUT[] prepareInputs(Object rawData, SimilarityConfig config);

    abstract SimilarityComputer<INPUT> similarityComputer(
        Double skipValue,
        int[] sourceIndexIds,
        int[] targetIndexIds
    );

    abstract Supplier<RleDecoder> inputDecoderFactory(INPUT[] inputs);

    @Override
    public SimilarityAlgorithmResult compute() {
        ImmutableSimilarityAlgorithmResult.Builder builder = ImmutableSimilarityAlgorithmResult.builder();

        INPUT[] inputs = prepareInputs(config.data(), config);
        long[] inputIds = SimilarityInput.extractInputIds(inputs, config.concurrency());
        int[] sourceIndexIds = indexesFor(inputIds, config.sourceIds(), "sourceIds");
        int[] targetIndexIds = indexesFor(inputIds, config.targetIds(), "targetIds");
        SimilarityComputer<INPUT> computer = similarityComputer(config.skipValue(), sourceIndexIds, targetIndexIds);

        builder.nodes(inputIds.length)
            .sourceIdsLength(sourceIndexIds.length)
            .targetIdsLength(targetIndexIds.length);

        if (inputs.length == 0) {
            return builder
                .stream(Stream.empty())
                .isEmpty(true)
                .build();
        }

        if (config.showComputations()) {
            SimilarityRecorder<INPUT> recorder = new SimilarityRecorder<>(computer);
            builder.computations(recorder);
            computer = recorder;
        }

        Stream<SimilarityResult> resultStream = generateWeightedStream(
            inputs,
            sourceIndexIds,
            targetIndexIds,
            config.normalizedSimilarityCutoff(),
            config.normalizedTopN(),
            config.normalizedTopK(),
            computer
        );

        return builder
            .stream(resultStream)
            .isEmpty(false)
            .build();
    }

    @Override
    public void release() {}

    SimilarityResult modifyResult(SimilarityResult result) {
        return result;
    }

    Stream<SimilarityResult> generateWeightedStream(
        INPUT[] inputs,
        int[] sourceIndexIds,
        int[] targetIndexIds,
        double similarityCutoff,
        int topN,
        int topK,
        SimilarityComputer<INPUT> computer
    ) {
        Supplier<RleDecoder> decoderFactory = inputDecoderFactory(inputs);
        return topN(
            similarityStream(
                inputs,
                sourceIndexIds,
                targetIndexIds,
                computer,
                decoderFactory,
                similarityCutoff,
                topK
            ),
            topN
        ).map(this::modifyResult);
    }

    protected Supplier<RleDecoder> createDecoderFactory(int size) {
        if (ProcedureConstants.CYPHER_QUERY_KEY.equals(config.graph())) {
            return () -> new RleDecoder(size);
        }
        return () -> null;
    }

    public static Stream<SimilarityResult> topN(Stream<SimilarityResult> stream, int topN) {
        if (topN == 0) {
            return stream;
        }
        Comparator<SimilarityResult> comparator = topN > 0 ? SimilarityResult.DESCENDING : SimilarityResult.ASCENDING;
        topN = Math.abs(topN);

        if (topN > 10000) {
            return stream.sorted(comparator).limit(topN);
        }
        return TopKConsumer.topK(stream, topN, comparator);
    }

    protected Stream<SimilarityResult> similarityStream(
        INPUT[] inputs,
        int[] sourceIndexIds,
        int[] targetIndexIds,
        SimilarityComputer<INPUT> computer,
        Supplier<RleDecoder> decoderFactory,
        double cutoff,
        int topK
    ) {
        SimilarityStreamGenerator<INPUT> generator = new SimilarityStreamGenerator<>(
            terminationFlag,
            config.concurrency(),
            decoderFactory,
            computer
        );
        if (sourceIndexIds.length == 0 && targetIndexIds.length == 0) {
            return generator.stream(inputs, cutoff, topK);
        } else {
            return generator.stream(inputs, sourceIndexIds, targetIndexIds, cutoff, topK);
        }
    }
}
