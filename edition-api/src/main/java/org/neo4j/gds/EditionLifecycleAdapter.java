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
package org.neo4j.gds;

import org.neo4j.configuration.Config;
import org.neo4j.gds.concurrency.ConcurrencyValidatorBuilder;
import org.neo4j.gds.concurrency.ConcurrencyValidatorService;
import org.neo4j.gds.concurrency.PoolSizesProvider;
import org.neo4j.gds.concurrency.PoolSizesService;
import org.neo4j.gds.core.IdMapBehaviorFactory;
import org.neo4j.gds.core.IdMapBehaviorServiceProvider;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.core.model.ModelCatalogProvider;
import org.neo4j.gds.transaction.SecurityContextWrapperFactory;
import org.neo4j.kernel.api.procedure.GlobalProcedures;
import org.neo4j.kernel.extension.context.ExtensionContext;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;

import static org.neo4j.gds.utils.PriorityServiceLoader.loadService;

public class EditionLifecycleAdapter extends LifecycleAdapter {

    private final ExtensionContext context;
    private final Config config;
    private final GlobalProcedures globalProceduresRegistry;

    EditionLifecycleAdapter(
        ExtensionContext context,
        Config config,
        GlobalProcedures globalProceduresRegistry
    ) {
        this.context = context;
        this.config = config;
        this.globalProceduresRegistry = globalProceduresRegistry;
    }

    @Override
    public void init() {
        var licenseState = registerLicenseState();
        registerSecurityContextService(licenseState);

        setupProcedurePreconditions(licenseState);
        setupIdMapBehavior(licenseState);
        setupConcurrencyValidator(licenseState);
        setupPoolSizes(licenseState);
        setupModelCatalog(licenseState);
    }

    private LicenseState registerLicenseState() {
        var licensingServiceBuilder = loadService(
            LicensingServiceBuilder.class,
            LicensingServiceBuilder::priority
        );
        var licensingService = licensingServiceBuilder.build(config);

        globalProceduresRegistry.registerComponent(LicenseState.class, (context) -> licensingService.get(), true);
        context.dependencySatisfier().satisfyDependency(licensingService.get());
        return licensingService.get();
    }

    private void registerSecurityContextService(LicenseState licenseState) {
        var securityContextServiceFactory = loadService(
            SecurityContextWrapperFactory.class,
            SecurityContextWrapperFactory::priority
        );

        var securityContextService = securityContextServiceFactory.create(licenseState);
        context.dependencySatisfier().satisfyDependency(securityContextService);
    }

    private void setupProcedurePreconditions(LicenseState licenseState) {
        var procedurePreconditionsFactory = loadService(
            ProcedurePreconditionsFactory.class,
            ProcedurePreconditionsFactory::priority
        );

        var procedurePreconditions = procedurePreconditionsFactory.create(licenseState);
        ProcedurePreconditionsProvider.procedurePreconditions(procedurePreconditions);
    }

    private void setupIdMapBehavior(LicenseState licenseState) {
        var idMapBehaviorFactory = loadService(
            IdMapBehaviorFactory.class,
            IdMapBehaviorFactory::priority
        );

        IdMapBehaviorServiceProvider.idMapBehavior(idMapBehaviorFactory.create(licenseState));
    }

    private void setupConcurrencyValidator(LicenseState licenseState) {
        var concurrencyValidatorBuilder = loadService(
            ConcurrencyValidatorBuilder.class,
            ConcurrencyValidatorBuilder::priority
        );

        ConcurrencyValidatorService.validator(concurrencyValidatorBuilder.build(licenseState));
    }

    private void setupPoolSizes(LicenseState licenseState) {
        var poolSizesProvider = loadService(
            PoolSizesProvider.class,
            PoolSizesProvider::priority
        );

        PoolSizesService.poolSizes(poolSizesProvider.get(licenseState));
    }

    private void setupModelCatalog(LicenseState licenseState) {
        var modelCatalogProvider = loadService(
            ModelCatalogProvider.class,
            ModelCatalogProvider::priority
        );

        var modelCatalog = modelCatalogProvider.get(licenseState);

        globalProceduresRegistry.registerComponent(
            ModelCatalog.class,
            (context) -> modelCatalog,
            true
        );
        context.dependencySatisfier().satisfyDependency(modelCatalog);
    }
}
