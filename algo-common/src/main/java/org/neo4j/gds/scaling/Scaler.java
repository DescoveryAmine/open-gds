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
package org.neo4j.gds.scaling;

import java.util.List;

public interface Scaler {

    double CLOSE_TO_ZERO = 1e-15;

    double scaleProperty(long nodeId);

    int dimension();

    class ArrayScaler implements Scaler {

        private final List<ScalarScaler> elementScalers;

        ArrayScaler(List<ScalarScaler> elementScalers) {
            this.elementScalers = elementScalers;
        }

        public void scaleProperty(long nodeId, double[] result, int offset) {
            for (int i = 0; i < dimension(); i++) {
                result[offset + i] = elementScalers.get(i).scaleProperty(nodeId);
            }
        }

        @Override
        public double scaleProperty(long nodeId) {
            throw new UnsupportedOperationException("Use the other scaleProperty method");
        }

        @Override
        public int dimension() {
            return elementScalers.size();
        }
    }

}
