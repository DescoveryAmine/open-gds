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
package org.neo4j.gds.similarity.nodesim;

import com.carrotsearch.hppc.BitSet;
import org.neo4j.gds.Algorithm;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.RelationshipConsumer;
import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.utils.BatchingProgressLogger;
import org.neo4j.gds.core.utils.Intersections;
import org.neo4j.gds.core.utils.SetBitsIterable;
import org.neo4j.gds.core.utils.paged.HugeObjectArray;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.gds.similarity.SimilarityGraphBuilder;
import org.neo4j.gds.similarity.SimilarityGraphResult;
import org.neo4j.gds.similarity.SimilarityResult;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class NodeSimilarity extends Algorithm<NodeSimilarityResult> {

    private final Graph graph;
    private final boolean sortVectors;
    private final NodeSimilarityBaseConfig config;

    private final ExecutorService executorService;

    private final BitSet nodeFilter;

    private HugeObjectArray<long[]> vectors;
    private HugeObjectArray<double[]> weights;
    private long nodesToCompare;

    private final boolean weighted;

    public NodeSimilarity(
        Graph graph,
        NodeSimilarityBaseConfig config,
        ExecutorService executorService,
        ProgressTracker progressTracker
    ) {
        super(progressTracker);
        this.graph = graph;
        this.sortVectors = graph.schema().relationshipSchema().availableTypes().size() > 1;
        this.config = config;
        this.executorService = executorService;
        this.nodeFilter = new BitSet(graph.nodeCount());
        this.weighted = config.hasRelationshipWeightProperty();
    }

    @Override
    public void release() {
        graph.release();
    }

    @Override
    public NodeSimilarityResult compute() {
        progressTracker.beginSubTask();
        if (config.computeToStream()) {
            var computeToStream = computeToStream();
            progressTracker.endSubTask();
            return ImmutableNodeSimilarityResult.of(
                Optional.of(computeToStream),
                Optional.empty()
            );
        } else {
            var computeToGraph = computeToGraph();
            progressTracker.endSubTask();
            return ImmutableNodeSimilarityResult.of(
                Optional.empty(),
                Optional.of(computeToGraph)
            );
        }
    }

    public Stream<SimilarityResult> computeToStream() {
        // Create a filter for which nodes to compare and calculate the neighborhood for each node
        prepare();
        assertRunning();

        // Compute similarities
        if (config.hasTopN() && !config.hasTopK()) {
            // Special case: compute topN without topK.
            // This can not happen when algo is called from proc.
            // Ignore parallelism, always run single threaded,
            // but run on primitives.
            return computeTopN();
        } else {
            return config.isParallel()
                ? computeParallel()
                : computeSimilarityResultStream();
        }
    }

    public SimilarityGraphResult computeToGraph() {
        Graph similarityGraph;
        boolean isTopKGraph = false;

        if (config.hasTopK() && !config.hasTopN()) {
            prepare();
            assertRunning();

            TopKMap topKMap = config.isParallel()
                ? computeTopKMapParallel()
                : computeTopKMap();

            isTopKGraph = true;
            similarityGraph = new TopKGraph(graph, topKMap);
        } else {
            Stream<SimilarityResult> similarities = computeToStream();
            similarityGraph = new SimilarityGraphBuilder(
                graph,
                config.concurrency(),
                executorService
            ).build(similarities);
        }
        return new SimilarityGraphResult(similarityGraph, nodesToCompare, isTopKGraph);
    }

    private void prepare() {
        progressTracker.beginSubTask();

        vectors = HugeObjectArray.newArray(long[].class, graph.nodeCount());
        if (weighted) {
            weights = HugeObjectArray.newArray(double[].class, graph.nodeCount());
        }

        DegreeComputer degreeComputer = new DegreeComputer();
        VectorComputer vectorComputer = VectorComputer.of(graph, weighted);
        vectors.setAll(node -> {
            graph.forEachRelationship(node, degreeComputer);
            int degree = degreeComputer.degree;
            degreeComputer.reset();
            vectorComputer.reset(degree);

            if (degree >= config.degreeCutoff()) {
                nodesToCompare++;
                nodeFilter.set(node);

                progressTracker.logProgress(graph.degree(node));
                vectorComputer.forEachRelationship(node);
                if (weighted) {
                    weights.set(node, vectorComputer.getWeights());
                }
                if (sortVectors) {
                    Arrays.sort(vectorComputer.targetIds.buffer);
                }
                return vectorComputer.targetIds.buffer;
            }

            progressTracker.logProgress(graph.degree(node));
            return null;
        });
        progressTracker.endSubTask();
    }

    private Stream<SimilarityResult> computeSimilarityResultStream() {
        return (config.hasTopK() && config.hasTopN())
            ? computeTopN(computeTopKMap())
            : (config.hasTopK())
                ? computeTopKMap().stream()
                : computeAll();
    }

    private Stream<SimilarityResult> computeParallel() {
        return (config.hasTopK() && config.hasTopN())
            ? computeTopN(computeTopKMapParallel())
            : (config.hasTopK())
                ? computeTopKMapParallel().stream()
                : computeAllParallel();
    }

    private Stream<SimilarityResult> computeAll() {
        progressTracker.beginSubTask(calculateWorkload());

        var similarityResultStream = loggableAndTerminatableNodeStream()
            .boxed()
            .flatMap(this::computeSimilaritiesForNode);
        progressTracker.endSubTask();
        return similarityResultStream;
    }

    private Stream<SimilarityResult> computeAllParallel() {
        return ParallelUtil.parallelStream(
            loggableAndTerminatableNodeStream(), config.concurrency(), stream -> stream
                .boxed()
                .flatMap(this::computeSimilaritiesForNode)
        );
    }

    private TopKMap computeTopKMap() {
        progressTracker.beginSubTask(calculateWorkload());

        Comparator<SimilarityResult> comparator = config.normalizedK() > 0 ? SimilarityResult.DESCENDING : SimilarityResult.ASCENDING;
        TopKMap topKMap = new TopKMap(vectors.size(), nodeFilter, Math.abs(config.normalizedK()), comparator
        );
        loggableAndTerminatableNodeStream()
            .forEach(node1 -> {
                long[] vector1 = vectors.get(node1);
                nodeStream(node1 + 1)
                    .forEach(node2 -> {
                        double similarity = weighted
                            ? weightedJaccard(vector1, vectors.get(node2), weights.get(node1), weights.get(node2))
                            : jaccard(vector1, vectors.get(node2));
                        if (!Double.isNaN(similarity)) {
                            topKMap.put(node1, node2, similarity);
                            topKMap.put(node2, node1, similarity);
                        }
                    });
            });
        progressTracker.endSubTask();
        return topKMap;
    }

    private TopKMap computeTopKMapParallel() {
        progressTracker.beginSubTask(calculateWorkload());

        Comparator<SimilarityResult> comparator = config.normalizedK() > 0 ? SimilarityResult.DESCENDING : SimilarityResult.ASCENDING;
        TopKMap topKMap = new TopKMap(vectors.size(), nodeFilter, Math.abs(config.normalizedK()), comparator
        );
        ParallelUtil.parallelStreamConsume(
            loggableAndTerminatableNodeStream(),
            config.concurrency(),
            stream -> stream
                .forEach(node1 -> {
                    long[] vector1 = vectors.get(node1);
                    // We deliberately compute the full matrix (except the diagonal).
                    // The parallel workload is partitioned based on the outer stream.
                    // The TopKMap stores a priority queue for each node. Writing
                    // into these queues is not considered to be thread-safe.
                    // Hence, we need to ensure that down the stream, exactly one queue
                    // within the TopKMap processes all pairs for a single node.
                    nodeStream()
                        .filter(node2 -> node1 != node2)
                        .forEach(node2 -> {
                            double similarity = weighted
                                ? weightedJaccard(vector1, vectors.get(node2), weights.get(node1), weights.get(node2))
                                : jaccard(vector1, vectors.get(node2));
                            if (!Double.isNaN(similarity)) {
                                topKMap.put(node1, node2, similarity);
                            }
                        });
                })
        );

        progressTracker.endSubTask();
        return topKMap;
    }

    private Stream<SimilarityResult> computeTopN() {
        progressTracker.beginSubTask(calculateWorkload());

        TopNList topNList = new TopNList(config.normalizedN());
        loggableAndTerminatableNodeStream()
            .forEach(node1 -> {
                long[] vector1 = vectors.get(node1);

                nodeStream(node1 + 1)
                    .forEach(node2 -> {
                        double similarity = weighted
                            ? weightedJaccard(vector1, vectors.get(node2), weights.get(node1), weights.get(node2))
                            : jaccard(vector1, vectors.get(node2));
                        if (!Double.isNaN(similarity)) {
                            topNList.add(node1, node2, similarity);
                        }
                    });
            });

        progressTracker.endSubTask();
        return topNList.stream();
    }

    private Stream<SimilarityResult> computeTopN(TopKMap topKMap) {
        TopNList topNList = new TopNList(config.normalizedN());
        topKMap.forEach(topNList::add);
        return topNList.stream();
    }

    private double jaccard(long[] vector1, long[] vector2) {
        long intersection = Intersections.intersection3(vector1, vector2);
        double union = vector1.length + vector2.length - intersection;
        double similarity = union == 0 ? 0 : intersection / union;
        progressTracker.logProgress();
        return similarity >= config.similarityCutoff() ? similarity : Double.NaN;

    }

    private double weightedJaccard(long[] vector1, long[] vector2, double[] weights1, double[] weights2) {
        assert vector1.length == weights1.length;
        assert vector2.length == weights2.length;

        int offset1 = 0;
        int offset2 = 0;
        int length1 = weights1.length;
        int length2 = weights2.length;
        double max = 0;
        double min = 0;
        while (offset1 < length1 && offset2 < length2) {
            long target1 = vector1[offset1];
            long target2 = vector2[offset2];
            if (target1 == target2) {
                double w1 = weights1[offset1];
                double w2 = weights2[offset2];
                if (w1 > w2) {
                    max += w1;
                    min += w2;
                } else {
                    min += w1;
                    max += w2;
                }
                offset1++;
                offset2++;
            } else if (target1 < target2) {
                max += weights1[offset1];
                offset1++;
            } else {
                max += weights2[offset2];
                offset2++;
            }
        }
        for (; offset1 < length1; offset1++) {
            max += weights1[offset1];
        }
        for (; offset2 < length2; offset2++) {
            max += weights2[offset2];
        }
        double similarity = min / max;
        progressTracker.logProgress();
        return similarity >= config.similarityCutoff() ? similarity : Double.NaN;
    }

    private LongStream nodeStream() {
        return nodeStream(0);
    }

    private LongStream loggableAndTerminatableNodeStream() {
        return checkProgress(nodeStream());
    }

    private LongStream checkProgress(LongStream stream) {
        return stream.peek(node -> {
            if ((node & BatchingProgressLogger.MAXIMUM_LOG_INTERVAL) == 0) {
                assertRunning();
            }
        });
    }

    private LongStream nodeStream(long offset) {
        return new SetBitsIterable(nodeFilter, offset).stream();
    }

    private long calculateWorkload() {
        long workload = nodesToCompare * nodesToCompare;
        if (config.concurrency() == 1) {
            workload = workload / 2;
        }
        return workload;
    }

    private Stream<SimilarityResult> computeSimilaritiesForNode(long node1) {
        long[] vector1 = vectors.get(node1);
        return nodeStream(node1 + 1)
            .mapToObj(node2 -> {
                double similarity = weighted
                    ? weightedJaccard(vector1, vectors.get(node2), weights.get(node1), weights.get(node2))
                    : jaccard(vector1, vectors.get(node2));
                return Double.isNaN(similarity) ? null : new SimilarityResult(
                    node1,
                    node2,
                    similarity
                );
            })
            .filter(Objects::nonNull);
    }

    private static final class DegreeComputer implements RelationshipConsumer {

        long lastTarget = -1;
        int degree = 0;

        @Override
        public boolean accept(long source, long target) {
            if (source != target && lastTarget != target) {
                degree++;
            }
            lastTarget = target;
            return true;
        }

        void reset() {
            lastTarget = -1;
            degree = 0;
        }
    }
}
