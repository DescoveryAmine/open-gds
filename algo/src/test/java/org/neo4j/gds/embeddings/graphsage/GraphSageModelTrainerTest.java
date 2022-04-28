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
package org.neo4j.gds.embeddings.graphsage;

import com.carrotsearch.hppc.LongHashSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.assertj.core.data.Offset;
import org.assertj.core.util.DoubleComparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.compat.Neo4jProxy;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.utils.paged.HugeObjectArray;
import org.neo4j.gds.core.utils.partition.PartitionUtils;
import org.neo4j.gds.core.utils.progress.EmptyTaskRegistryFactory;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSageTrainAlgorithmFactory;
import org.neo4j.gds.embeddings.graphsage.algo.GraphSageTrainConfig;
import org.neo4j.gds.embeddings.graphsage.algo.ImmutableGraphSageTrainConfig;
import org.neo4j.gds.extension.GdlExtension;
import org.neo4j.gds.extension.GdlGraph;
import org.neo4j.gds.extension.Inject;
import org.neo4j.gds.ml.core.AbstractVariable;
import org.neo4j.gds.ml.core.Dimensions;
import org.neo4j.gds.ml.core.helper.TensorTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.neo4j.gds.assertj.Extractors.removingThreadId;
import static org.neo4j.gds.compat.TestLog.INFO;
import static org.neo4j.gds.embeddings.graphsage.Aggregator.AggregatorType;
import static org.neo4j.gds.embeddings.graphsage.GraphSageTestGraph.DUMMY_PROPERTY;

@GdlExtension
class GraphSageModelTrainerTest {

    private final int FEATURES_COUNT = 5;
    private final int EMBEDDING_DIMENSION = 64;

    @SuppressFBWarnings("HSC_HUGE_SHARED_STRING_CONSTANT")
    @GdlGraph
    private static final String GDL = GraphSageTestGraph.GDL;

    @SuppressFBWarnings
    @GdlGraph(orientation = Orientation.UNDIRECTED, graphNamePrefix = "array")
    private static final String ARRAY_GRAPH = "CREATE" +
                                              "  (a { features: [-1.0, 2.1] })" +
                                              ", (b { features: [4.2, -1.6] })" +
                                              ", (a)-[:REL]->(b)";

    private final String MODEL_NAME = "graphSageModel";

    @Inject
    private Graph graph;
    @Inject
    private Graph arrayGraph;
    private HugeObjectArray<double[]> features;
    private ImmutableGraphSageTrainConfig.Builder configBuilder;


