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
package org.neo4j.gds.core.utils.io.file;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.gds.RelationshipType;
import org.neo4j.kernel.database.DatabaseIdFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.neo4j.gds.core.utils.io.file.csv.CsvGraphInfoVisitor.GRAPH_INFO_FILE_NAME;

class GraphInfoLoaderTest {

    @Test
    void shouldLoadGraphInfo(@TempDir Path exportDir) throws IOException {
        var uuid = UUID.randomUUID();

        var databaseId = DatabaseIdFactory.from("my-database", uuid);
        var graphInfoFile = exportDir.resolve(GRAPH_INFO_FILE_NAME).toFile();
        var lines = List.of(
            String.join(", ", "databaseId", "databaseName", "nodeCount", "maxOriginalId", "relTypeCounts"),
            String.join(", ", uuid.toString(), "my-database", "19", "1337", "REL;42")
        );
        FileUtils.writeLines(graphInfoFile, lines);

        var graphInfoLoader = new GraphInfoLoader(exportDir);
        var graphInfo = graphInfoLoader.load();

        assertThat(graphInfo).isNotNull();
        assertThat(graphInfo.namedDatabaseId()).isEqualTo(databaseId);
        assertThat(graphInfo.namedDatabaseId().name()).isEqualTo("my-database");

        assertThat(graphInfo.nodeCount()).isEqualTo(19L);
        assertThat(graphInfo.maxOriginalId()).isEqualTo(1337L);

        assertThat(graphInfo.relationshipTypeCounts()).containsExactlyEntriesOf(
            Map.of(RelationshipType.of("REL"), 42L)
        );
    }

    /**
     * Test for backwards compatibility by leaving out `relTypeCounts`
     */
    @Test
    void shouldLoadGraphInfoWithoutRelTypeCounts(@TempDir Path exportDir) throws IOException {
        var uuid = UUID.randomUUID();

        var graphInfoFile = exportDir.resolve(GRAPH_INFO_FILE_NAME).toFile();
        var lines = List.of(
            String.join(", ", "databaseId", "databaseName", "nodeCount", "maxOriginalId"),
            String.join(", ", uuid.toString(), "my-database", "19", "1337")
        );
        FileUtils.writeLines(graphInfoFile, lines);

        var graphInfoLoader = new GraphInfoLoader(exportDir);
        var graphInfo = graphInfoLoader.load();

        assertThat(graphInfo.relationshipTypeCounts()).isEmpty();
    }

}
