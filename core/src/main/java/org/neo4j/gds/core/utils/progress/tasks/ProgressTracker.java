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
package org.neo4j.gds.core.utils.progress.tasks;

import org.neo4j.gds.core.utils.mem.MemoryRange;

public interface ProgressTracker {

    ProgressTracker NULL_TRACKER = new EmptyProgressTracker();

    void setEstimatedResourceFootprint(MemoryRange memoryEstimationInBytes, int concurrency);

    void beginSubTask();

    void beginSubTask(long taskVolume);

    void beginSubTask(String expectedTaskDescription);

    void endSubTask();

    void endSubTask(String expectedTaskDescription);

    void endSubTaskWithFailure();

    void endSubTaskWithFailure(String expectedTaskDescription);

    void logProgress(long value);

    default void logProgress() {
        logProgress(1);
    }

    void logProgress(long value, String messageTemplate);

    void setVolume(long volume);

    void logDebug(String message);

    void logWarning(String message);

    void logMessage(String message);

    void release();

    class EmptyProgressTracker implements ProgressTracker {

        @Override
        public void setEstimatedResourceFootprint(MemoryRange memoryRangeInBytes, int concurrency) {
        }

        @Override
        public void beginSubTask() {
        }

        @Override
        public void beginSubTask(long taskVolume) {

        }

        @Override
        public void endSubTask() {
        }

        @Override
        public void beginSubTask(String expectedTaskDescription) {

        }

        @Override
        public void endSubTask(String expectedTaskDescription) {

        }

        @Override
        public void logProgress(long value) {
        }

        @Override
        public void logProgress(long value, String messageTemplate) {

        }

        @Override
        public void setVolume(long volume) {
        }

        @Override
        public void logDebug(String message) {

        }

        @Override
        public void logWarning(String message) {

        }

        @Override
        public void logMessage(String message) {

        }

        @Override
        public void release() {
        }

        @Override
        public void endSubTaskWithFailure() {

        }

        @Override
        public void endSubTaskWithFailure(String expectedTaskDescription) {

        }
    }
}
