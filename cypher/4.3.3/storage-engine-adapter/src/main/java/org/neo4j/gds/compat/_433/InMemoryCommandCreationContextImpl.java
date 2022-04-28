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
package org.neo4j.gds.compat._433;

import org.neo4j.gds.compat.AbstractInMemoryCommandCreationContext;
import org.neo4j.io.pagecache.context.CursorContext;
import org.neo4j.lock.LockTracer;
import org.neo4j.lock.ResourceLocker;
import org.neo4j.storageengine.api.txstate.ReadableTransactionState;

public class InMemoryCommandCreationContextImpl extends AbstractInMemoryCommandCreationContext {

    @Override
    public void acquireRelationshipCreationLock(
        ReadableTransactionState txState, ResourceLocker locker, LockTracer lockTracer, long sourceNode, long targetNode
    ) {

    }

    @Override
    public void acquireRelationshipDeletionLock(
        ReadableTransactionState txState,
        ResourceLocker locker,
        LockTracer lockTracer,
        long sourceNode,
        long targetNode,
        long relationship
    ) {
        throw new UnsupportedOperationException("Creating relationships is not supported");
    }

    @Override
    public void acquireNodeDeletionLock(
        ReadableTransactionState txState, ResourceLocker locker, LockTracer lockTracer, long node
    ) {
        throw new UnsupportedOperationException("Creating nodes is not supported");
    }

    @Override
    public long reserveRelationship() {
        throw new UnsupportedOperationException("Creating relationships is not supported");
    }

    @Override
    public void initialize(CursorContext cursorContext) {

    }
}
