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
import org.jetbrains.annotations.TestOnly;
import org.neo4j.configuration.Config;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.gds.annotation.SuppressForbidden;
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
import java.util.ServiceLoader;

@SuppressForbidden(reason = "This is the best we can do at the moment")
public final class Neo4jProxy {

    private static final Neo4jProxyApi IMPL;

    static {
        var neo4jVersion = GraphDatabaseApiProxy.neo4jVersion();
        Neo4jProxyFactory neo4jProxyFactory = ServiceLoader
            .load(Neo4jProxyFactory.class)
            .stream()
            .map(ServiceLoader.Provider::get)
            .filter(f -> f.canLoad(neo4jVersion))
            .findFirst()
            .orElseThrow(() -> new LinkageError("Could not load the " + Neo4jProxy.class + " implementation for " + neo4jVersion));
        IMPL = neo4jProxyFactory.load();
        var log = LogBuilders.outputStreamLog(
            System.out,
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
        log.info("Loaded compatibility layer: %s", IMPL.getClass());
        log.info("Loaded version: %s", neo4jVersion);
        log.info("Java vendor: %s", System.getProperty("java.vendor"));
        log.info("Java version: %s", System.getProperty("java.version"));
        log.info("Java home: %s", System.getProperty("java.home"));
    }

    public static GdsGraphDatabaseAPI newDb(DatabaseManagementService dbms) {
        return IMPL.newDb(dbms);
    }

    public static String validateExternalDatabaseName(String databaseName) {
        return IMPL.validateExternalDatabaseName(databaseName);
    }

    @TestOnly
    public static void cacheDatabaseId(
        DatabaseIdRepository.Caching databaseIdRepository,
        NamedDatabaseId namedDatabaseId
    ) {
        IMPL.cacheDatabaseId(databaseIdRepository, namedDatabaseId);
    }

    public static AccessMode accessMode(CustomAccessMode customAccessMode) {
        return IMPL.accessMode(customAccessMode);
    }

    public static String username(AuthSubject subject) {
        return IMPL.username(subject);
    }

    // Maybe we should move this to a test-only proxy?
    @TestOnly
    public static SecurityContext securityContext(
        String username,
        AuthSubject authSubject,
        AccessMode mode,
        String databaseName
    ) {
        return IMPL.securityContext(username, authSubject, mode, databaseName);
    }

    public static long getHighestPossibleIdInUse(
        RecordStore<? extends AbstractBaseRecord> recordStore,
        KernelTransaction kernelTransaction
    ) {
        return IMPL.getHighestPossibleIdInUse(recordStore, kernelTransaction);
    }

    public static List<StoreScan<NodeLabelIndexCursor>> entityCursorScan(
        KernelTransaction transaction,
        int[] labelIds,
        int batchSize
    ) {
        return IMPL.entityCursorScan(transaction, labelIds, batchSize);
    }

    public static PropertyCursor allocatePropertyCursor(KernelTransaction kernelTransaction) {
        return IMPL.allocatePropertyCursor(kernelTransaction);
    }

    public static PropertyReference propertyReference(NodeCursor nodeCursor) {
        return IMPL.propertyReference(nodeCursor);
    }

    public static PropertyReference propertyReference(RelationshipScanCursor relationshipScanCursor) {
        return IMPL.propertyReference(relationshipScanCursor);
    }

    public static PropertyReference noPropertyReference() {
        return IMPL.noPropertyReference();
    }

    public static void nodeProperties(
        KernelTransaction kernelTransaction,
        long nodeReference,
        PropertyReference reference,
        PropertyCursor cursor
    ) {
        IMPL.nodeProperties(kernelTransaction, nodeReference, reference, cursor);
    }

    public static void relationshipProperties(
        KernelTransaction kernelTransaction,
        long relationshipReference,
        PropertyReference reference,
        PropertyCursor cursor
    ) {
        IMPL.relationshipProperties(kernelTransaction, relationshipReference, reference, cursor);
    }

    public static NodeCursor allocateNodeCursor(KernelTransaction kernelTransaction) {
        return IMPL.allocateNodeCursor(kernelTransaction);
    }

    public static RelationshipScanCursor allocateRelationshipScanCursor(KernelTransaction kernelTransaction) {
        return IMPL.allocateRelationshipScanCursor(kernelTransaction);
    }

    public static NodeLabelIndexCursor allocateNodeLabelIndexCursor(KernelTransaction kernelTransaction) {
        return IMPL.allocateNodeLabelIndexCursor(kernelTransaction);
    }

    public static NodeValueIndexCursor allocateNodeValueIndexCursor(KernelTransaction kernelTransaction) {
        return IMPL.allocateNodeValueIndexCursor(kernelTransaction);
    }

    public static boolean hasNodeLabelIndex(KernelTransaction kernelTransaction) {
        return IMPL.hasNodeLabelIndex(kernelTransaction);
    }

    public static StoreScan<NodeLabelIndexCursor> nodeLabelIndexScan(
        KernelTransaction transaction,
        int labelId,
        int batchSize
    ) {
        return IMPL.nodeLabelIndexScan(transaction, labelId, batchSize);
    }

    public static <C extends Cursor> StoreScan<C> scanToStoreScan(Scan<C> scan, int batchSize) {
        return IMPL.scanToStoreScan(scan, batchSize);
    }

    public static CompatIndexQuery rangeIndexQuery(
        int propertyKeyId,
        double from,
        boolean fromInclusive,
        double to,
        boolean toInclusive
    ) {
        return IMPL.rangeIndexQuery(propertyKeyId, from, fromInclusive, to, toInclusive);
    }

    public static CompatIndexQuery rangeAllIndexQuery(int propertyKeyId) {
        return IMPL.rangeAllIndexQuery(propertyKeyId);
    }

    public static void nodeIndexSeek(
        Read dataRead,
        IndexReadSession index,
        NodeValueIndexCursor cursor,
        IndexOrder indexOrder,
        boolean needsValues,
        CompatIndexQuery query
    ) throws Exception {
        IMPL.nodeIndexSeek(dataRead, index, cursor, indexOrder, needsValues, query);
    }

    public static CompositeNodeCursor compositeNodeCursor(List<NodeLabelIndexCursor> cursors, int[] labelIds) {
        return IMPL.compositeNodeCursor(cursors, labelIds);
    }

    public static BatchImporter instantiateBatchImporter(
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
    ) {
        return IMPL.instantiateBatchImporter(
            factory,
            directoryStructure,
            fileSystem,
            pageCacheTracer,
            writeConcurrency,
            pageCacheMemory,
            logService,
            executionMonitor,
            additionalInitialIds,
            dbConfig,
            recordFormats,
            jobScheduler,
            badCollector
        );
    }

    public static Input batchInputFrom(CompatInput compatInput) {
        return IMPL.batchInputFrom(compatInput);
    }

    public static Setting<String> additionalJvm() {
        return IMPL.additionalJvm();
    }

    @SuppressWarnings("unchecked")
    public static <T> Setting<T> pageCacheMemory() {
        return (Setting<T>) IMPL.pageCacheMemory();
    }

    @SuppressWarnings("unchecked")
    public static <T> T pageCacheMemoryValue(String value) {
        return (T) IMPL.pageCacheMemoryValue(value);
    }

    public static ExecutionMonitor invisibleExecutionMonitor() {
        return IMPL.invisibleExecutionMonitor();
    }

    public static ProcedureSignature procedureSignature(
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
    ) {
        return IMPL.procedureSignature(
            name,
            inputSignature,
            outputSignature,
            mode,
            admin,
            deprecated,
            allowed,
            description,
            warning,
            eager,
            caseInsensitive,
            systemProcedure,
            internal,
            allowExpiredCredentials
        );
    }

    public static long getHighestPossibleNodeCount(Read read, @Nullable IdGeneratorFactory idGeneratorFactory) {
        return IMPL.getHighestPossibleNodeCount(read, idGeneratorFactory);
    }

    public static long getHighestPossibleRelationshipCount(Read read, @Nullable IdGeneratorFactory idGeneratorFactory) {
        return IMPL.getHighestPossibleRelationshipCount(read, idGeneratorFactory);
    }

    public static String versionLongToString(long storeVersion) {
        return IMPL.versionLongToString(storeVersion);
    }

    public static TestLog testLog() {
        return IMPL.testLog();
    }

    public static Relationship virtualRelationship(long id, Node startNode, Node endNode, RelationshipType type) {
        return IMPL.virtualRelationship(id, startNode, endNode, type);
    }

    private Neo4jProxy() {
        throw new UnsupportedOperationException("No instances");
    }
}
