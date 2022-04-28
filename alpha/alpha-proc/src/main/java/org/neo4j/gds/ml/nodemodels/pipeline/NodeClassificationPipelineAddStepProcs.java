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
package org.neo4j.gds.ml.nodemodels.pipeline;

import org.neo4j.gds.BaseProc;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

import static org.neo4j.procedure.Mode.READ;

public class NodeClassificationPipelineAddStepProcs extends BaseProc {

    @Procedure(name = "gds.beta.pipeline.nodeClassification.addNodeProperty", mode = READ)
    @Description("Add a node property step to an existing node classification training pipeline.")
    public Stream<PipelineInfoResult> addNodeProperty(
        @Name("pipelineName") String pipelineName,
        @Name("procedureName") String taskName,
        @Name("procedureConfiguration") Map<String, Object> procedureConfig
    ) {
        return Stream.of(NodeClassificationPipelineAddSteps.addNodeProperty(
            username(),
            pipelineName,
            taskName,
            procedureConfig
        ));
    }

    @Procedure(name = "gds.beta.pipeline.nodeClassification.selectFeatures", mode = READ)
    @Description("Add one or several features to an existing node classification training pipeline.")
    public Stream<PipelineInfoResult> selectFeatures(
        @Name("pipelineName") String pipelineName,
        @Name("nodeProperties") Object nodeProperties
    ) {
        return Stream.of(NodeClassificationPipelineAddSteps.selectFeatures(username(), pipelineName, nodeProperties));
    }
}
