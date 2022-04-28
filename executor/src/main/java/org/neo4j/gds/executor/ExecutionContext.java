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
package org.neo4j.gds.executor;

import org.immutables.value.Value;
import org.jetbrains.annotations.Nullable;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.compat.GraphDatabaseApiProxy;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.core.utils.progress.EmptyTaskRegistryFactory;
import org.neo4j.gds.core.utils.progress.TaskRegistryFactory;
import org.neo4j.gds.core.utils.warnings.EmptyUserLogRegistryFactory;
import org.neo4j.gds.core.utils.warnings.UserLogRegistryFactory;
import org.neo4j.gds.core.write.RelationshipStreamExporter;
import org.neo4j.gds.core.write.RelationshipStreamExporterBuilder;
import org.neo4j.gds.transaction.SecurityContextWrapper;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.kernel.api.procs.ProcedureCallContext;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.database.NamedDatabaseId;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.logging.NullLog;

// TODO Remove the @Nullable annotations once the EstimationCli uses ProcedureExecutors
@ValueClass
public interface ExecutionContext {

    @Nullable
    GraphDatabaseAPI api();

    @Nullable
    ModelCatalog modelCatalog();

    @Nullable
    Log log();

    @Nullable
    Transaction procedureTransaction();

    @Nullable
    KernelTransaction transaction();

    @Nullable
    ProcedureCallContext callContext();

    @Nullable
    TaskRegistryFactory taskRegistryFactory();

    @Nullable
    UserLogRegistryFactory userLogRegistryFactory();

    String username();

    @Nullable
    RelationshipStreamExporterBuilder<? extends RelationshipStreamExporter> relationshipStreamExporterBuilder();

    @Value.Lazy
    default NamedDatabaseId databaseId() {
        return api().databaseId();
    }

    @Value.Lazy
    default boolean isGdsAdmin() {
        if (transaction() == null) {
            // No transaction available (likely we're in a test), no-one is admin here
            return false;
        }
        return GraphDatabaseApiProxy
            .resolveDependency(api(), SecurityContextWrapper.class)
            .isAdmin(transaction().securityContext());
    }

    ExecutionContext EMPTY = new ExecutionContext() {
        @Override
        public @Nullable GraphDatabaseAPI api() {
            return null;
        }

        @Override
        public @Nullable ModelCatalog modelCatalog() {
            return null;
        }

        @Override
        public @Nullable Log log() {
            return NullLog.getInstance();
        }

        @Override
        public @Nullable Transaction procedureTransaction() {
            return null;
        }

        @Override
        public @Nullable KernelTransaction transaction() {
            return null;
        }

        @Override
        public @Nullable ProcedureCallContext callContext() {
            return ProcedureCallContext.EMPTY;
        }

        @Override
        public @Nullable TaskRegistryFactory taskRegistryFactory() {
            return EmptyTaskRegistryFactory.INSTANCE;
        }

        @Override
        public @Nullable
        UserLogRegistryFactory userLogRegistryFactory() {
            return EmptyUserLogRegistryFactory.INSTANCE;
        }

        @Override
        public @Nullable RelationshipStreamExporterBuilder<? extends RelationshipStreamExporter> relationshipStreamExporterBuilder() {
            return null;
        }

        @Override
        public String username() {
            return "";
        }
    };
}
