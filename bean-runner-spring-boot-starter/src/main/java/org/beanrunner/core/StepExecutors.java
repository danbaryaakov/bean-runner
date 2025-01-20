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

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class StepExecutors {

    private List<Listener> listeners = new CopyOnWriteArrayList<>();

    private Map<FlowRunIdentifier, AtomicInteger> counters = new ConcurrentHashMap<>();

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(4);

    public int getCounter(FlowRunIdentifier id) {
        return counters.getOrDefault(id, new AtomicInteger()).get();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void execute(FlowRunIdentifier id, Runnable r) {
        counters.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
        executorService.execute(() -> {
            try {
                r.run();
            } finally {
                int count = counters.get(id).decrementAndGet();
                if (count == 0) {
                    listeners.forEach(l -> l.onRunComplete(id));
                }
            }
        });
    }

    public void submit(FlowRunIdentifier id, Runnable c) {
        counters.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
        executorService.submit(() -> {
            try {
                c.run();
            } finally {
                int count = counters.get(id).decrementAndGet();
                if (count == 0) {
                    listeners.forEach(l -> l.onRunComplete(id));
                }
            }
        });
    }

    public void schedule(FlowRunIdentifier id, Runnable r, long delay, TimeUnit unit) {
        counters.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
        scheduledExecutorService.schedule(() -> {
            try {
                r.run();
            } finally {
                int count = counters.get(id).decrementAndGet();
                if (count == 0) {
                    listeners.forEach(l -> l.onRunComplete(id));
                }
            }
        }, delay, unit);
    }

    public interface Listener {
        void onRunComplete(FlowRunIdentifier id);
    }
}
