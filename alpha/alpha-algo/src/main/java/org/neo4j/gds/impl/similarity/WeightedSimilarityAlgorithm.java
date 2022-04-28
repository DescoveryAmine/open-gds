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

import com.carrotsearch.hppc.LongDoubleHashMap;
import com.carrotsearch.hppc.LongDoubleMap;
import com.carrotsearch.hppc.LongHashSet;
import com.carrotsearch.hppc.LongSet;
import org.neo4j.gds.core.ProcedureConstants;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.graphdb.Result;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import static org.neo4j.gds.compat.GraphDatabaseApiProxy.applyInTransaction;
import static org.neo4j.gds.compat.GraphDatabaseApiProxy.runQueryWithoutClosingTheResult;
import static org.neo4j.gds.impl.utils.NumberUtils.getDoubleValue;
import static org.neo4j.gds.impl.utils.NumberUtils.getLongValue;
import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public abstract class WeightedSimilarityAlgorithm<ME extends WeightedSimilarityAlgorithm<ME>> extends SimilarityAlgorithm<ME, WeightedInput> {

    public WeightedSimilarityAlgorithm(SimilarityConfig config, GraphDatabaseAPI api) {
        super(config, api, ProgressTracker.NULL_TRACKER);
    }

    @Override
    WeightedInput[] prepareInputs(Object rawData, SimilarityConfig config) {
        Double skipValue = config.skipValue();
        if (ProcedureConstants.CYPHER_QUERY_KEY.equals(config.graph())) {
            return prepareSparseWeights(api, (String) rawData, skipValue);
        } else {
            List<Map<String, Object>> data = (List<Map<String, Object>>) rawData;
            return WeightedInput.prepareDenseWeights(data, config.degreeCutoff(), skipValue);
        }
    }

    private WeightedInput[] prepareSparseWeights(GraphDatabaseAPI api, String query, Double skipValue) {
        Map<String, Object> params = config.params();
        long degreeCutoff = config.degreeCutoff();
        int repeatCutoff = config.sparseVectorRepeatCutoff();

        return applyInTransaction(api, tx -> {
            Result result = runQueryWithoutClosingTheResult(tx, query, params);
            Map<Long, LongDoubleMap> map = new HashMap<>();
            LongSet ids = new LongHashSet();
            result.accept(resultRow -> {
                try {
                    long item = getLongValue(resultRow.getNumber("item"));
                    long id = getLongValue(resultRow.getNumber("category"));
                    double weight = getDoubleValue(resultRow.getNumber("weight"));
                    ids.add(id);
                    map.compute(item, (key, agg) -> {
                        if (agg == null) agg = new LongDoubleHashMap();
                        agg.put(id, weight);
                        return agg;
                    });
                } catch (NoSuchElementException nse) {
                    throw new IllegalArgumentException(formatWithLocale(
                        "Query %s does not return expected columns 'item', 'category' and 'weight'.",
                        query
                    ));
                }
                return true;
            });

            WeightedInput[] inputs = new WeightedInput[map.size()];
            int idx = 0;

            long[] idsArray = ids.toArray();
            for (Map.Entry<Long, LongDoubleMap> entry : map.entrySet()) {
                Long item = entry.getKey();
                LongDoubleMap sparseWeights = entry.getValue();

                if (sparseWeights.size() > degreeCutoff) {
                    var weightsList = new ArrayList<Double>(ids.size());
                    for (long id : idsArray) {
                        weightsList.add(sparseWeights.getOrDefault(id, skipValue));
                    }
                    int size = weightsList.size();
                    int nonSkipSize = sparseWeights.size();
                    double[] weights = Weights.buildRleWeights(weightsList, repeatCutoff);

                    inputs[idx++] = WeightedInput.sparse(item, weights, size, nonSkipSize);
                }
            }

            if (idx != inputs.length) inputs = Arrays.copyOf(inputs, idx);
            Arrays.sort(inputs);
            return inputs;
        });
    }

    @Override
    Supplier<RleDecoder> inputDecoderFactory(WeightedInput[] inputs) {
        return createDecoderFactory(inputs[0].initialSize());
    }

    protected Supplier<RleDecoder> createDecoderFactory(int size) {
        if (ProcedureConstants.CYPHER_QUERY_KEY.equals(config.graph())) {
            return () -> new RleDecoder(size);
        }
        return () -> null;
    }
}
