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
package org.neo4j.gds.ml.util;

import org.apache.commons.math3.random.RandomDataGenerator;
import org.neo4j.gds.core.utils.paged.HugeLongArray;

import java.util.Optional;

public final class ShuffleUtil {

    public static void shuffleHugeLongArray(HugeLongArray data, RandomDataGenerator random) {
        for (long offset = 0; offset < data.size() - 1; offset++) {
            long swapWith = random.nextLong(offset, data.size() - 1);
            long tempValue = data.get(swapWith);
            data.set(swapWith, data.get(offset));
            data.set(offset, tempValue);
        }
    }

    public static RandomDataGenerator createRandomDataGenerator(Optional<Long> randomSeed) {
        var random = new RandomDataGenerator();
        randomSeed.ifPresent(random::reSeed);
        return random;
    }

    private ShuffleUtil() {}
}
