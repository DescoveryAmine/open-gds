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
package org.neo4j.gds.core.loading;

import org.neo4j.gds.NodeLabel;
import org.neo4j.gds.PropertyMapping;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.gds.api.NodeProperty;
import org.neo4j.gds.api.NodePropertyStore;
import org.neo4j.gds.api.PropertyState;

import java.util.HashMap;
import java.util.Map;

@ValueClass
public interface IdMapAndProperties {

    IdMap idMap();

    Map<NodeLabel, NodePropertyStore> properties();

    static IdMapAndProperties of(IdMap idMap, Map<NodeLabel, Map<PropertyMapping, NodeProperties>> properties) {
        Map<NodeLabel, NodePropertyStore> nodePropertyStores = new HashMap<>(properties.size());
        properties.forEach((nodeLabel, propertyMap) -> {
            NodePropertyStore.Builder builder = NodePropertyStore.builder();
            propertyMap.forEach((propertyMapping, propertyValues) -> builder.putNodeProperty(
                propertyMapping.propertyKey(),
                NodeProperty.of(
                    propertyMapping.propertyKey(),
                    PropertyState.PERSISTENT,
                    propertyValues,
                    propertyMapping.defaultValue().isUserDefined()
                        ? propertyMapping.defaultValue()
                        : propertyValues.valueType().fallbackValue()
                )
            ));
            nodePropertyStores.put(nodeLabel, builder.build());
        });
        return ImmutableIdMapAndProperties.of(idMap, nodePropertyStores);
    }
}
