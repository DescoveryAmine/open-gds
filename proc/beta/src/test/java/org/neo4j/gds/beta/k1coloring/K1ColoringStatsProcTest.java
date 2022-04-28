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
package org.neo4j.gds.beta.k1coloring;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.neo4j.gds.AlgoBaseProc;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.utils.paged.HugeLongArray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class K1ColoringStatsProcTest extends K1ColoringProcBaseTest<K1ColoringStatsConfig> {

    @Override
    public Class<? extends AlgoBaseProc<K1Coloring, HugeLongArray, K1ColoringStatsConfig, ?>> getProcedureClazz() {
        return K1ColoringStatsProc.class;
    }

    @Override
    public K1ColoringStatsConfig createConfig(CypherMapWrapper configMap) {
        return K1ColoringStatsConfig.of(configMap);
    }
    @Test
    void testStats() {
        @Language("Cypher")
        String query = algoBuildStage()
            .statsMode()
            .yields();

        runQueryWithRowConsumer(query, row -> {
            assertNotEquals(-1L, row.getNumber("preProcessingMillis").longValue());
            assertNotEquals(-1L, row.getNumber("computeMillis").longValue());
            assertEquals(4, row.getNumber("nodeCount").longValue());
            assertEquals(2, row.getNumber("colorCount").longValue());
            assertTrue(row.getBoolean("didConverge"));
            assertTrue(row.getNumber("ranIterations").longValue() < 3);
        });
    }
}
