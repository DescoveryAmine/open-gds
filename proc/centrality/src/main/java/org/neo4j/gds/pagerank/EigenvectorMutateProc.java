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
package org.neo4j.gds.pagerank;

import org.neo4j.gds.GraphAlgorithmFactory;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.executor.GdsCallable;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.gds.executor.ExecutionMode.MUTATE_NODE_PROPERTY;
import static org.neo4j.procedure.Mode.READ;

@GdsCallable(name = "gds.eigenvector.mutate", description = PageRankProc.EIGENVECTOR_DESCRIPTION, executionMode = MUTATE_NODE_PROPERTY)
public class EigenvectorMutateProc extends PageRankMutateProc {

    @Override
    @Procedure(value = "gds.eigenvector.mutate", mode = READ)
    @Description(PageRankProc.EIGENVECTOR_DESCRIPTION)
    public Stream<MutateResult> mutate(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return super.mutate(graphName, configuration);
    }

    @Override
    @Procedure(value = "gds.eigenvector.mutate.estimate", mode = READ)
    @Description(ESTIMATE_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimate(
        @Name(value = "graphNameOrConfiguration") Object graphNameOrConfiguration,
        @Name(value = "algoConfiguration") Map<String, Object> algoConfiguration
    ) {
        return super.estimate(graphNameOrConfiguration, algoConfiguration);
    }

    @Override
    public GraphAlgorithmFactory<PageRankAlgorithm, PageRankMutateConfig> algorithmFactory() {
        return new PageRankAlgorithmFactory<>(PageRankAlgorithmFactory.Mode.EIGENVECTOR);
    }

    @Override
    protected PageRankMutateConfig newConfig(String username, CypherMapWrapper config) {
        if (config.containsKey("dampingFactor")) {
            throw new IllegalArgumentException("Unexpected configuration key: dampingFactor");
        }

        return super.newConfig(username, config);
    }
}
