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
package org.neo4j.gds.beta.pregel;

import org.jetbrains.annotations.NotNull;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.utils.paged.HugeAtomicBitSet;
import org.neo4j.gds.core.utils.partition.Partition;
import org.neo4j.gds.core.utils.partition.PartitionUtils;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;

public class PartitionedComputer<CONFIG extends PregelConfig> extends PregelComputer<CONFIG> {
    private final ExecutorService executorService;
    private final int concurrency;

    private List<PartitionedComputeStep<CONFIG, ?>> computeSteps;

    PartitionedComputer(
        Graph graph,
        PregelComputation<CONFIG> computation,
        CONFIG config,
        NodeValue nodeValues,
        Messenger<?> messenger,
        HugeAtomicBitSet voteBits,
        int concurrency,
        ExecutorService executorService,
        ProgressTracker progressTracker
    ) {
        super(graph, computation, config, nodeValues, messenger, voteBits, progressTracker);
        this.executorService = executorService;
        this.concurrency = concurrency;
    }

    @Override
    public void initComputation() {
        this.computeSteps = createComputeSteps(voteBits);
    }

    @Override
    public void initIteration(int iteration) {
        for (var computeStep : computeSteps) {
            computeStep.init(iteration);
        }
    }

    @Override
    public void runIteration() {
        ParallelUtil.runWithConcurrency(concurrency, computeSteps, executorService);
    }

    @Override
    public boolean hasConverged() {
        // No messages have been sent and all nodes voted to halt
        var lastIterationSendMessages = computeSteps
            .stream()
            .anyMatch(PartitionedComputeStep::hasSentMessage);
        return !lastIterationSendMessages && voteBits.allSet();

    }

    @Override
    void release() {
        // Unlike in the sibling ForkJoinComputer, we will not shut down the
        // executor service (thread pool), since we use the shared global thread pool.
    }

    @NotNull
    private List<PartitionedComputeStep<CONFIG, ?>> createComputeSteps(HugeAtomicBitSet voteBits) {
        Function<Partition, PartitionedComputeStep<CONFIG, ?>> partitionFunction = partition -> new PartitionedComputeStep<>(
            graph.concurrentCopy(),
            computation,
            config,
            0,
            partition,
            nodeValues,
            messenger,
            voteBits,
            progressTracker
        );

        switch (config.partitioning()) {
            case RANGE:
                return PartitionUtils.rangePartition(
                    concurrency,
                    graph.nodeCount(),
                    partitionFunction,
                    Optional.empty()
                );
            case DEGREE:
                return PartitionUtils.degreePartition(
                    graph,
                    concurrency,
                    partitionFunction::apply,
                    Optional.empty()
                );
            default:
                throw new IllegalArgumentException(formatWithLocale(
                    "Unsupported partitioning `%s`",
                    config.partitioning()
                ));
        }
    }
}
