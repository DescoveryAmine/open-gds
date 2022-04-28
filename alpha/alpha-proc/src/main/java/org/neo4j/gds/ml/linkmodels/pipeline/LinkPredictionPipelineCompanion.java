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

import org.neo4j.gds.core.model.Model;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.executor.validation.BeforeLoadValidation;
import org.neo4j.gds.executor.validation.GraphProjectConfigValidations;
import org.neo4j.gds.executor.validation.ValidationConfiguration;
import org.neo4j.gds.models.logisticregression.LogisticRegressionTrainConfig;
import org.neo4j.gds.ml.linkmodels.pipeline.predict.LinkPredictionPredictPipelineBaseConfig;
import org.neo4j.gds.models.logisticregression.LogisticRegressionData;
import org.neo4j.gds.ml.pipeline.linkPipeline.LinkPredictionModelInfo;
import org.neo4j.gds.ml.pipeline.linkPipeline.train.LinkPredictionTrainConfig;

import java.util.List;
import java.util.Map;

public final class LinkPredictionPipelineCompanion {

    public static final String PREDICT_DESCRIPTION = "Predicts relationships for all node pairs based on a previously trained link prediction model.";
    public static final String ESTIMATE_PREDICT_DESCRIPTION = "Estimates memory for predicting links based on a previously trained pipeline model";
    static final List<Map<String, Object>> DEFAULT_PARAM_CONFIG = List.of(
        LogisticRegressionTrainConfig.defaultConfig().toMap()
    );

    private LinkPredictionPipelineCompanion() {}

    public static <CONFIG extends LinkPredictionPredictPipelineBaseConfig> ValidationConfiguration<CONFIG> getValidationConfig() {
        return new ValidationConfiguration<>() {
            @Override
            public List<BeforeLoadValidation<CONFIG>> beforeLoadValidations() {
                return List.of(new GraphProjectConfigValidations.UndirectedGraphValidation<>());
            }
        };
    }

    public static Model<LogisticRegressionData, LinkPredictionTrainConfig, LinkPredictionModelInfo> getTrainedLPPipelineModel(
        ModelCatalog modelCatalog,
        String pipelineName,
        String username
    ) {
        return modelCatalog.get(username, pipelineName, LogisticRegressionData.class, LinkPredictionTrainConfig.class, LinkPredictionModelInfo.class);
    }
}
