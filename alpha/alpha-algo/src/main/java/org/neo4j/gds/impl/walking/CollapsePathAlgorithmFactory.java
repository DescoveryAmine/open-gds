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
package org.neo4j.gds.impl.walking;

import org.neo4j.gds.GraphStoreAlgorithmFactory;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;

public class CollapsePathAlgorithmFactory extends GraphStoreAlgorithmFactory<CollapsePath, CollapsePathConfig> {

    @Override
    public CollapsePath build(
        GraphStore graphStore,
        CollapsePathConfig config,
        ProgressTracker progressTracker
    ) {
        Graph[] graphs = config.relationshipTypes()
            .stream()
            .map(relType -> graphStore.getGraph(RelationshipType.of(relType)))
            .toArray(Graph[]::new);

        return new CollapsePath(graphs, config, Pools.DEFAULT);
    }

    @Override
    public String taskName() {
        return "CollapsePath";
    }
}
