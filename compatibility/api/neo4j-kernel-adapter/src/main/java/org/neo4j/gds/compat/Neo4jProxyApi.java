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
package org.neo4j.gds.compat;

import org.jetbrains.annotations.Nullable;
import org.neo4j.configuration.Config;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.config.Setting;
import org.neo4j.internal.batchimport.AdditionalInitialIds;
import org.neo4j.internal.batchimport.BatchImporter;
import org.neo4j.internal.batchimport.BatchImporterFactory;
import org.neo4j.internal.batchimport.input.Collector;
import org.neo4j.internal.batchimport.input.Input;
import org.neo4j.internal.batchimport.staging.ExecutionMonitor;
import org.neo4j.internal.id.IdGeneratorFactory;
import org.neo4j.internal.kernel.api.Cursor;
import org.neo4j.internal.kernel.api.IndexReadSession;
import org.neo4j.internal.kernel.api.NodeCursor;
import org.neo4j.internal.kernel.api.NodeLabelIndexCursor;
import org.neo4j.internal.kernel.api.NodeValueIndexCursor;
import org.neo4j.internal.kernel.api.PropertyCursor;
import org.neo4j.internal.kernel.api.Read;
import org.neo4j.internal.kernel.api.RelationshipScanCursor;
import org.neo4j.internal.kernel.api.Scan;
import org.neo4j.internal.kernel.api.procs.FieldSignature;
import org.neo4j.internal.kernel.api.procs.ProcedureSignature;
import org.neo4j.internal.kernel.api.procs.QualifiedName;
import org.neo4j.internal.kernel.api.security.AccessMode;
import org.neo4j.internal.kernel.api.security.AuthSubject;
import org.neo4j.internal.kernel.api.security.SecurityContext;
import org.neo4j.internal.schema.IndexOrder;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.io.pagecache.tracing.PageCacheTracer;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.kernel.database.DatabaseIdRepository;
import org.neo4j.kernel.database.NamedDatabaseId;
import org.neo4j.kernel.impl.store.RecordStore;
import org.neo4j.kernel.impl.store.format.RecordFormats;
import org.neo4j.kernel.impl.store.record.AbstractBaseRecord;
import org.neo4j.logging.internal.LogService;
import org.neo4j.procedure.Mode;
import org.neo4j.scheduler.JobScheduler;

import java.util.List;
import java.util.Optional;

public interface Neo4jProxyApi {

    GdsGraphDatabaseAPI newDb(DatabaseManagementService dbms);

    String validateExternalDatabaseName(String databaseName);

    void cacheDatabaseId(DatabaseIdRepository.Caching databaseIdRepository, NamedDatabaseId namedDatabaseId);

    AccessMode accessMode(CustomAccessMode customAccessMode);

    String username(AuthSubject subject);

    SecurityContext securityContext(
        String username,
        AuthSubject authSubject,
        AccessMode mode,
        String databaseName
    );

    long getHighestPossibleIdInUse(
        RecordStore<? extends AbstractBaseRecord> recordStore,
        KernelTransaction kernelTransaction
    );

    List<StoreScan<NodeLabelIndexCursor>> entityCursorScan(
        KernelTransaction transaction,
        int[] labelIds,
        int batchSize
    );

    PropertyCursor allocatePropertyCursor(KernelTransaction kernelTransaction);

    PropertyReference propertyReference(NodeCursor nodeCursor);

    PropertyReference propertyReference(RelationshipScanCursor relationshipScanCursor);

    PropertyReference noPropertyReference();

    void nodeProperties(
        KernelTransaction kernelTransaction,
        long nodeReference,
        PropertyReference reference,
        PropertyCursor cursor
    );

    void relationshipProperties(
        KernelTransaction kernelTransaction,
        long relationshipReference,
        PropertyReference reference,
        PropertyCursor cursor
    );

    NodeCursor allocateNodeCursor(KernelTransaction kernelTransaction);

    RelationshipScanCursor allocateRelationshipScanCursor(KernelTransaction kernelTransaction);

    NodeLabelIndexCursor allocateNodeLabelIndexCursor(KernelTransaction kernelTransaction);

    NodeValueIndexCursor allocateNodeValueIndexCursor(KernelTransaction kernelTransaction);

    boolean hasNodeLabelIndex(KernelTransaction kernelTransaction);

    StoreScan<NodeLabelIndexCursor> nodeLabelIndexScan(
        KernelTransaction transaction,
        int labelId,
        int batchSize
    );

    <C extends Cursor> StoreScan<C> scanToStoreScan(Scan<C> scan, int batchSize);

    CompatIndexQuery rangeIndexQuery(
        int propertyKeyId,
        double from,
        boolean fromInclusive,
        double to,
        boolean toInclusive
    );

    CompatIndexQuery rangeAllIndexQuery(int propertyKeyId);

    void nodeIndexSeek(
        Read dataRead,
        IndexReadSession index,
        NodeValueIndexCursor cursor,
        IndexOrder indexOrder,
        boolean needsValues,
        CompatIndexQuery query
    ) throws Exception;

    CompositeNodeCursor compositeNodeCursor(List<NodeLabelIndexCursor> cursors, int[] labelIds);

    BatchImporter instantiateBatchImporter(
        BatchImporterFactory factory,
        DatabaseLayout directoryStructure,
        FileSystemAbstraction fileSystem,
        PageCacheTracer pageCacheTracer,
        int writeConcurrency,
        Optional<Long> pageCacheMemory,
        LogService logService,
        ExecutionMonitor executionMonitor,
        AdditionalInitialIds additionalInitialIds,
        Config dbConfig,
        RecordFormats recordFormats,
        JobScheduler jobScheduler,
        Collector badCollector
    );

    Input batchInputFrom(CompatInput compatInput);

    Setting<String> additionalJvm();

    Setting<?> pageCacheMemory();

    Object pageCacheMemoryValue(String value);

    ExecutionMonitor invisibleExecutionMonitor();

    ProcedureSignature procedureSignature(
        QualifiedName name,
        List<FieldSignature> inputSignature,
        List<FieldSignature> outputSignature,
        Mode mode,
        boolean admin,
        String deprecated,
        String[] allowed,
        String description,
        String warning,
        boolean eager,
        boolean caseInsensitive,
        boolean systemProcedure,
        boolean internal,
        boolean allowExpiredCredentials
    );

    long getHighestPossibleNodeCount(Read read, @Nullable IdGeneratorFactory idGeneratorFactory);

    long getHighestPossibleRelationshipCount(Read read, @Nullable IdGeneratorFactory idGeneratorFactory);

    String versionLongToString(long storeVersion);

    TestLog testLog();

    Relationship virtualRelationship(long id, Node startNode, Node endNode, RelationshipType type);
}
