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
package org.neo4j.gds.beta.filter.expression;

import org.immutables.value.Value;
import org.neo4j.gds.ElementIdentifier;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.nodeproperties.ValueType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ValueClass
public interface ValidationContext {
    Context context();

    Set<String> availableLabelsOrTypes();

    Map<String, ValueType> availableProperties();

    @Value.Default
    default List<SemanticErrors.SemanticError> errors() {
        return List.of();
    }

    @Value.Derived
    default ValidationContext withError(SemanticErrors.SemanticError error) {
        return ImmutableValidationContext
            .builder()
            .from(this)
            .addError(error)
            .build();
    }

    @Value.Derived
    default void validate() throws SemanticErrors {
        if (!errors().isEmpty()) {
            throw SemanticErrors.of(errors());
        }
    }

    static ValidationContext forNodes(GraphStore graphStore) {
        var propertiesAndTypes = graphStore
            .schema()
            .nodeSchema()
            .unionProperties()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().valueType()
            ));

        return ImmutableValidationContext
            .builder()
            .context(Context.NODE)
            .addAllAvailableLabelsOrTypes(graphStore
                .nodeLabels()
                .stream()
                .map(ElementIdentifier::name)
                .collect(Collectors.toSet()))
            .putAllAvailableProperties(propertiesAndTypes)
            .build();
    }

    static ValidationContext forRelationships(GraphStore graphStore) {
        var propertiesAndTypes = graphStore
            .schema()
            .relationshipSchema()
            .unionProperties()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().valueType()
            ));

        return ImmutableValidationContext
            .builder()
            .context(Context.RELATIONSHIP)
            .addAllAvailableLabelsOrTypes(graphStore
                .relationshipTypes()
                .stream()
                .map(ElementIdentifier::name)
                .collect(Collectors.toSet()))
            .putAllAvailableProperties(propertiesAndTypes)
            .build();
    }

    enum Context {
        NODE,
        RELATIONSHIP
    }
}
