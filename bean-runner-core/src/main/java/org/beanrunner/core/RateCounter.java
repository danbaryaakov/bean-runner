/*
 * This file is part of bean-runner.
 *
 * Copyright (C) 2025 Dan Bar-Yaakov
 *
 * bean-runner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bean-runner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.beanrunner.core;

public class RateCounter {
    private final long windowMillis;
    private long invocationCount = 0;
    private long startTime;

    public RateCounter(long windowMillis) {
        this.windowMillis = windowMillis;
        this.startTime = System.currentTimeMillis();
    }

    public synchronized void recordInvocation() {
        invocationCount++;
        long currentTime = System.currentTimeMillis();
        if (currentTime - startTime >= windowMillis) {
            resetCounter(currentTime);
        }
    }

    public synchronized double getInvocationsPerSecond() {
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        if (elapsedTime == 0) {
            return 0.0;
        }
        return (invocationCount * 1000.0) / elapsedTime;
    }

    private void resetCounter(long currentTime) {
        invocationCount = 0;
        startTime = currentTime;
    }
}