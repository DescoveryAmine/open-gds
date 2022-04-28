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
package org.neo4j.gds.impl.similarity;

import org.neo4j.gds.compat.MapUtil;
import org.neo4j.graphdb.Node;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserAggregationResult;
import org.neo4j.procedure.UserAggregationUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SimilarityVectorAggregator {
    private final List<Map<String, Object>> vector = new ArrayList<>();
    public static final String CATEGORY_KEY = "category";
    public static final String WEIGHT_KEY = "weight";

    @UserAggregationUpdate
    public void next(
            @Name("node") Node node, @Name("weight") double weight) {
        vector.add(MapUtil.map(CATEGORY_KEY, node.getId(), WEIGHT_KEY, weight));
    }

    @UserAggregationResult
    public List<Map<String, Object>> result() {
        return vector;
    }
}