    @BeforeEach
    void setUp() {
        long nodeCount = graph.nodeCount();
        features = HugeObjectArray.newArray(double[].class, nodeCount);

        Random random = new Random(19L);
        LongStream.range(0, nodeCount).forEach(n -> features.set(n, random.doubles(FEATURES_COUNT).toArray()));
        configBuilder = ImmutableGraphSageTrainConfig.builder()
            .featureProperties(Collections.nCopies(FEATURES_COUNT, "dummyProp"))
            .embeddingDimension(EMBEDDING_DIMENSION);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void trainsWithMeanAggregator(boolean useRelationshipWeight) {
        if (useRelationshipWeight) {
            configBuilder.relationshipWeightProperty("times");
        }
        var config = configBuilder
            .aggregator(AggregatorType.MEAN)
            .modelName(MODEL_NAME)
            .build();

        var trainModel = new GraphSageModelTrainer(config, Pools.DEFAULT, ProgressTracker.NULL_TRACKER);

        GraphSageModelTrainer.ModelTrainResult result = trainModel.train(graph, features);

        Layer[] layers = result.layers();
        assertThat(layers)
            .hasSize(2)
            .allSatisfy(layer -> assertThat(layer.weights())
                .hasSize(1)
                .noneMatch(weights -> TensorTestUtils.containsNaN(weights.data())));

        // First layer is (embeddingDimension x features.length)
        assertThat(layers[0].weights())
            .map(AbstractVariable::dimensions)
            .containsExactly(Dimensions.matrix(EMBEDDING_DIMENSION, FEATURES_COUNT));

        // Second layer weights (embeddingDimension x embeddingDimension)
        assertThat(layers[1].weights())
            .map(AbstractVariable::dimensions)
            .containsExactly(Dimensions.matrix(EMBEDDING_DIMENSION, EMBEDDING_DIMENSION));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void trainsWithPoolAggregator(boolean useRelationshipWeight) {
        if (useRelationshipWeight) {
            configBuilder.relationshipWeightProperty("times");
        }
        var config = configBuilder
            .aggregator(AggregatorType.POOL)
            .modelName(MODEL_NAME)
            .build();

        var trainModel = new GraphSageModelTrainer(config, Pools.DEFAULT, ProgressTracker.NULL_TRACKER);

        GraphSageModelTrainer.ModelTrainResult result = trainModel.train(graph, features);
        Layer[] layers = result.layers();

        assertThat(layers)
            .hasSize(2)
            .allSatisfy(layer -> assertThat(layer.weights())
                .hasSize(4)
                .noneMatch(weights -> TensorTestUtils.containsNaN(weights.data()))
            );

        var firstWeights = layers[0].weights();
        var firstLayerPoolWeights = firstWeights.get(0).dimensions();
        var firstLayerSelfWeights = firstWeights.get(1).dimensions();
        var firstLayerNeighborsWeights = firstWeights.get(2).dimensions();
        var firstLayerBias = firstWeights.get(3).dimensions();

        assertThat(firstLayerPoolWeights).containsExactly(Dimensions.matrix(EMBEDDING_DIMENSION, FEATURES_COUNT));
        assertThat(firstLayerSelfWeights).containsExactly(Dimensions.matrix(EMBEDDING_DIMENSION, FEATURES_COUNT));
        assertThat(firstLayerNeighborsWeights).containsExactly(Dimensions.matrix(EMBEDDING_DIMENSION, EMBEDDING_DIMENSION));
        assertThat(firstLayerBias).containsExactly(Dimensions.vector(EMBEDDING_DIMENSION));

        var secondWeights = layers[1].weights();
        var secondLayerPoolWeights = secondWeights.get(0).dimensions();
        var secondLayerSelfWeights = secondWeights.get(1).dimensions();
        var secondLayerNeighborsWeights = secondWeights.get(2).dimensions();
        var secondLayerBias = secondWeights.get(3).dimensions();

        assertThat(secondLayerPoolWeights).containsExactly(Dimensions.matrix(EMBEDDING_DIMENSION, EMBEDDING_DIMENSION));
        assertThat(secondLayerSelfWeights).containsExactly(Dimensions.matrix(EMBEDDING_DIMENSION, EMBEDDING_DIMENSION));
        assertThat(secondLayerNeighborsWeights).containsExactly(Dimensions.matrix(EMBEDDING_DIMENSION, EMBEDDING_DIMENSION));
        assertThat(secondLayerBias).containsExactly(Dimensions.vector(EMBEDDING_DIMENSION));
    }

    @Test
    void testLogging() {
        var config = ImmutableGraphSageTrainConfig.builder()
            .addFeatureProperties(DUMMY_PROPERTY)
            .embeddingDimension(EMBEDDING_DIMENSION)
            .modelName("model")
            .epochs(2)
            .maxIterations(2)
            .tolerance(1e-10)
            .learningRate(0.001)
            .randomSeed(42L)
            .build();

        var log = Neo4jProxy.testLog();

        var algo = new GraphSageTrainAlgorithmFactory().build(
            graph,
            config,
            log,
            EmptyTaskRegistryFactory.INSTANCE
        );
        algo.compute();

        var messagesInOrder = log.getMessages(INFO);

        assertThat(messagesInOrder)
            // avoid asserting on the thread id
            .extracting(removingThreadId())
            .containsExactly(
                "GraphSageTrain :: Start",
                "GraphSageTrain :: train epoch 1 of 2 :: Start",
                "GraphSageTrain :: train epoch 1 of 2 :: iteration 1 of 2 :: Start",
                "GraphSageTrain :: train epoch 1 of 2 :: iteration 1 of 2 :: LOSS: 531.5699087433",
                "GraphSageTrain :: train epoch 1 of 2 :: iteration 1 of 2 100%",
                "GraphSageTrain :: train epoch 1 of 2 :: iteration 1 of 2 :: Finished",
                "GraphSageTrain :: train epoch 1 of 2 :: iteration 2 of 2 :: Start",
                "GraphSageTrain :: train epoch 1 of 2 :: iteration 2 of 2 100%",
                "GraphSageTrain :: train epoch 1 of 2 :: iteration 2 of 2 :: Finished",
                "GraphSageTrain :: train epoch 1 of 2 :: Finished",
                "GraphSageTrain :: train epoch 2 of 2 :: Start",
                "GraphSageTrain :: train epoch 2 of 2 :: iteration 1 of 2 :: Start",
                "GraphSageTrain :: train epoch 2 of 2 :: iteration 1 of 2 100%",
                "GraphSageTrain :: train epoch 2 of 2 :: iteration 1 of 2 :: Finished",
                "GraphSageTrain :: train epoch 2 of 2 :: Finished",
                "GraphSageTrain :: Finished"
            );
    }

    @Test
    void shouldTrainModelWithArrayProperties() {
        var arrayFeatures = HugeObjectArray.newArray(double[].class, arrayGraph.nodeCount());
        LongStream
            .range(0, arrayGraph.nodeCount())
            .forEach(n -> arrayFeatures.set(n, arrayGraph.nodeProperties("features").doubleArrayValue(n)));
        var config = GraphSageTrainConfig.testBuilder()
            .embeddingDimension(12)
            .aggregator(AggregatorType.MEAN)
            .activationFunction(ActivationFunction.SIGMOID)
            .addFeatureProperty("features")
            .modelName("model")
            .build();

        var trainer = new GraphSageModelTrainer(config, Pools.DEFAULT, ProgressTracker.NULL_TRACKER);

        var result = trainer.train(arrayGraph, arrayFeatures);

        assertThat(result.layers())
            .allSatisfy(layer -> assertThat(layer.weights())
                .noneMatch(weights -> TensorTestUtils.containsNaN(weights.data()))
            );
    }

    @Test
    void testLosses() {
        var config = configBuilder
            .modelName("randomSeed2")
            .embeddingDimension(12)
            .epochs(10)
            .tolerance(1e-10)
            .addSampleSizes(5, 3)
            .batchSize(5)
            .maxIterations(100)
            .randomSeed(42L)
            .build();

        var trainer = new GraphSageModelTrainer(
            config,
            Pools.DEFAULT,
            ProgressTracker.NULL_TRACKER
        );

        var trainResult = trainer.train(graph, features);

        var metrics = trainResult.metrics();
        assertThat(metrics.didConverge()).isFalse();
        assertThat(metrics.ranEpochs()).isEqualTo(10);

        var metricsMap =  metrics.toMap().get("metrics");
        assertThat(metricsMap).isInstanceOf(Map.class);

        var epochLosses = ((Map<String, Object>) metricsMap).get("epochLosses");
        assertThat(epochLosses).isInstanceOf(List.class);
        assertThat(((List<Double>) epochLosses).stream().mapToDouble(Double::doubleValue).toArray())
            .contains(new double[]{
                    91.33327272,
                    88.17940500,
                    87.68340477,
                    85.60797746,
                    85.59108701,
                    85.59007234,
                    81.44403525,
                    81.44260858,
                    81.44349342,
                    81.45612978
                }, Offset.offset(1e-8)
            );
    }

    @Test
    void testLossesWithPoolAggregator() {
        var config = configBuilder
            .modelName("randomSeed2")
            .embeddingDimension(12)
            .aggregator(AggregatorType.POOL)
            .epochs(10)
            .tolerance(1e-10)
            .addSampleSizes(5, 3)
            .batchSize(5)
            .maxIterations(100)
            .randomSeed(42L)
            .build();

        var trainer = new GraphSageModelTrainer(
            config,
            Pools.DEFAULT,
            ProgressTracker.NULL_TRACKER
        );

        var trainResult = trainer.train(graph, features);

        var metrics = trainResult.metrics();
        assertThat(metrics.didConverge()).isFalse();
        assertThat(metrics.ranEpochs()).isEqualTo(10);

        var metricsMap =  metrics.toMap().get("metrics");
        assertThat(metricsMap).isInstanceOf(Map.class);

        var epochLosses = ((Map<String, Object>) metricsMap).get("epochLosses");
        assertThat(epochLosses).isInstanceOf(List.class);
        assertThat(((List<Double>) epochLosses).stream().mapToDouble(Double::doubleValue).toArray())
            .contains(new double[]{
                    90.53,
                    83.29,
                    74.75,
                    74.61,
                    74.68,
                    74.54,
                    74.46,
                    74.47,
                    74.41,
                    74.41
                }, Offset.offset(0.05)
            );
    }

    @Test
    void testConvergence() {
        var trainer = new GraphSageModelTrainer(
            configBuilder.modelName("convergingModel:)").tolerance(100.0).epochs(10).build(),
            Pools.DEFAULT,
            ProgressTracker.NULL_TRACKER
        );

        var trainResult = trainer.train(graph, features);

        var trainMetrics = trainResult.metrics();
        assertThat(trainMetrics.didConverge()).isTrue();
        assertThat(trainMetrics.ranEpochs()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(longs = {20L, -100L, 30L})
    void seededSingleBatch(long seed) {
        var config = configBuilder
            .modelName("randomSeed")
            .embeddingDimension(12)
            .randomSeed(seed)
            .concurrency(1)
            .build();

        var trainer = new GraphSageModelTrainer(config, Pools.DEFAULT, ProgressTracker.NULL_TRACKER);
        var otherTrainer = new GraphSageModelTrainer(config, Pools.DEFAULT, ProgressTracker.NULL_TRACKER);

        var result = trainer.train(graph, features);
        var otherResult = otherTrainer.train(graph, features);

        assertThat(result).usingRecursiveComparison().isEqualTo(otherResult);
    }

    @ParameterizedTest
    @ValueSource(longs = {20L, -100L, 30L})
    void seededMultiBatch(long seed) {
        var config = configBuilder
            .modelName("randomSeed")
            .embeddingDimension(12)
            .randomSeed(seed)
            .concurrency(1)
            .batchSize(5)
            .build();

        var trainer = new GraphSageModelTrainer(config, Pools.DEFAULT, ProgressTracker.NULL_TRACKER);
        var otherTrainer = new GraphSageModelTrainer(config, Pools.DEFAULT, ProgressTracker.NULL_TRACKER);

        var result = trainer.train(graph, features);
        var otherResult = otherTrainer.train(graph, features);

        // Needs deterministic weights updates
        assertThat(result).usingRecursiveComparison().withComparatorForType(new DoubleComparator(1e-10), Double.class).isEqualTo(otherResult);
    }

    @Test
    void seededNeighborBatch() {
        var batchSize = 5;
        var seed = 20L;
        var config = configBuilder
            .modelName("randomSeed")
            .embeddingDimension(12)
            .randomSeed(seed)
            .batchSize(batchSize)
            .build();

        var trainer = new GraphSageModelTrainer(config, Pools.DEFAULT, ProgressTracker.NULL_TRACKER);
        var otherTrainer = new GraphSageModelTrainer(config, Pools.DEFAULT, ProgressTracker.NULL_TRACKER);

        var partitions = PartitionUtils.rangePartitionWithBatchSize(
            graph.nodeCount(),
            batchSize,
            Function.identity()
        );

        for (int i = 0; i < partitions.size(); i++) {
            var localSeed = i + seed;
            var neighborBatch = trainer.neighborBatch(graph, partitions.get(i), localSeed);
            var otherNeighborBatch = otherTrainer.neighborBatch(graph, partitions.get(i), localSeed);
            assertThat(neighborBatch).containsExactlyElementsOf(otherNeighborBatch.boxed().collect(Collectors.toList()));
        }
    }

    @Test
    void seededNegativeBatch() {
        var batchSize = 5;
        var seed = 20L;
        var config = configBuilder
            .modelName("randomSeed")
            .embeddingDimension(12)
            .randomSeed(seed)
            .batchSize(batchSize)
            .build();

        var trainer = new GraphSageModelTrainer(config, Pools.DEFAULT, ProgressTracker.NULL_TRACKER);
        var otherTrainer = new GraphSageModelTrainer(config, Pools.DEFAULT, ProgressTracker.NULL_TRACKER);

        var partitions = PartitionUtils.rangePartitionWithBatchSize(
            graph.nodeCount(),
            batchSize,
            Function.identity()
        );

        var neighborsSet = new LongHashSet(5);
        neighborsSet.addAll(0, 3, 5, 6, 10);

        for (int i = 0; i < partitions.size(); i++) {
            var localSeed = i + seed;
            var negativeBatch = trainer.negativeBatch(graph, Math.toIntExact(partitions.get(i).nodeCount()), neighborsSet, localSeed);
            var otherNegativeBatch = otherTrainer.negativeBatch(graph, Math.toIntExact(partitions.get(i).nodeCount()), neighborsSet, localSeed);

            assertThat(negativeBatch).containsExactlyElementsOf(otherNegativeBatch.boxed().collect(Collectors.toList()));
        }
    }
}
