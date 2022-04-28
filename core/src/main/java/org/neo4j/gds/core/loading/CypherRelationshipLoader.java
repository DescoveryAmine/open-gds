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

import org.apache.commons.lang3.mutable.MutableInt;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectDoubleHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectIntHashMap;
import org.immutables.value.Value;
import org.neo4j.gds.AbstractRelationshipProjection;
import org.neo4j.gds.Orientation;
import org.neo4j.gds.PropertyMapping;
import org.neo4j.gds.PropertyMappings;
import org.neo4j.gds.RelationshipProjection;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.GraphLoaderContext;
import org.neo4j.gds.api.IdMap;
import org.neo4j.gds.config.GraphProjectFromCypherConfig;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.loading.construction.GraphFactory;
import org.neo4j.gds.core.loading.construction.NodesBuilder;
import org.neo4j.gds.core.loading.construction.RelationshipsBuilder;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Value.Enclosing
class CypherRelationshipLoader extends CypherRecordLoader<RelationshipsAndProperties> {

    private final IdMap idMap;
    private final Context loaderContext;
    private final ProgressTracker progressTracker;

    // Property mappings are either defined upfront in
    // the procedure configuration or during load time
    // by looking at the columns returned by the query.
    private ObjectIntHashMap<String> propertyKeyIdsByName;
    private ObjectDoubleHashMap<String> propertyDefaultValueByName;
    private boolean initializedFromResult;
    private List<GraphFactory.PropertyConfig> propertyConfigs;
    private AbstractRelationshipProjection.Builder projectionBuilder;

    CypherRelationshipLoader(
        String relationshipQuery,
        IdMap idMap,
        GraphProjectFromCypherConfig config,
        GraphLoaderContext loadingContext,
        ProgressTracker progressTracker
    ) {
        super(relationshipQuery, idMap.nodeCount(), config, loadingContext);
        this.idMap = idMap;
        this.progressTracker = progressTracker;
        this.loaderContext = new Context();
    }

    private void initFromPropertyMappings(PropertyMappings propertyMappings) {
        var propertyKeyId = new MutableInt(0);
        int numberOfMappings = propertyMappings.numberOfMappings();

        propertyKeyIdsByName = new ObjectIntHashMap<>(numberOfMappings);
        propertyMappings
            .stream()
            .forEach(mapping -> propertyKeyIdsByName.put(mapping.neoPropertyKey(), propertyKeyId.getAndIncrement()));

        propertyDefaultValueByName = new ObjectDoubleHashMap<>(numberOfMappings);
        propertyMappings
            .stream()
            .forEach(mapping -> propertyDefaultValueByName.put(
                mapping.neoPropertyKey(),
                mapping.defaultValue().doubleValue()
            ));

        propertyConfigs = propertyMappings
            .stream()
            .map(mapping -> GraphFactory.PropertyConfig.of(mapping.aggregation(), mapping.defaultValue()))
            .collect(Collectors.toList());

        projectionBuilder = RelationshipProjection
            .builder()
            .orientation(Orientation.NATURAL)
            .properties(propertyMappings);
    }

    @Override
    BatchLoadResult loadSingleBatch(Transaction tx, int bufferSize) {
        progressTracker.beginSubTask("Relationships");
        Result queryResult = runLoadingQuery(tx);

        List<String> allColumns = queryResult.columns();

        // If the user specifies property mappings, we use those.
        // Otherwise, we create new property mappings from the result columns.
        // We do that only once, as each batch has the same columns.
        Collection<String> propertyColumns = getPropertyColumns(queryResult);
        if (!initializedFromResult) {

            List<PropertyMapping> propertyMappings = propertyColumns
                .stream()
                .map(propertyColumn -> PropertyMapping.of(
                    propertyColumn,
                    propertyColumn,
                    NodesBuilder.NO_PROPERTY_VALUE,
                    Aggregation.NONE
                ))
                .collect(Collectors.toList());

            initFromPropertyMappings(PropertyMappings.of(propertyMappings));

            initializedFromResult = true;
        }

        boolean isAnyRelTypeQuery = !allColumns.contains(RelationshipRowVisitor.TYPE_COLUMN);

        if (isAnyRelTypeQuery) {
            loaderContext.getOrCreateRelationshipsBuilder(RelationshipType.ALL_RELATIONSHIPS);
        }

        var visitor = new RelationshipRowVisitor(
            idMap,
            loaderContext,
            propertyKeyIdsByName,
            propertyDefaultValueByName,
            isAnyRelTypeQuery,
            cypherConfig.validateRelationships(),
            progressTracker
        );

        queryResult.accept(visitor);

        progressTracker.endSubTask("Relationships");
        return new BatchLoadResult(visitor.rows(), -1L);
    }

    @Override
    void updateCounts(BatchLoadResult result) {}

    @Override
    RelationshipsAndProperties result() {
        var relationshipsByType = loaderContext.relationshipBuildersByType.entrySet().stream().collect(Collectors.toMap(
            entry -> {
                var projection = projectionBuilder.type(entry.getKey().name).build();
                return RelationshipsAndProperties.RelationshipTypeAndProjection.of(entry.getKey(), projection);
            },
            entry -> entry.getValue().buildAll()
        ));

        return RelationshipsAndProperties.of(relationshipsByType);
    }

    @Override
    Set<String> getMandatoryColumns() {
        return RelationshipRowVisitor.REQUIRED_COLUMNS;
    }

    @Override
    Set<String> getReservedColumns() {
        return RelationshipRowVisitor.RESERVED_COLUMNS;
    }

    @Override
    QueryType queryType() {
        return QueryType.RELATIONSHIP;
    }

    class Context {

        private final Map<RelationshipType, RelationshipsBuilder> relationshipBuildersByType;

        Context() {
            this.relationshipBuildersByType = new HashMap<>();
        }

        RelationshipsBuilder getOrCreateRelationshipsBuilder(RelationshipType relationshipType) {
            return relationshipBuildersByType.computeIfAbsent(relationshipType, this::createRelationshipsBuilder);
        }

        private RelationshipsBuilder createRelationshipsBuilder(RelationshipType relationshipType) {
            return GraphFactory.initRelationshipsBuilder()
                .nodes(idMap)
                .concurrency(cypherConfig.readConcurrency())
                .propertyConfigs(propertyConfigs)
                .orientation(Orientation.NATURAL)
                .validateRelationships(cypherConfig.validateRelationships())
                .build();
        }
    }
}
