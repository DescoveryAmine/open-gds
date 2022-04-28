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

import org.neo4j.storageengine.api.CommandCreationContext;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractInMemoryCommandCreationContext implements CommandCreationContext {

    private final AtomicLong schemaTokens;
    private final AtomicInteger propertyTokens;
    private final AtomicInteger labelTokens;

    public AbstractInMemoryCommandCreationContext() {
        schemaTokens = new AtomicLong(0);
        propertyTokens = new AtomicInteger(0);
        labelTokens = new AtomicInteger(0);
    }

    @Override
    public long reserveNode() {
        throw new UnsupportedOperationException("Creating nodes is not supported");
    }

    @Override
    public long reserveSchema() {
        return schemaTokens.getAndIncrement();
    }

    @Override
    public int reserveLabelTokenId() {
        return labelTokens.getAndIncrement();
    }

    @Override
    public int reservePropertyKeyTokenId() {
        return propertyTokens.getAndIncrement();
    }

    @Override
    public int reserveRelationshipTypeTokenId() {
        throw new UnsupportedOperationException("Creating relationship types is not supported");
    }

    @Override
    public void close() {

    }
}
