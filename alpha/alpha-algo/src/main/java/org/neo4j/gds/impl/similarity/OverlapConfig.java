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

import org.neo4j.gds.annotation.Configuration;
import org.neo4j.gds.annotation.ValueClass;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@ValueClass
@Configuration
@SuppressWarnings("immutables:subtype")
public interface OverlapConfig extends SimilarityConfig {

    @Override
    @Configuration.Ignore
    default String graph() {
        throw new UnsupportedOperationException(
            "OverlapConfig does not support loading via Graph/Cypher.");
    }

    @Override
    default List<Map<String,Object>> data() {
        return Collections.emptyList();
    }

    @Override
    default String writeRelationshipType() {
        return "NARROWER_THAN";
    }
}
