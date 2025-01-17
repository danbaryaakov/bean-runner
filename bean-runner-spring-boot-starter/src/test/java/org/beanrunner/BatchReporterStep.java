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

package org.beanrunner;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepIcon;
import org.beanrunner.core.annotations.StepSize;
import org.beanrunner.core.annotations.UIConfigurable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@StepSize(25)
@StepIcon("images/step-batch.svg")
public abstract class BatchReporterStep<D> extends Step<D> {

    private long lastExecutionTime;

    @OnSuccess
    private final Step<D> inputStep;

    @JsonProperty
    @UIConfigurable("Batch Size")
    private final int batchSize;

    private final List<D> batch;
    private final Lock lock;
    private final ScheduledExecutorService scheduler;

    public BatchReporterStep(Step<D> inputStep, int batchSize, int batchWindow, TimeUnit batchWindowUnit) {
        this.inputStep = inputStep;
        this.batchSize = batchSize;

        this.batch = new ArrayList<>();
        this.lock = new ReentrantLock();

        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduler.scheduleAtFixedRate(this::flushIfNeeded, batchWindow, batchWindow, batchWindowUnit);
    }

    @Override
    protected void run() {
        lock.lock();
        try {
            batch.add(inputStep.getData());
            if (batch.size() >= batchSize) {
                flush();
            }
        } finally {
            lock.unlock();
        }
    }

    private void flushIfNeeded() {
        lock.lock();
        try {
            if (!batch.isEmpty()) {
                flush();
            }
        } finally {
            lock.unlock();
        }
    }

    private void flush() {
        if (!batch.isEmpty()) {
            List<D> batchToHandle = new ArrayList<>(batch);
            batch.clear();
            handleBatch(batchToHandle);
        }
    }

    protected abstract void handleBatch(List<D> data);
}
