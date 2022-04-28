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
package org.neo4j.gds.ml.linkmodels;

import org.immutables.value.Value;
import org.neo4j.gds.annotation.Configuration;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.config.WritePropertyConfig;
import org.neo4j.gds.config.WriteRelationshipConfig;
import org.neo4j.gds.core.CypherMapWrapper;

@ValueClass
@Configuration
@SuppressWarnings("immutables:subtype")
public interface LinkPredictionPredictWriteConfig extends LinkPredictionPredictBaseConfig, WriteRelationshipConfig, WritePropertyConfig {

    @Value.Default
    @Override
    default String writeProperty() {
        return "probability";
    }

    @Override
    @Value.Derived
    @Configuration.Key(WRITE_CONCURRENCY_KEY)
    @Configuration.Ignore
    default int writeConcurrency() {
        return concurrency();
    }

    static LinkPredictionPredictWriteConfig of(String username, CypherMapWrapper config) {
        return new LinkPredictionPredictWriteConfigImpl(username, config);
    }

}
