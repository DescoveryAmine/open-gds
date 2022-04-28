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
package org.neo4j.gds.ml.core.randomforest;

import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.utils.paged.HugeByteArray;
import org.neo4j.gds.core.utils.paged.HugeIntArray;
import org.neo4j.gds.core.utils.paged.HugeObjectArray;
import org.neo4j.gds.ml.core.decisiontree.ClassificationDecisionTreeTrain;
import org.neo4j.gds.ml.core.decisiontree.DecisionTreeLoss;
import org.neo4j.gds.ml.core.decisiontree.DecisionTreePredict;

import java.util.HashMap;
import java.util.Optional;

public class ClassificationRandomForestTrain<LOSS extends DecisionTreeLoss> {

    private final LOSS lossFunction;
    private final HugeObjectArray<double[]> allFeatureVectors;
    private final int maxDepth;
    private final int minSize;
    private final double numFeatureIndicesRatio;
    private final double numFeatureVectorsRatio;
    private final Optional<Long> randomSeed;
    private final int concurrency;
    private final int numDecisionTrees;
    private final int[] classes;
    private final HugeIntArray allLabels;

    public ClassificationRandomForestTrain(
        LOSS lossFunction,
        HugeObjectArray<double[]> allFeatureVectors,
        int maxDepth,
        int minSize,
        double numFeatureIndicesRatio,
        double numFeatureVectorsRatio,
        Optional<Long> randomSeed,
        int numDecisionTrees,
        int concurrency,
        int[] classes,
        HugeIntArray allLabels
    ) {
        this.lossFunction = lossFunction;
        this.allFeatureVectors = allFeatureVectors;
        this.maxDepth = maxDepth;
        this.minSize = minSize;
        this.numFeatureIndicesRatio = numFeatureIndicesRatio;
        this.numFeatureVectorsRatio = numFeatureVectorsRatio;
        this.randomSeed = randomSeed;
        this.concurrency = concurrency;
        this.numDecisionTrees = numDecisionTrees;
        this.classes = classes;
        this.allLabels = allLabels;
    }

    public ClassificationRandomForestTrainResult train() {
        var decisionTrees = new DecisionTreePredict[numDecisionTrees];
        var bootstrappedDatasets = new HugeByteArray[numDecisionTrees];

        var classToIdx = new HashMap<Integer, Integer>();
        for (int i = 0; i < classes.length; i++) {
            classToIdx.put(classes[i], i);
        }

        var tasks = ParallelUtil.tasks(numDecisionTrees, index -> () -> {
            var decisionTreeBuilder =
                new ClassificationDecisionTreeTrain.Builder<>(
                    lossFunction,
                    allFeatureVectors,
                    maxDepth,
                    classes,
                    allLabels,
                    classToIdx
                )
                    .withMinSize(minSize)
                    .withFeatureBaggingRatio(numFeatureIndicesRatio)
                    .withNumFeatureVectorsRatio(numFeatureVectorsRatio);

            randomSeed.ifPresent(seed -> {
                decisionTreeBuilder.withRandomSeed(seed + index);
            });

            var decisionTree = decisionTreeBuilder.build();
            decisionTrees[index] = decisionTree.train();
            bootstrappedDatasets[index] = decisionTree.bootstrappedDataset();
        });
        ParallelUtil.runWithConcurrency(this.concurrency, tasks, Pools.DEFAULT);

        return ImmutableClassificationRandomForestTrainResult.of(
            new ClassificationRandomForestPredict(decisionTrees, classes, classToIdx, concurrency),
            bootstrappedDatasets
        );
    }
}
