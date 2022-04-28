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

import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.gds.api.schema.NodeSchema;
import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.utils.io.GraphStoreExporter;
import org.neo4j.gds.core.utils.io.GraphStoreInput;
import org.neo4j.gds.core.utils.io.NeoNodeProperties;
import org.neo4j.gds.core.utils.io.file.csv.CsvGraphInfoVisitor;
import org.neo4j.gds.core.utils.io.file.csv.CsvNodeSchemaVisitor;
import org.neo4j.gds.core.utils.io.file.csv.CsvNodeVisitor;
import org.neo4j.gds.core.utils.io.file.csv.CsvRelationshipSchemaVisitor;
import org.neo4j.gds.core.utils.io.file.csv.CsvRelationshipVisitor;
import org.neo4j.gds.core.utils.io.file.csv.UserInfoVisitor;
import org.neo4j.gds.core.utils.io.file.schema.NodeSchemaVisitor;
import org.neo4j.gds.core.utils.io.file.schema.RelationshipSchemaVisitor;
import org.neo4j.gds.core.utils.progress.tasks.ProgressTracker;
import org.neo4j.internal.batchimport.input.Collector;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class GraphStoreToFileExporter extends GraphStoreExporter<GraphStoreToFileExporterConfig> {

    private final VisitorProducer<NodeVisitor> nodeVisitorSupplier;
    private final VisitorProducer<RelationshipVisitor> relationshipVisitorSupplier;

    public static GraphStoreToFileExporter csv(
        GraphStore graphStore,
        GraphStoreToFileExporterConfig config,
        Path exportPath
    ) {
        return csv(graphStore, config, exportPath, Optional.empty());
    }

    public static GraphStoreToFileExporter csv(
        GraphStore graphStore,
        GraphStoreToFileExporterConfig config,
        Path exportPath,
        Optional<NeoNodeProperties> neoNodeProperties
    ) {
        Set<String> headerFiles = ConcurrentHashMap.newKeySet();

        var nodeSchema = graphStore.schema().nodeSchema();
        var relationshipSchema = graphStore.schema().relationshipSchema();

        var builder = NodeSchema.builder();

        // Add additional properties to each label present in the graph store.
        var neoNodeSchema = neoNodeProperties.map(additionalProps -> {
            additionalProps
                .neoNodeProperties()
                .forEach((key, ignore) -> nodeSchema
                    .availableLabels()
                    .forEach(label -> builder.addProperty(label, key, ValueType.STRING))
                );
            return builder.build();
        }).orElseGet(() -> builder.build());

        return GraphStoreToFileExporter.of(
            graphStore,
            config,
            neoNodeProperties,
            () -> new UserInfoVisitor(exportPath),
            () -> new CsvGraphInfoVisitor(exportPath),
            () -> new CsvNodeSchemaVisitor(exportPath),
            () -> new CsvRelationshipSchemaVisitor(exportPath),
            (index) -> new CsvNodeVisitor(
                exportPath,
                nodeSchema.union(neoNodeSchema),
                headerFiles,
                index
            ),
            (index) -> new CsvRelationshipVisitor(exportPath, relationshipSchema, headerFiles, index)
        );
    }

    private static GraphStoreToFileExporter of(
        GraphStore graphStore,
        GraphStoreToFileExporterConfig config,
        Optional<NeoNodeProperties> neoNodeProperties,
        Supplier<SingleRowVisitor<String>> userInfoVisitorSupplier,
        Supplier<SingleRowVisitor<GraphInfo>> graphInfoVisitorSupplier,
        Supplier<NodeSchemaVisitor> nodeSchemaVisitorSupplier,
        Supplier<RelationshipSchemaVisitor> relationshipSchemaVisitorSupplier,
        VisitorProducer<NodeVisitor> nodeVisitorSupplier,
        VisitorProducer<RelationshipVisitor> relationshipVisitorSupplier
    ) {
        if (config.includeMetaData()) {
            return new FullGraphStoreToFileExporter(
                graphStore,
                config,
                neoNodeProperties,
                userInfoVisitorSupplier,
                graphInfoVisitorSupplier,
                nodeSchemaVisitorSupplier,
                relationshipSchemaVisitorSupplier,
                nodeVisitorSupplier,
                relationshipVisitorSupplier
            );
        } else {
            return new GraphStoreToFileExporter(
                graphStore,
                config,
                neoNodeProperties,
                nodeVisitorSupplier,
                relationshipVisitorSupplier
            );
        }
    }

    GraphStoreToFileExporter(
        GraphStore graphStore,
        GraphStoreToFileExporterConfig config,
        Optional<NeoNodeProperties> neoNodeProperties,
        VisitorProducer<NodeVisitor> nodeVisitorSupplier,
        VisitorProducer<RelationshipVisitor> relationshipVisitorSupplier
    ) {
        super(graphStore, config, neoNodeProperties);
        this.nodeVisitorSupplier = nodeVisitorSupplier;
        this.relationshipVisitorSupplier = relationshipVisitorSupplier;
    }

    @Override
    protected void export(GraphStoreInput graphStoreInput) {
        exportNodes(graphStoreInput);
        exportRelationships(graphStoreInput);
    }

    private void exportNodes(GraphStoreInput graphStoreInput) {
        var nodeInput = graphStoreInput.nodes(Collector.EMPTY);
        var nodeInputIterator = nodeInput.iterator();

        var tasks = ParallelUtil.tasks(
            config.writeConcurrency(),
            (index) -> new ElementImportRunner(nodeVisitorSupplier.apply(index), nodeInputIterator, ProgressTracker.NULL_TRACKER)
        );

        ParallelUtil.runWithConcurrency(config.writeConcurrency(), tasks, Pools.DEFAULT);
    }

    private void exportRelationships(GraphStoreInput graphStoreInput) {
        var relationshipInput = graphStoreInput.relationships(Collector.EMPTY);
        var relationshipInputIterator = relationshipInput.iterator();

        var tasks = ParallelUtil.tasks(
            config.writeConcurrency(),
            (index) -> new ElementImportRunner(relationshipVisitorSupplier.apply(index), relationshipInputIterator, ProgressTracker.NULL_TRACKER)
        );

        ParallelUtil.runWithConcurrency(config.writeConcurrency(), tasks, Pools.DEFAULT);
    }

    private static final class FullGraphStoreToFileExporter extends GraphStoreToFileExporter {
        private final Supplier<SingleRowVisitor<String>> userInfoVisitorSupplier;
        private final Supplier<SingleRowVisitor<GraphInfo>> graphInfoVisitorSupplier;
        private final Supplier<NodeSchemaVisitor> nodeSchemaVisitorSupplier;
        private final Supplier<RelationshipSchemaVisitor> relationshipSchemaVisitorSupplier;

        private FullGraphStoreToFileExporter(
            GraphStore graphStore,
            GraphStoreToFileExporterConfig config,
            Optional<NeoNodeProperties> neoNodeProperties,
            Supplier<SingleRowVisitor<String>> userInfoVisitorSupplier,
            Supplier<SingleRowVisitor<GraphInfo>> graphInfoVisitorSupplier,
            Supplier<NodeSchemaVisitor> nodeSchemaVisitorSupplier,
            Supplier<RelationshipSchemaVisitor> relationshipSchemaVisitorSupplier,
            VisitorProducer<NodeVisitor> nodeVisitorSupplier,
            VisitorProducer<RelationshipVisitor> relationshipVisitorSupplier
        ) {
            super(graphStore, config, neoNodeProperties, nodeVisitorSupplier, relationshipVisitorSupplier);
            this.userInfoVisitorSupplier = userInfoVisitorSupplier;
            this.graphInfoVisitorSupplier = graphInfoVisitorSupplier;
            this.nodeSchemaVisitorSupplier = nodeSchemaVisitorSupplier;
            this.relationshipSchemaVisitorSupplier = relationshipSchemaVisitorSupplier;
        }

        @Override
        protected void export(GraphStoreInput graphStoreInput) {
            exportUserName();
            exportGraphInfo(graphStoreInput);
            exportNodeSchema(graphStoreInput);
            exportRelationshipSchema(graphStoreInput);
            super.export(graphStoreInput);
        }

        private void exportUserName() {
            try (var userInfoVisitor = userInfoVisitorSupplier.get()) {
                userInfoVisitor.export(config.username());
            }
        }

        private void exportGraphInfo(GraphStoreInput graphStoreInput) {
            GraphInfo graphInfo = graphStoreInput.metaDataStore().graphInfo();
            try (var graphInfoVisitor = graphInfoVisitorSupplier.get()) {
                graphInfoVisitor.export(graphInfo);
            }
        }

        private void exportNodeSchema(GraphStoreInput graphStoreInput) {
            var nodeSchema = graphStoreInput.metaDataStore().nodeSchema();
            try (var nodeSchemaVisitor = nodeSchemaVisitorSupplier.get()) {
                nodeSchema.properties().forEach((nodeLabel, properties) -> {
                    if (properties.isEmpty()) {
                        nodeSchemaVisitor.nodeLabel(nodeLabel);
                        nodeSchemaVisitor.endOfEntity();
                    } else {
                        properties.forEach((propertyKey, propertySchema) -> {
                            nodeSchemaVisitor.nodeLabel(nodeLabel);
                            nodeSchemaVisitor.key(propertyKey);
                            nodeSchemaVisitor.defaultValue(propertySchema.defaultValue());
                            nodeSchemaVisitor.valueType(propertySchema.valueType());
                            nodeSchemaVisitor.state(propertySchema.state());
                            nodeSchemaVisitor.endOfEntity();
                        });
                    }
                });
            }
        }

        private void exportRelationshipSchema(GraphStoreInput graphStoreInput) {
            var relationshipSchema = graphStoreInput.metaDataStore().relationshipSchema();
            try (var relationshipSchemaVisitor = relationshipSchemaVisitorSupplier.get()) {
                relationshipSchema.properties().forEach((relationshipType, properties) -> {
                    if (properties.isEmpty()) {
                        relationshipSchemaVisitor.relationshipType(relationshipType);
                        relationshipSchemaVisitor.endOfEntity();
                    } else {
                        properties.forEach((propertyKey, propertySchema) -> {
                            relationshipSchemaVisitor.relationshipType(relationshipType);
                            relationshipSchemaVisitor.key(propertyKey);
                            relationshipSchemaVisitor.defaultValue(propertySchema.defaultValue());
                            relationshipSchemaVisitor.valueType(propertySchema.valueType());
                            relationshipSchemaVisitor.aggregation(propertySchema.aggregation());
                            relationshipSchemaVisitor.state(propertySchema.state());
                            relationshipSchemaVisitor.endOfEntity();
                        });
                    }
                });
            }
        }
    }
}
