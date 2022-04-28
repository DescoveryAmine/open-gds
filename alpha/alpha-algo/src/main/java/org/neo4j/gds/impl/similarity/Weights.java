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
package org.neo4j.gds.impl.similarity;

import java.util.Arrays;
import java.util.List;

public final class Weights {

    public static double[] buildWeights(List<Double> weightList) {
        double[] weights = new double[weightList.size()];
        int i = 0;
        for (Number value : weightList) {
            weights[i++] = value.doubleValue();
        }
        return weights;
    }

    public static double[] buildRleWeights(List<Double> weightList, int limit) {
        double[] weights = new double[weightList.size() + (weightList.size() / (limit * 2))];

        double latestValue = Double.POSITIVE_INFINITY;
        int counter = 0;

        int i = 0;
        for (Number value : weightList) {
            if (value.doubleValue() == latestValue || (Double.isNaN(latestValue) && Double.isNaN(value.doubleValue()))) {
                counter++;
            } else {
                if (counter > limit) {
                    weights[i++] = Double.POSITIVE_INFINITY;
                    weights[i++] = counter;
                    weights[i++] = latestValue;
                    counter = 1;
                } else {
                    if (counter > 0) {
                        for (int j = 0; j < counter; j++) {
                            weights[i++] = latestValue;
                        }
                    }
                    counter = 1;
                }
                latestValue = value.doubleValue();
            }
        }

        if (counter > limit) {
            weights[i++] = Double.POSITIVE_INFINITY;
            weights[i++] = counter;
            weights[i++] = latestValue;
        } else {
            for (int j = 0; j < counter; j++) {
                weights[i++] = latestValue;
            }
        }

        return Arrays.copyOf(weights, i);
    }

    public static double[] buildRleWeights(double[] weightList, int limit) {
        double[] weights = new double[weightList.length + (weightList.length / (limit * 2))];

        double latestValue = Double.POSITIVE_INFINITY;
        int counter = 0;

        int i = 0;
        for (double value : weightList) {
            if (value == latestValue || (Double.isNaN(latestValue) && Double.isNaN(value))) {
                counter++;
            } else {
                if (counter > limit) {
                    weights[i++] = Double.POSITIVE_INFINITY;
                    weights[i++] = counter;
                    weights[i++] = latestValue;
                    counter = 1;
                } else {
                    if (counter > 0) {
                        for (int j = 0; j < counter; j++) {
                            weights[i++] = latestValue;
                        }
                    }
                    counter = 1;
                }
                latestValue = value;
            }
        }

        if (counter > limit) {
            weights[i++] = Double.POSITIVE_INFINITY;
            weights[i++] = counter;
            weights[i++] = latestValue;
        } else {
            for (int j = 0; j < counter; j++) {
                weights[i++] = latestValue;
            }
        }

        return Arrays.copyOf(weights, i);
    }

    private Weights() {}
}
