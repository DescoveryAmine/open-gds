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
package org.neo4j.gds.core.utils.paged;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.gds.api.nodeproperties.DoubleArrayNodeProperties;
import org.neo4j.gds.api.nodeproperties.FloatArrayNodeProperties;
import org.neo4j.gds.api.nodeproperties.LongArrayNodeProperties;
import org.neo4j.gds.mem.MemoryUsage;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.gds.mem.MemoryUsage.sizeOfLongArray;

final class HugeObjectArrayTest extends HugeArrayTestBase<String[], String, HugeObjectArray<String>> {

    private static final int NODE_COUNT = 42;

    @ParameterizedTest
    @MethodSource("floatsArrays")
    void shouldReturnNodePropertiesForFloatArrayValues(HugeObjectArray<float[]> floats) {
        floats.setAll(idx -> {
            var values = new float[13];
            Arrays.fill(values, .37F);
            return values;
        });
        var nodeProperties = floats.asNodeProperties();
        assertThat(nodeProperties)
            .asInstanceOf(InstanceOfAssertFactories.type(FloatArrayNodeProperties.class))
            .describedAs("float properties must return the same size and elements as the underlying array")
            .returns(floats.size(), FloatArrayNodeProperties::size)
            .satisfies(array -> {
                for (int nodeId = 0; nodeId < NODE_COUNT; nodeId++) {
                    assertThat(array.floatArrayValue(nodeId))
                        .as("Elements for id %d must match", nodeId)
                        .containsExactly(floats.get(nodeId));
                }
            });
    }

    @ParameterizedTest
    @MethodSource("doublesArrays")
    void shouldReturnNodePropertiesForDoubleArrayValues(HugeObjectArray<double[]> doubles) {
        doubles.setAll(idx -> {
            var values = new double[13];
            Arrays.fill(values, .37D);
            return values;
        });
        var nodeProperties = doubles.asNodeProperties();
        assertThat(nodeProperties)
            .asInstanceOf(InstanceOfAssertFactories.type(DoubleArrayNodeProperties.class))
            .describedAs("double properties must return the same size and elements as the underlying array")
            .returns(doubles.size(), DoubleArrayNodeProperties::size)
            .satisfies(array -> {
                for (int nodeId = 0; nodeId < NODE_COUNT; nodeId++) {
                    assertThat(array.doubleArrayValue(nodeId))
                        .as("Elements for id %d must match", nodeId)
                        .containsExactly(doubles.get(nodeId));
                }
            });
    }

    @ParameterizedTest
    @MethodSource("longsArrays")
    void shouldReturnNodePropertiesForLongArrayValues(HugeObjectArray<long[]> longs) {
        longs.setAll(idx -> {
            var values = new long[13];
            Arrays.fill(values, 37L);
            return values;
        });
        var nodeProperties = longs.asNodeProperties();
        assertThat(nodeProperties)
            .asInstanceOf(InstanceOfAssertFactories.type(LongArrayNodeProperties.class))
            .describedAs("long properties must return the same size and elements as the underlying array")
            .returns(longs.size(), LongArrayNodeProperties::size)
            .satisfies(array -> {
                for (int nodeId = 0; nodeId < NODE_COUNT; nodeId++) {
                    assertThat(array.longArrayValue(nodeId))
                        .as("Elements for id %d must match", nodeId)
                        .containsExactly(longs.get(nodeId));
                }
            });
    }

    @ParameterizedTest
    @MethodSource("stringArrays")
    void shouldNotSupportNodePropertiesForStringValues(HugeObjectArray<String> array) {
        assertThatThrownBy(array::asNodeProperties)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("This HugeObjectArray can not be converted to node properties.");
    }

    static Stream<HugeObjectArray<float[]>> floatsArrays() {
        return arraysForTest(float[].class);
    }

    static Stream<HugeObjectArray<double[]>> doublesArrays() {
        return arraysForTest(double[].class);
    }

    static Stream<HugeObjectArray<long[]>> longsArrays() {
        return arraysForTest(long[].class);
    }

    static Stream<HugeObjectArray<String>> stringArrays() {
        return arraysForTest(String.class);
    }

    static <T> Stream<HugeObjectArray<T>> arraysForTest(Class<T> type) {
        return Stream.of(
            HugeObjectArray.newSingleArray(type, NODE_COUNT),
            HugeObjectArray.newPagedArray(type, NODE_COUNT)
        );
    }

    @Override
    HugeObjectArray<String> singleArray(final int size) {
        return HugeObjectArray.newSingleArray(String.class, size);
    }

    @Override
    HugeObjectArray<String> pagedArray(final int size) {
        return HugeObjectArray.newPagedArray(String.class, size);
    }

    @Override
    long bufferSize(final int size) {
        return MemoryUsage.sizeOfObjectArray(size);
    }

    @Override
    String box(final int value) {
        return value + "";
    }

    @Override
    int unbox(final String value) {
        return value == null ? 0 : Integer.parseInt(value);
    }

    @Override
    String primitiveNull() {
        return null;
    }

    @ParameterizedTest
    // {42, ArrayUtil.MAX_ARRAY_LENGTH + 42}
    @ValueSource(longs = {42, 268435498})
    void shouldComputeMemoryEstimation(long elementCount) {
        var elementEstimation = sizeOfLongArray(42);
        var lowerBoundEstimate = elementCount * elementEstimation;

        var estimation = HugeObjectArray.memoryEstimation(elementCount, elementEstimation);
        assertThat(estimation).isCloseTo(lowerBoundEstimate, Percentage.withPercentage(2));
    }
}
