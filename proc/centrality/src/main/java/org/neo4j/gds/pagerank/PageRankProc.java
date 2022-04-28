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

import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.executor.ComputationResult;
import org.neo4j.gds.executor.validation.BeforeLoadValidation;
import org.neo4j.gds.executor.validation.ValidationConfiguration;
import org.neo4j.gds.result.AbstractCentralityResultBuilder;
import org.neo4j.internal.kernel.api.procs.ProcedureCallContext;
import org.neo4j.logging.Log;

import java.util.List;

final class PageRankProc {

    static final String PAGE_RANK_DESCRIPTION =
        "Page Rank is an algorithm that measures the transitive influence or connectivity of nodes.";

    static final String ARTICLE_RANK_DESCRIPTION =
        "Article Rank is a variant of the Page Rank algorithm, which " +
        "measures the transitive influence or connectivity of nodes.";

    static final String EIGENVECTOR_DESCRIPTION =
        "Eigenvector Centrality is an algorithm that measures the transitive influence or connectivity of nodes.";

    private PageRankProc() {}

    static <PROC_RESULT, CONFIG extends PageRankConfig> PageRankResultBuilder<PROC_RESULT> resultBuilder(
        PageRankResultBuilder<PROC_RESULT> procResultBuilder,
        ComputationResult<PageRankAlgorithm, PageRankResult, CONFIG> computeResult
    ) {
        var result = computeResult.result();
        procResultBuilder
            .withDidConverge(!computeResult.isGraphEmpty() && result.didConverge())
            .withRanIterations(!computeResult.isGraphEmpty() ? result.iterations() : 0)
            .withCentralityFunction(!computeResult.isGraphEmpty() ? computeResult.result().scores()::get : null)
            .withScalerVariant(computeResult.config().scaler());

        return procResultBuilder;
    }

    static <CONFIG extends PageRankConfig> NodeProperties nodeProperties(ComputationResult<PageRankAlgorithm, PageRankResult, CONFIG> computeResult) {
        return computeResult.result().scores().asNodeProperties();
    }

    static <CONFIG extends PageRankConfig> ValidationConfiguration<CONFIG> getValidationConfig(Log log) {
        return new ValidationConfiguration<>() {
            @Override
            public List<BeforeLoadValidation<CONFIG>> beforeLoadValidations() {
                return List.of(
                    (graphProjectConfig, config) -> {

                    }
                );
            }
        };
    }

    abstract static class PageRankResultBuilder<PROC_RESULT> extends AbstractCentralityResultBuilder<PROC_RESULT> {
        protected long ranIterations;

        protected boolean didConverge;

        PageRankResultBuilder(ProcedureCallContext callContext, int concurrency) {
            super(callContext, concurrency);
        }

        PageRankResultBuilder<PROC_RESULT> withRanIterations(long ranIterations) {
            this.ranIterations = ranIterations;
            return this;
        }

        PageRankResultBuilder<PROC_RESULT> withDidConverge(boolean didConverge) {
            this.didConverge = didConverge;
            return this;
        }
    }
}
