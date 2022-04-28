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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.core.utils.io.file.csv.CsvGraphInfoVisitor;
import org.neo4j.gds.core.utils.io.file.csv.CsvMapUtil;
import org.neo4j.kernel.database.DatabaseIdFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public class GraphInfoLoader {
    private final Path graphInfoPath;
    private final ObjectReader objectReader;

    public GraphInfoLoader(Path csvDirectory) {
        this.graphInfoPath = csvDirectory.resolve(CsvGraphInfoVisitor.GRAPH_INFO_FILE_NAME);

        CsvMapper csvMapper = new CsvMapper();
        csvMapper.enable(CsvParser.Feature.TRIM_SPACES);
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        objectReader = csvMapper.readerFor(GraphInfoLine.class).with(schema);
    }

    public GraphInfo load() {
        try (var fileReader = Files.newBufferedReader(graphInfoPath, StandardCharsets.UTF_8)) {
            var mappingIterator = objectReader.<GraphInfoLine>readValues(fileReader);

            var line = mappingIterator.next();
            var databaseId = DatabaseIdFactory.from(line.databaseName, line.databaseId);
            return ImmutableGraphInfo.builder()
                .namedDatabaseId(databaseId)
                .nodeCount(line.nodeCount)
                .maxOriginalId(line.maxOriginalId)
                .relationshipTypeCounts(line.relTypeCounts)
                .build();

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static class GraphInfoLine {
        @JsonProperty
        UUID databaseId;

        @JsonProperty
        String databaseName;

        @JsonProperty
        long nodeCount;

        @JsonProperty
        long maxOriginalId;

        @JsonDeserialize(using = RelationshipTypesDeserializer.class)
        Map<RelationshipType, Long> relTypeCounts = Map.of();
    }

    static class RelationshipTypesDeserializer extends StdDeserializer<Map<RelationshipType, Long>> {

        public RelationshipTypesDeserializer() {
            this(null);
        }

        RelationshipTypesDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public Map<RelationshipType, Long> deserialize(
            JsonParser parser, DeserializationContext ctxt
        ) throws IOException {
            String mapString = parser.getText();
            return CsvMapUtil.fromString(mapString, RelationshipType::of, Long::parseLong);
        }
    }
}
