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
package org.neo4j.gds.core.utils.io.db;

import org.neo4j.configuration.Config;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.compat.Neo4jProxy;
import org.neo4j.gds.core.Settings;
import org.neo4j.gds.core.utils.io.GraphStoreExporter;
import org.neo4j.gds.core.utils.io.GraphStoreInput;
import org.neo4j.gds.core.utils.io.NeoNodeProperties;
import org.neo4j.internal.batchimport.AdditionalInitialIds;
import org.neo4j.internal.batchimport.BatchImporterFactory;
import org.neo4j.internal.batchimport.input.Collector;
import org.neo4j.internal.batchimport.input.Input;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.Neo4jLayout;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.kernel.impl.store.format.RecordFormatSelector;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.neo4j.logging.Level;
import org.neo4j.logging.NullLogProvider;
import org.neo4j.logging.internal.LogService;
import org.neo4j.logging.internal.NullLogService;
import org.neo4j.logging.internal.SimpleLogService;
import org.neo4j.logging.log4j.Log4jLogProvider;
import org.neo4j.logging.log4j.LogConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.neo4j.gds.utils.StringFormatting.formatWithLocale;
import static org.neo4j.kernel.impl.scheduler.JobSchedulerFactory.createScheduler;

public final class GraphStoreToDatabaseExporter extends GraphStoreExporter<GraphStoreToDatabaseExporterConfig> {

    private final Path neo4jHome;
    private final FileSystemAbstraction fs;

    public static GraphStoreToDatabaseExporter of(
        GraphStore graphStore,
        GraphDatabaseAPI api,
        GraphStoreToDatabaseExporterConfig config
    ) {
        return of(graphStore, api, config, Optional.empty());
    }

    public static GraphStoreToDatabaseExporter of(
        GraphStore graphStore,
        GraphDatabaseAPI api,
        GraphStoreToDatabaseExporterConfig config,
        Optional<NeoNodeProperties> neoNodeProperties
    ) {
        return new GraphStoreToDatabaseExporter(graphStore, api, config, neoNodeProperties);
    }

    private GraphStoreToDatabaseExporter(
        GraphStore graphStore,
        GraphDatabaseAPI api,
        GraphStoreToDatabaseExporterConfig config,
        Optional<NeoNodeProperties> neoNodeProperties
    ) {
        super(graphStore, config, neoNodeProperties);
        this.neo4jHome = api.databaseLayout().getNeo4jLayout().homeDirectory();
        this.fs = api.getDependencyResolver().resolveDependency(FileSystemAbstraction.class);
    }

    @Override
    public void export(GraphStoreInput graphStoreInput) {
        DIRECTORY_IS_WRITABLE.validate(neo4jHome);
        var databaseConfig = Config.defaults(Settings.neo4jHome(), neo4jHome);
        var databaseLayout = Neo4jLayout.of(databaseConfig).databaseLayout(config.dbName());

        var lifeSupport = new LifeSupport();

        try {
            LogService logService;
            if (config.enableDebugLog()) {
                var storeInternalLogPath = databaseConfig.get(Settings.storeInternalLogPath());
                var neo4jLoggerContext = LogConfig.createBuilder(fs, storeInternalLogPath, Level.INFO).build();
                var simpleLogService = new SimpleLogService(
                    NullLogProvider.getInstance(),
                    new Log4jLogProvider(neo4jLoggerContext)
                );
                logService = lifeSupport.add(simpleLogService);
            } else {
                logService = NullLogService.getInstance();
            }
            var jobScheduler = lifeSupport.add(createScheduler());

            lifeSupport.start();

            Input input = Neo4jProxy.batchInputFrom(graphStoreInput);

            var metaDataPath = databaseLayout.metadataStore();
            var dbExists = Files.exists(metaDataPath) && Files.isReadable(metaDataPath);
            if (dbExists) {
                throw new IllegalArgumentException(formatWithLocale(
                    "The database [%s] already exists. The graph export procedure can only create new databases.",
                    config.dbName()
                ));
            }

            var importer = Neo4jProxy.instantiateBatchImporter(
                BatchImporterFactory.withHighestPriority(),
                databaseLayout,
                fs,
                PageCacheTracer.NULL,
                config.writeConcurrency(),
                config.pageCacheMemory(),
                logService,
                Neo4jProxy.invisibleExecutionMonitor(),
                AdditionalInitialIds.EMPTY,
                databaseConfig,
                RecordFormatSelector.selectForConfig(databaseConfig, logService.getInternalLogProvider()),
                jobScheduler,
                Collector.EMPTY
            );
            importer.doImport(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            lifeSupport.shutdown();
        }
    }
}
