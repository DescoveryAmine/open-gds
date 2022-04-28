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
package org.neo4j.gds.compat._44;

import org.neo4j.internal.recordstorage.AbstractInMemoryMetaDataProvider;
import org.neo4j.internal.recordstorage.AbstractTransactionIdStore;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.storageengine.api.ClosedTransactionMetadata;

public class InMemoryMetaDataProviderImpl extends AbstractInMemoryMetaDataProvider {
    private final InMemoryTransactionIdStoreImpl transactionIdStore = new InMemoryTransactionIdStoreImpl();

    @Override
    public ClosedTransactionMetadata getLastClosedTransaction() {
        return this.transactionIdStore.getLastClosedTransaction();
    }

    @Override
    public AbstractTransactionIdStore transactionIdStore() {
        return transactionIdStore;
    }

    @Override
    public void transactionClosed(
        long transactionId, long logVersion, long byteOffset, CursorContext cursorContext
    ) {
        this.transactionIdStore().transactionClosed(transactionId, logVersion, byteOffset, cursorContext);
    }

    @Override
    public void resetLastClosedTransaction(
        long transactionId, long logVersion, long byteOffset, boolean missingLogs, CursorContext cursorContext
    ) {
        this.transactionIdStore().resetLastClosedTransaction(
            transactionId,
            logVersion,
            byteOffset,
            missingLogs,
            cursorContext
        );
    }
}
