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

import lombok.Getter;
import org.beanrunner.core.annotations.OnComplete;
import org.beanrunner.core.annotations.StepHidden;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@StepHidden
public class FlowInvoker<P, R> extends Step<R> {

    private final Map<String, BiConsumer<String, R>> asyncCallables = new ConcurrentHashMap<>();
    private final Map<String, BiConsumer<String, List<Throwable>>> errorConsumers = new ConcurrentHashMap<>();

    private final  Map<String, FlowRunIdentifier> taskRunIdentifiers = new ConcurrentHashMap<>();

    @Getter
    private final Step<P> firstStep;

    @OnComplete
    protected final Step<R> lastStep;

    public FlowInvoker(Step<P> firstStep, Step<R> lastStep) {
        this.firstStep = firstStep;
        this.lastStep = lastStep;
    }

    public final synchronized String runAsync(P parameter) {
        return runAsync(parameter, null, null);
    }

    public final synchronized String runAsync(P parameter, BiConsumer<String, R> consumer) {
        return runAsync(parameter, consumer, null);
    }


    public final synchronized String runAsync(P parameter, BiConsumer<String, R> consumer, BiConsumer<String, List<Throwable>> errorConsumer) {
        FlowRunIdentifier identifier = StaticTransactionManagerHolder.getBean(StepManager.class).executeFlow(firstStep, parameter, true, getSourceName(), getSourceIconPath());
        if (consumer != null) {
            asyncCallables.put(identifier.getId(), consumer);
        }
        if (errorConsumer != null) {
            errorConsumers.put(identifier.getId(), errorConsumer);
        }
        taskRunIdentifiers.put(identifier.getId(), identifier);
        return identifier.getId();
    }

    public final R runSync(P parameter) {

        FlowRunIdentifier identifier = new FlowRunIdentifier();
        taskRunIdentifiers.put(identifier.getId(), identifier);

        synchronized (identifier) {

            StaticTransactionManagerHolder.getBean(StepManager.class).executeFlow(firstStep, parameter, identifier, false, getSourceName(), getSourceIconPath());

            try {
                identifier.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            StepStatus status = getFlowStatus(identifier);
            if (status == StepStatus.FAILED) {
                getExceptions(identifier).stream().findFirst().ifPresent(e -> {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    } else {
                        throw new RuntimeException(e);
                    }
                });
            }
            return getData(identifier);
        }
    }

    protected final StepStatus getFlowStatus(FlowRunIdentifier identifier) {
        return StaticTransactionManagerHolder.getBean(StepManager.class).getFlowStatus(firstStep, identifier, this);
    }

    protected final List<Throwable> getExceptions(FlowRunIdentifier identifier) {
        return StaticTransactionManagerHolder.getBean(StepManager.class).collectFlowExceptions(firstStep, identifier);
    }

    @Override
    public final void run() {
        if (lastStep != null) {

            R result = lastStep.getData();
            setData(result);
            // handle async invocation
            String identifier = MDC.get("runId");

            FlowRunIdentifier flowRunIdentifier = taskRunIdentifiers.remove(identifier);

            BiConsumer<String, R> consumer = asyncCallables.remove(identifier);
            BiConsumer<String, List<Throwable>> errorConsumer = errorConsumers.remove(identifier);
            StepStatus flowStatus = getFlowStatus(flowRunIdentifier);
            if (flowStatus == StepStatus.FAILED) {
                List<Throwable> exceptions = getExceptions(flowRunIdentifier);
                if (errorConsumer != null) {
                    errorConsumer.accept(identifier, exceptions);
                }
            } else {
                if (consumer != null) {
                    consumer.accept(identifier, result);
                }
            }

            // wake up sync invocations
            synchronized (flowRunIdentifier) {
                flowRunIdentifier.notifyAll();
            }

        }
    }

    protected String getSourceName() {
        return "Code";
    }

    protected String getSourceIconPath() {
        return "images/code.svg";
    }

}
