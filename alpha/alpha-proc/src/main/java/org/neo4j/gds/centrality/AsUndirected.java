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
package org.neo4j.gds.centrality;

import org.neo4j.gds.Orientation;
import org.neo4j.gds.RelationshipProjections;
import org.neo4j.gds.config.GraphProjectConfig;
import org.neo4j.gds.config.GraphProjectFromStoreConfig;
import org.neo4j.gds.config.ImmutableGraphProjectFromStoreConfig;

public enum AsUndirected implements GraphProjectConfig.Rewriter {

    INSTANCE;

    static GraphProjectConfig rewrite(GraphProjectConfig config) {
        return INSTANCE.apply(config);
    }

    @Override
    public GraphProjectConfig store(GraphProjectFromStoreConfig storeConfig) {
        RelationshipProjections.Builder builder = RelationshipProjections.builder();
        storeConfig.relationshipProjections().projections().forEach(
            (id, projection) -> builder.putProjection(id, projection.withOrientation(Orientation.UNDIRECTED))
        );
        return ImmutableGraphProjectFromStoreConfig.builder()
            .from(storeConfig)
            .relationshipProjections(builder.build())
            .build();
    }
}
