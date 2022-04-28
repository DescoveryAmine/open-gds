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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class WeightsTest {

    @Test
    void shouldTransformListToArray() {
        Double[] values = {1.0, 2.0, 3.0, 4.0};
        List<Double> weightList = Arrays.asList(values);
        assertArrayEquals(new double[]{1.0, 2.0, 3.0, 4.0}, Weights.buildWeights(weightList), 0.01);
    }

    @Test
    void nans() {
        Double[] values = {Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
        List<Double> weightList = Arrays.asList(values);
        double[] actuals = Weights.buildRleWeights(weightList, 3);
        assertArrayEquals(new double[]{Double.POSITIVE_INFINITY, 5.0, Double.NaN}, actuals, 0.01);
    }

    @Test
    void rleWithOneRepeatedValue() {
        Double[] values = {4.0, 4.0};
        List<Double> weightList = Arrays.asList(values);
        double[] actuals = Weights.buildRleWeights(weightList, 1);
        assertArrayEquals(new double[]{Double.POSITIVE_INFINITY, 2.0, 4.0}, actuals, 0.01);
    }

    @Test
    void rleWithMoreThanOneRepeatedValue() {
        Double[] values = {2.0, 2.0, 4.0, 4.0, 6.0, 6.0};
        List<Double> weightList = Arrays.asList(values);
        double[] actuals = Weights.buildRleWeights(weightList, 1);
        assertArrayEquals(new double[]{
                Double.POSITIVE_INFINITY, 2.0, 2.0,
                Double.POSITIVE_INFINITY, 2.0, 4.0,
                Double.POSITIVE_INFINITY, 2.0, 6.0}, actuals, 0.01);
    }

    @Test
    void rleWithMoreThanOneRepeatedValueOfDifferentSizes() {
        Double[] values = {2.0, 2.0, 4.0, 4.0, 4.0, 4.0, 6.0, 6.0};
        List<Double> weightList = Arrays.asList(values);
        double[] actuals = Weights.buildRleWeights(weightList, 1);
        assertArrayEquals(new double[]{
                Double.POSITIVE_INFINITY, 2.0, 2.0,
                Double.POSITIVE_INFINITY, 4.0, 4.0,
                Double.POSITIVE_INFINITY, 2.0, 6.0}, actuals, 0.01);
    }

    @Test
    void rleWithMixedValues() {
        Double[] values = {7.0, 2.0, 2.0, 4.0, 4.0, 6.0, 6.0, 7.0};
        List<Double> weightList = Arrays.asList(values);
        double[] actuals = Weights.buildRleWeights(weightList, 1);
        assertArrayEquals(new double[]{
                7.0,
                Double.POSITIVE_INFINITY, 2.0, 2.0,
                Double.POSITIVE_INFINITY, 2.0, 4.0,
                Double.POSITIVE_INFINITY, 2.0, 6.0,
                7.0}, actuals, 0.01);
    }

    @Test
    void rleWithNoRepeats() {
        Double[] values = {7.0, 2.0, 2.0, 4.0, 4.0, 6.0, 6.0, 7.0};
        List<Double> weightList = Arrays.asList(values);
        double[] actuals = Weights.buildRleWeights(weightList, 5);
        assertArrayEquals(new double[]{7.0, 2.0, 2.0, 4.0, 4.0, 6.0, 6.0, 7.0}, actuals, 0.01);
    }

    @Test
    void rleWithEmptyArray() {
        List<Double> weightList = Collections.emptyList();
        double[] actuals = Weights.buildRleWeights(weightList, 5);
        assertArrayEquals(new double[0], actuals, 0.01);
    }

}
