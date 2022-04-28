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

import org.immutables.value.Value;
import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.annotation.Configuration;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.config.WritePropertyConfig;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface SimilarityConfig extends AlgoBaseConfig, WritePropertyConfig {

    int TOP_K_DEFAULT = 3;

    int TOP_N_DEFAULT = 0;

    @Value.Default
    default @Nullable Double skipValue() {
        return Double.NaN;
    }

    @Value.Default
    default String graph() {
        return "dense";
    }

    @Value.Default
    default Object data() {
        return Collections.emptyList();
    }

    @Value.Default
    default Map<String, Object> params() {
        return Collections.emptyMap();
    }

    @Value.Default
    default long degreeCutoff() {
        return 0;
    }

    @Value.Default
    default double similarityCutoff() {
        return -1D;
    }

    @Value.Derived
    @Configuration.Ignore
    default double normalizedSimilarityCutoff() {
        return similarityCutoff();
    }

    @Value.Default
    default int sparseVectorRepeatCutoff() {
        return 3;
    }

    @Value.Default
    default List<Long> sourceIds() {
        return Collections.emptyList();
    }

    @Value.Default
    default List<Long> targetIds() {
        return Collections.emptyList();
    }

    @Value.Default
    default int top() {
        return TOP_N_DEFAULT;
    }

    @Value.Derived
    @Configuration.Ignore
    default int normalizedTopN() {
        return top();
    }

    @Value.Default
    default int topK() {
        return TOP_K_DEFAULT;
    }

    @Value.Derived
    @Configuration.Ignore
    default int normalizedTopK() {
        return topK();
    }

    @Value.Default
    default boolean showComputations() {
        return false;
    }

    @Value.Default
    default String writeRelationshipType() {
        return "SIMILAR";
    }

    @Value.Default
    default String writeProperty() {
        return "score";
    }

    @Value.Default
    default long writeBatchSize() {
        return 10_000L;
    }
}
