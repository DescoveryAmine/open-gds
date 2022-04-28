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
package org.neo4j.gds.core.loading.nodeproperties;

import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.api.NodeProperties;
import org.neo4j.values.storable.Value;

public abstract class InnerNodePropertiesBuilder {
    protected abstract Class<?> valueClass();

    public abstract void setValue(long neoNodeId, Value value);

    /**
     * Builds the underlying node properties as-is.
     *
     * Note:
     * The method expects the underlying node properties to be indexed
     * by internal ids, i.e., ids ranging from 0 to node count.
     * Violating the constraint is undefined behaviour.
     */
    public abstract NodeProperties buildDirect(long size);

    /**
     * Builds the underlying node properties and performs a remapping
     * to the internal id space using the given id map.
     */
    public abstract NodeProperties build(long size, IdMap idMap);
}
