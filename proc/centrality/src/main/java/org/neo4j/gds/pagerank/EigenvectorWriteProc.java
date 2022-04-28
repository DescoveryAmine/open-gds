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

import static org.neo4j.gds.executor.ExecutionMode.WRITE_NODE_PROPERTY;
import static org.neo4j.procedure.Mode.READ;
import static org.neo4j.procedure.Mode.WRITE;

@GdsCallable(name = "gds.eigenvector.write", description = PageRankProc.EIGENVECTOR_DESCRIPTION, executionMode = WRITE_NODE_PROPERTY)
public class EigenvectorWriteProc extends PageRankWriteProc {

    @Override
    @Procedure(value = "gds.eigenvector.write", mode = WRITE)
    @Description(PageRankProc.EIGENVECTOR_DESCRIPTION)
    public Stream<WriteResult> write(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return super.write(graphName, configuration);
    }

    @Override
    @Procedure(value = "gds.eigenvector.write.estimate", mode = READ)
    @Description(ESTIMATE_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimate(
        @Name(value = "graphNameOrConfiguration") Object graphNameOrConfiguration,
        @Name(value = "algoConfiguration") Map<String, Object> algoConfiguration
    ) {
        return super.estimate(graphNameOrConfiguration, algoConfiguration);
    }

    @Override
    public GraphAlgorithmFactory<PageRankAlgorithm, PageRankWriteConfig> algorithmFactory() {
        return new PageRankAlgorithmFactory<>(PageRankAlgorithmFactory.Mode.EIGENVECTOR);
    }

    @Override
    protected PageRankWriteConfig newConfig(String username, CypherMapWrapper config) {
        if (config.containsKey("dampingFactor")) {
            throw new IllegalArgumentException("Unexpected configuration key: dampingFactor");
        }

        return super.newConfig(username, config);
    }
}
