/*
 * This file is part of bean-runner.
 *
 * Copyright (C) 2025 Dan Bar-Yaakov
 *
 *  bean-runner is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.concurrent.*;

public class Debouncer {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, DebouncedFutureHolder> scheduledTasks = new ConcurrentHashMap<>();
    private final long delayMillis;

    public Debouncer(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    public void debounce(String scope, Runnable task) {
        DebouncedFutureHolder holder = scheduledTasks.get(scope);

        // If a task for the scope exists, extend its execution time
        if (holder != null && !holder.getFuture().isCancelled() && !holder.getFuture().isDone()) {
            holder.extend();
            return;
        }

        // Otherwise, start a new task
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            // Check if the task is still active
            if (System.currentTimeMillis() - scheduledTasks.get(scope).getLastInvoked() > delayMillis) {
                // Stop calling the task if no recent invocations
                scheduledTasks.remove(scope).getFuture().cancel(false);
            } else {
                // Otherwise, invoke the task
                task.run();
            }
        }, 0, delayMillis, TimeUnit.MILLISECONDS);

        scheduledTasks.put(scope, new DebouncedFutureHolder(future, System.currentTimeMillis()));
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    class DebouncedFutureHolder {
        private ScheduledFuture<?> future;
        private long lastInvoked;

        // Extend the "lifetime" of the task
        public void extend() {
            lastInvoked = System.currentTimeMillis();
        }
    }
}