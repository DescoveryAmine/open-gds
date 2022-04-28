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
package org.neo4j.gds.ml.linkmodels.pipeline;

import org.neo4j.gds.BaseProc;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.ml.pipeline.PipelineCatalog;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkPredictionPipeline;
import org.neo4j.gds.models.logisticregression.LogisticRegressionTrainConfig;
import org.neo4j.gds.models.logisticregression.LogisticRegressionTrainConfigImpl;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

public class LinkPredictionPipelineConfigureParamsProc extends BaseProc {

    @Procedure(name = "gds.beta.pipeline.linkPrediction.configureParams", mode = READ)
    @Description("Configures the parameters of the link prediction train pipeline.")
    public Stream<PipelineInfoResult> configureParams(@Name("pipelineName") String pipelineName, @Name("parameterSpace") List<Map<String, Object>> parameterSpace) {
        var pipeline = PipelineCatalog.getTyped(username(), pipelineName, LinkPredictionPipeline.class);

        List<LogisticRegressionTrainConfig> trainConfigs = parameterSpace
            .stream()
            .map(CypherMapWrapper::create)
            .map(rawConfig -> {
                var config = new LogisticRegressionTrainConfigImpl(rawConfig);
                validateConfig(rawConfig, config.configKeys());
                return config;
            })
            .collect(Collectors.toList());

        pipeline.setTrainingParameterSpace(trainConfigs);

        return Stream.of(new PipelineInfoResult(pipelineName, pipeline));
    }
}
