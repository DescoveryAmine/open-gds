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

import org.immutables.value.Value;
import org.neo4j.gds.annotation.Configuration;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.config.SingleThreadedRandomSeedConfig;
import org.neo4j.gds.core.MissingParameterExceptions;
import org.neo4j.gds.model.ModelConfig;
import org.neo4j.gds.similarity.knn.ImmutableKnnBaseConfig;
import org.neo4j.gds.similarity.knn.KnnBaseConfig;
import org.neo4j.gds.utils.StringJoining;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

@Configuration
@SuppressWarnings("immutables:subtype")
public interface LinkPredictionPredictPipelineBaseConfig extends AlgoBaseConfig, SingleThreadedRandomSeedConfig, ModelConfig {

    double DEFAULT_THRESHOLD = 0.0;

    //TODO make this a parameter
    String graphName();

    @Value.Default
    @Configuration.DoubleRange(min = 0, max = 1, minInclusive = false)
    default double sampleRate() {
        return 1;
    }

    //Exhaustive strategy fields
    @Configuration.IntegerRange(min = 1)
    Optional<Integer> topN();

    @Configuration.DoubleRange(min = 0, max = 1)
    Optional<Double> threshold();

    //Approximate strategy fields
    @Configuration.IntegerRange(min = 1)
    Optional<Integer> topK();

    @Configuration.DoubleRange(min = 0, max = 1)
    Optional<Double> deltaThreshold();

    //We don't extend IterationsConfig because it is strategy-specific in this class
    @Configuration.IntegerRange(min = 1)
    Optional<Integer> maxIterations();

    @Configuration.IntegerRange(min = 0)
    Optional<Integer> randomJoins();

    @Value.Check
    default void validateParameterCombinations() {
        if (isApproximateStrategy()) {
            Map<String, Boolean> exhaustiveStrategyParameters = Map.of(
                "topN", topN().isPresent(),
                "threshold", threshold().isPresent()
            );
            validateStrategySpecificParameters(exhaustiveStrategyParameters, "equal to 1");
        } else {
            Map<String, Boolean> approximateStrategyParameters = Map.of(
                "topK",topK().isPresent(),
                "deltaThreshold", deltaThreshold().isPresent(),
                "maxIterations", maxIterations().isPresent(),
                "randomJoins", randomJoins().isPresent());
            validateStrategySpecificParameters(approximateStrategyParameters, "less than 1");

            topN().orElseThrow(()-> MissingParameterExceptions.missingValueFor("topN", Collections.emptyList()));
        }
    }

    @Configuration.Ignore
    default void validateStrategySpecificParameters(Map<String, Boolean> forbiddenParameters, String errorMsg) {
        var definedIllegalParameters = forbiddenParameters
            .entrySet()
            .stream()
            .filter(Map.Entry::getValue)
            .map(
                Map.Entry::getKey)
            .collect(Collectors.toList());
        if (!definedIllegalParameters.isEmpty()) {
            throw new IllegalArgumentException(formatWithLocale(
                "Configuration parameters %s may only be set if parameter 'sampleRate' is %s.",
                StringJoining.join(definedIllegalParameters), errorMsg
            ));
        }
    }

    @Configuration.Ignore
    @Value.Derived
    default KnnBaseConfig approximateConfig() {
        if (!isApproximateStrategy()) {
            throw new IllegalStateException(formatWithLocale(
                "Cannot derive approximateConfig when 'sampleRate' is 1.")
            );
        }
        var knnBuilder = ImmutableKnnBaseConfig.builder()
            .sampleRate(sampleRate())
            .nodeProperties(List.of("NotUsedInLP"))
            .concurrency(concurrency());

        topK().ifPresent(knnBuilder::topK);
        deltaThreshold().ifPresent(knnBuilder::deltaThreshold);
        maxIterations().ifPresent(knnBuilder::maxIterations);
        randomJoins().ifPresent(knnBuilder::randomJoins);
        randomSeed().ifPresent(knnBuilder::randomSeed);


        return knnBuilder.build();
    }

    @Configuration.Ignore
    @Value.Derived
    default double thresholdOrDefault() {
        return threshold().orElse(DEFAULT_THRESHOLD);
    }

    @Configuration.Ignore
    @Value.Derived
    default boolean isApproximateStrategy() {
        return sampleRate() < 1;
    }
}
