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
package org.neo4j.gds.graphbuilder;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.util.Random;

/**
 * default builder makes methods
 * from abstract graphBuilder accessible
 */
public class DefaultBuilder extends GraphBuilder<DefaultBuilder> {

    protected DefaultBuilder(
        GraphDatabaseAPI api,
        Transaction tx,
        Label label,
        RelationshipType relationship,
        Random random
    ) {
        super(api, tx, label, relationship, random);
    }

    @Override
    protected DefaultBuilder me() {
        return this;
    }
}
