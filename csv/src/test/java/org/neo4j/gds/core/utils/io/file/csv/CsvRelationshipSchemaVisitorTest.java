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
package org.neo4j.gds.core.utils.io.file.csv;

import org.junit.jupiter.api.Test;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.DefaultValue;
import org.neo4j.gds.api.PropertyState;
import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.gds.core.Aggregation;

import java.util.List;

import static org.neo4j.gds.core.utils.io.file.csv.CsvNodeSchemaVisitor.DEFAULT_VALUE_COLUMN_NAME;
import static org.neo4j.gds.core.utils.io.file.csv.CsvNodeSchemaVisitor.PROPERTY_KEY_COLUMN_NAME;
import static org.neo4j.gds.core.utils.io.file.csv.CsvNodeSchemaVisitor.STATE_COLUMN_NAME;
import static org.neo4j.gds.core.utils.io.file.csv.CsvNodeSchemaVisitor.VALUE_TYPE_COLUMN_NAME;
import static org.neo4j.gds.core.utils.io.file.csv.CsvRelationshipSchemaVisitor.AGGREGATION_COLUMN_NAME;
import static org.neo4j.gds.core.utils.io.file.csv.CsvRelationshipSchemaVisitor.RELATIONSHIP_SCHEMA_FILE_NAME;
import static org.neo4j.gds.core.utils.io.file.csv.CsvRelationshipSchemaVisitor.RELATIONSHIP_TYPE_COLUMN_NAME;

public class CsvRelationshipSchemaVisitorTest extends CsvVisitorTest {

    public static final List<String> RELATIONSHIP_SCHEMA_COLUMNS = List.of(
        RELATIONSHIP_TYPE_COLUMN_NAME,
        PROPERTY_KEY_COLUMN_NAME,
        VALUE_TYPE_COLUMN_NAME,
        DEFAULT_VALUE_COLUMN_NAME,
        AGGREGATION_COLUMN_NAME,
        STATE_COLUMN_NAME
    );

    @Test
    void writesVisitedNodeSchema() {
        var relationshipSchemaVisitor = new CsvRelationshipSchemaVisitor(tempDir);
        var relType1 = RelationshipType.of("REL1");
        relationshipSchemaVisitor.relationshipType(relType1);
        relationshipSchemaVisitor.key("prop1");
        relationshipSchemaVisitor.valueType(ValueType.LONG);
        relationshipSchemaVisitor.defaultValue(DefaultValue.of(42L));
        relationshipSchemaVisitor.state(PropertyState.PERSISTENT);
        relationshipSchemaVisitor.aggregation(Aggregation.COUNT);
        relationshipSchemaVisitor.endOfEntity();

        var relType2 = RelationshipType.of("REL2");
        relationshipSchemaVisitor.relationshipType(relType2);
        relationshipSchemaVisitor.key("prop2");
        relationshipSchemaVisitor.valueType(ValueType.DOUBLE);
        relationshipSchemaVisitor.defaultValue(DefaultValue.of(13.37D));
        relationshipSchemaVisitor.state(PropertyState.TRANSIENT);
        relationshipSchemaVisitor.aggregation(Aggregation.DEFAULT);
        relationshipSchemaVisitor.endOfEntity();

        relationshipSchemaVisitor.close();
        assertCsvFiles(List.of(RELATIONSHIP_SCHEMA_FILE_NAME));
        assertDataContent(
            RELATIONSHIP_SCHEMA_FILE_NAME,
            List.of(
                defaultHeaderColumns(),
                List.of("REL1", "prop1", "long", "DefaultValue(42)", "COUNT", "PERSISTENT"),
                List.of("REL2", "prop2", "double", "DefaultValue(13.37)", "DEFAULT", "TRANSIENT")
            )
        );
    }

    @Test
    void writesSchemaWithoutProperties() {
        var relationshipSchemaVisitor = new CsvRelationshipSchemaVisitor(tempDir);
        RelationshipType rel1 = RelationshipType.of("REL1");
        relationshipSchemaVisitor.relationshipType(rel1);
        relationshipSchemaVisitor.endOfEntity();

        RelationshipType rel2 = RelationshipType.of("REL2");
        relationshipSchemaVisitor.relationshipType(rel2);
        relationshipSchemaVisitor.endOfEntity();

        relationshipSchemaVisitor.close();
        assertCsvFiles(List.of(RELATIONSHIP_SCHEMA_FILE_NAME));
        assertDataContent(
            RELATIONSHIP_SCHEMA_FILE_NAME,
            List.of(
                defaultHeaderColumns(),
                List.of("REL1"),
                List.of("REL2")
            )
        );
    }

    @Test
    void writesSchemaWithMixedProperties() {
        var relationshipSchemaVisitor = new CsvRelationshipSchemaVisitor(tempDir);
        RelationshipType rel1 = RelationshipType.of("REL1");
        relationshipSchemaVisitor.relationshipType(rel1);
        relationshipSchemaVisitor.key("prop1");
        relationshipSchemaVisitor.valueType(ValueType.LONG);
        relationshipSchemaVisitor.defaultValue(DefaultValue.of(42L));
        relationshipSchemaVisitor.state(PropertyState.PERSISTENT);
        relationshipSchemaVisitor.aggregation(Aggregation.COUNT);
        relationshipSchemaVisitor.endOfEntity();

        RelationshipType rel2 = RelationshipType.of("REL2");
        relationshipSchemaVisitor.relationshipType(rel2);
        relationshipSchemaVisitor.endOfEntity();

        relationshipSchemaVisitor.close();
        assertCsvFiles(List.of(RELATIONSHIP_SCHEMA_FILE_NAME));
        assertDataContent(
            RELATIONSHIP_SCHEMA_FILE_NAME,
            List.of(
                defaultHeaderColumns(),
                List.of("REL1", "prop1", "long", "DefaultValue(42)", "COUNT", "PERSISTENT"),
                List.of("REL2")
            )
        );
    }

    @Override
    protected List<String> defaultHeaderColumns() {
        return RELATIONSHIP_SCHEMA_COLUMNS;
    }
}
