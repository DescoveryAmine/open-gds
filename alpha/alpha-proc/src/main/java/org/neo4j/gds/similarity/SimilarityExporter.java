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
package org.neo4j.gds.similarity;

import org.neo4j.gds.core.concurrency.ParallelUtil;
import org.neo4j.gds.core.concurrency.Pools;
import org.neo4j.gds.core.utils.TerminationFlag;
import org.neo4j.gds.results.SimilarityResult;
import org.neo4j.gds.transaction.TransactionContext;
import org.neo4j.gds.utils.StatementApi;
import org.neo4j.kernel.api.KernelTransaction;
import org.neo4j.values.storable.Values;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.neo4j.gds.utils.ExceptionUtil.throwIfUnchecked;

public class SimilarityExporter extends StatementApi {

    private final int propertyId;
    private final int relationshipTypeId;
    private final TerminationFlag terminationFlag;

    public SimilarityExporter(
        TransactionContext tx,
        String relationshipType,
        String propertyName,
        TerminationFlag terminationFlag
    ) {
        super(tx);
        propertyId = getOrCreatePropertyId(propertyName);
        relationshipTypeId = getOrCreateRelationshipId(relationshipType);
        this.terminationFlag = terminationFlag;
    }

    public void export(Stream<SimilarityResult> similarityPairs, long batchSize) {
        writeSequential(similarityPairs, batchSize);
    }

    private void export(SimilarityResult similarityResult) {
        applyInTransaction(statement -> {
            try {
                createRelationship(similarityResult, statement);
            } catch (Exception e) {
                throwIfUnchecked(e);
                throw new RuntimeException(e);
            }
            return null;
        });

    }

    private void export(List<SimilarityResult> similarityResults) {
        applyInTransaction(statement -> {
            terminationFlag.assertRunning();
            long progress = 0L;
            for (SimilarityResult similarityResult : similarityResults) {
                try {
                    createRelationship(similarityResult, statement);
                    ++progress;
                    if (progress % Math.min(TerminationFlag.RUN_CHECK_NODE_COUNT, Math.max(similarityResults.size() / 2, 1)) == 0) {
                        terminationFlag.assertRunning();
                    }
                } catch (Exception e) {
                    throwIfUnchecked(e);
                    throw new RuntimeException(e);
                }
            }
            return null;
        });

    }

    private void createRelationship(SimilarityResult similarityResult, KernelTransaction statement) throws Exception {
        long node1 = similarityResult.item1;
        long node2 = similarityResult.item2;
        long relationshipId = statement.dataWrite().relationshipCreate(node1, relationshipTypeId, node2);

        statement.dataWrite().relationshipSetProperty(
            relationshipId, propertyId, Values.doubleValue(similarityResult.similarity));
    }

    private int getOrCreateRelationshipId(String relationshipType) {
        return applyInTransaction(stmt -> stmt
            .tokenWrite()
            .relationshipTypeGetOrCreateForName(relationshipType));
    }

    private int getOrCreatePropertyId(String propertyName) {
        return applyInTransaction(stmt -> stmt
            .tokenWrite()
            .propertyKeyGetOrCreateForName(propertyName));
    }

    private void writeSequential(Stream<SimilarityResult> similarityPairs, long batchSize) {
        if (batchSize == 1) {
            similarityPairs.forEach(this::export);
        } else {
            Iterator<SimilarityResult> iterator = similarityPairs.iterator();
            do {
                ParallelUtil.run(
                    () -> export(take(iterator, Math.toIntExact(batchSize))),
                    Pools.DEFAULT_SINGLE_THREAD_POOL
                );
            } while (iterator.hasNext());
        }
    }

    private static List<SimilarityResult> take(Iterator<SimilarityResult> iterator, int batchSize) {
        List<SimilarityResult> result = new ArrayList<>(batchSize);
        while (iterator.hasNext() && batchSize-- > 0) {
            result.add(iterator.next());
        }
        return result;
    }


}
