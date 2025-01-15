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

import org.beanrunner.core.annotations.OnComplete;
import org.beanrunner.core.annotations.StepHidden;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@StepHidden
public class StepInvoker<P, R> extends Step<R> {

    private final Map<String, Consumer<R>> asyncCallables = new ConcurrentHashMap<>();

    private final  Map<String, TaskRunIdentifier> taskRunIdentifiers = new ConcurrentHashMap<>();

    private final Step<P> firstStep;

    @OnComplete
    protected final Step<R> lastStep;

    public StepInvoker(Step<P> firstStep, Step<R> lastStep) {
        this.firstStep = firstStep;
        this.lastStep = lastStep;
    }

    public final synchronized void runAsync(P parameter) {
        runAsync(parameter, null);
    }

    public final synchronized void runAsync(P parameter, Consumer<R> consumer) {
        TaskRunIdentifier identifier = StaticTransactionManagerHolder.getBean(TaskManager.class).execute(firstStep, parameter, true, getSourceName(), getSourceIconPath());
        if (consumer != null) {
            asyncCallables.put(identifier.getId(), consumer);
        }
        taskRunIdentifiers.put(identifier.getId(), identifier);
    }

    public final R runSync(P parameter) {
        TaskRunIdentifier identifier = null;

        identifier = StaticTransactionManagerHolder.getBean(TaskManager.class).execute(firstStep, parameter, false, getSourceName(), getSourceIconPath());
        taskRunIdentifiers.put(identifier.getId(), identifier);

        synchronized (identifier) {
            if (getData(identifier) != null) {
                return getData(identifier);
            }
            try {
                identifier.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return getData(identifier);
        }
    }

    protected final TaskStatus getFlowStatus() {
        TaskRunIdentifier identifier = new TaskRunIdentifier(MDC.get("runId"));
        return StaticTransactionManagerHolder.getBean(TaskManager.class).getFlowStatus(firstStep, identifier, this);
    }

    protected final List<Throwable> getExceptions() {
        TaskRunIdentifier identifier = new TaskRunIdentifier(MDC.get("runId"));
        return StaticTransactionManagerHolder.getBean(TaskManager.class).collectFlowExceptions(firstStep, identifier);
    }

    @Override
    public final synchronized void run() {
        if (lastStep != null) {
            R result = lastStep.getData();
            setData(result);
            // handle async invocation
            String identifier = MDC.get("runId");
            Consumer<R> consumer = asyncCallables.remove(identifier);
            if (consumer != null) {
                consumer.accept(result);
            }

            // wake up async invocations
            TaskRunIdentifier taskRunIdentifier = taskRunIdentifiers.remove(identifier);
            if (taskRunIdentifier != null) {
                synchronized (taskRunIdentifier) {
                    taskRunIdentifier.notifyAll();
                }
            }
        }
    }

    protected String getSourceName() {
        return "Background";
    }

    protected String getSourceIconPath() {
        return "images/source-background.svg";
    }

}
