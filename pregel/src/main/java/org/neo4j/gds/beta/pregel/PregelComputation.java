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
package org.neo4j.gds.beta.pregel;

import org.neo4j.gds.beta.pregel.context.ComputeContext;
import org.neo4j.gds.beta.pregel.context.InitContext;
import org.neo4j.gds.beta.pregel.context.MasterComputeContext;

import java.util.Optional;

/**
 * Main interface to express user-defined logic using the
 * Pregel framework. An algorithm is expressed using a
 * node-centric view. A node can receive messages from
 * other nodes, change its state and send messages to other
 * nodes in each iteration (superstep).
 *
 * @see Pregel
 * @see <a href="https://kowshik.github.io/JPregel/pregel_paper.pdf">Paper</a>
 */
public interface PregelComputation<C extends PregelConfig> {
    /**
     * The schema describes the node property layout.
     * A node property can be composed of multiple primitive
     * values, such as double or long, as well as arrays of
     * those. Each part of that composite schema is named
     * by a unique key.
     * <br>
     * Example:
     * <pre>
     * public PregelSchema schema(PregelConfig config) {
     *      return new PregelSchema.Builder()
     *          .add("key", ValueType.LONG)
     *          .add("privateKey", ValueType.LONG, Visibility.PRIVATE)
     *          .build();
     * }
     * </pre>
     *
     * @see PregelSchema
     */
    PregelSchema schema(C config);

    /**
     * The init method is called in the beginning of the first
     * superstep (iteration) of the Pregel computation and allows
     * initializing node values.
     * <br>
     * The context parameter provides access to node properties of
     * the in-memory graph and the algorithm configuration.
     */
    default void init(InitContext<C> context) {}

    /**
     * The compute method is called individually for each node
     * in every superstep as long as the node receives messages
     * or has not voted to halt yet.
     * <br>
     * Since a Pregel computation is state-less, a node can only
     * communicate with other nodes via messages. In each super-
     * step, a node receives messages via the input parameter
     * and can send new messages via the context parameter.
     * Messages can be sent to neighbor nodes or any node if the
     * identifier is known.
     */
    void compute(ComputeContext<C> context, Messages messages);

    /**
     * The masterCompute method is called exactly once after every superstep.
     * It is called by a single thread.
     *
     * @return true, iff the computation converged and should stop
     */
    default boolean masterCompute(MasterComputeContext<C> context) {
        return false;
    }

    /**
     * A reducer is used to combine messages sent to a single node. Based on
     * the reduce function, multiple messages are condensed into a single one.
     * Use cases are computing the sum, count, minimum or maximum of messages.
     *
     * Specifying a reducer can significantly reduce memory consumption and
     * runtime of the computation.
     */
    default Optional<Reducer> reducer() {
        return Optional.empty();
    }

    /**
     * If the input graph is weighted, i.e. relationships have a
     * property, this method can be overridden to apply that weight
     * on a message before it is read by the receiving node.
     * <br>
     * If the input graph has no relationship properties, i.e. is
     * unweighted, the method is skipped.
     */
    default double applyRelationshipWeight(double nodeValue, double relationshipWeight) {
        return nodeValue;
    }
}
