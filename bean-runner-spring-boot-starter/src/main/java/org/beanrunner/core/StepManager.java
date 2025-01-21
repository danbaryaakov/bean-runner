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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.beanrunner.core.annotations.StepGroup;
import org.beanrunner.core.annotations.*;
import org.beanrunner.core.logging.CustomSpringLogbackAppender;
import org.beanrunner.core.storage.StorageService;
import org.beanrunner.core.storage.runs.StepRunStorage;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Component
@Slf4j
public class StepManager {

    @Getter
    private final List<Step<?>> allSteps;
    @Getter
    private final List<Step<?>> firstSteps = new ArrayList<>();
    private Set<String> disabledCronSteps = new HashSet<>();
    private final List<StepListener> listeners = new CopyOnWriteArrayList<>();
    private final Optional<TaskScheduler> scheduler;
    @Getter
    private final Map<Step<?>, List<Step<?>>> stepDependencyTree = new HashMap<>();

    private final StepExecutors executors;

    private final QualifierInspector qualifierInspector;
    private final StepRunStorage stepRunStorage;
    private final Map<Step<?>, LoadedState> flowIdentifierLoadingState = new ConcurrentHashMap<>();
    private final Map<Step<?>, Long> identifierStartLoadTime = new ConcurrentHashMap<>();
    private final Map<FlowRunIdentifier, Long> identifierPropagationLoadingTime = new ConcurrentHashMap<>();
    private final Map<FlowRunIdentifier, LoadedState> identifierLoadedStateMap = new ConcurrentHashMap<>();

    private final CustomSpringLogbackAppender appender;
    private final StorageService storageService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Step<?>, RateCounter> rateCounters = new ConcurrentHashMap<>();

    public StepManager(@Autowired Optional<TaskScheduler> scheduler,
                       @Autowired DynamicBeanRegistrar dynamicBeanRegistrar,
                       @Autowired List<Step<?>> steps,
                       @Autowired QualifierInspector qualifierInspector,
                       @Autowired StepRunStorage stepRunStorage,
                       @Autowired CustomSpringLogbackAppender appender,
                       @Autowired StorageService storageService,
                       @Autowired StepExecutors executors) {
        this.executors = executors;
        this.allSteps = steps;
        this.scheduler = scheduler;
        this.qualifierInspector = qualifierInspector;
        this.stepRunStorage = stepRunStorage;
        this.appender = appender;
        this.storageService = storageService;

        injectStepGroupAutowiring();

        buildStepDependencyTree();

        storageService.read("disabledCronSteps.json").ifPresent(json -> {
            try {
                disabledCronSteps = objectMapper.readValue(json, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to read disabled cron steps", e);
            }
        });

        scheduler.ifPresent(taskScheduler -> firstSteps.forEach(firstStep -> {
            if (firstStep.getClass().isAnnotationPresent(StepSchedule.class)) {
                StepSchedule runAt = firstStep.getClass().getAnnotation(StepSchedule.class);
                taskScheduler.schedule(() -> {
                    if (! disabledCronSteps.contains(qualifierInspector.getQualifierForBean(firstStep))) {
                        executeFlow(firstStep, null, false, "Cron", "images/source-cron.svg");
                    }
                }, new CronTrigger(runAt.value()));
            }
        }));

        for (Step<?> firstStep : firstSteps) {
            executors.addListener(id -> runStopped(firstStep, id));

            flowIdentifierLoadingState.put(firstStep, LoadedState.LOADING);
            CompletableFuture.runAsync(() -> {
                loadFlowIdentifiers(firstStep);
            });
        }

    }

    public void loadFlowIdentifiersFromStorageIfNecessary(Step<?> rootStep) {
        try {
            long now = System.nanoTime();
            long started = identifierStartLoadTime.computeIfAbsent(rootStep, key -> now);
            if (started == now) {
                String flowId = getFlowId(rootStep);
                List<FlowRunIdentifier> identifiers = stepRunStorage.getIdentifiersForFlow(flowId);
                for (FlowRunIdentifier identifier : identifiers) {
                    stepRunStorage.loadIdentifier(flowId, rootStep, identifier);
                    identifier.setOverrideDisplayValues(true);
                    rootStep.contextMap.put(identifier, new StepRunContext<>());
                }
            }
        } catch (Throwable t) {
            log.error("Failed to load flow identifiers", t);
        }
    }

    public boolean isLoaded(FlowRunIdentifier identifier) {
        return identifierLoadedStateMap.get(identifier) == LoadedState.LOADED;
    }

    public boolean loadAndPropagateIdentifierIfNecessary(Step<?> rootStep, FlowRunIdentifier identifier) {
        LoadedState state = identifierLoadedStateMap.compute(identifier, (i, value) -> {
            if (value == null || value == LoadedState.NOT_LOADED) {
                return LoadedState.SHOULD_LOAD;
            }
            if (value == LoadedState.SHOULD_LOAD) {
                return LoadedState.LOADING;
            }
            return value;
        });

        if (state == LoadedState.SHOULD_LOAD) {
            CompletableFuture.runAsync(() -> {
                String flowId = getFlowId(rootStep);
                List<Step<?>> steps = flattenSteps(rootStep);
                stepRunStorage.loadStepContext(flowId, steps, identifier);
                appender.loadLogs(getFlowId(rootStep), steps, identifier);
                identifierLoadedStateMap.put(identifier, LoadedState.LOADED);
                listeners.forEach(listener -> listener.runContentLoaded(rootStep, identifier));
            });
        }

        return state == LoadedState.LOADED;
    }

    public boolean isFlowLoaded(Step<?> firstStep) {
        return flowIdentifierLoadingState.get(firstStep) == LoadedState.LOADED;
    }

    private void loadFlowIdentifiers(Step<?> firstStep) {
        String flowId = getFlowId(firstStep);
        long startTime = System.currentTimeMillis();
        List<FlowRunIdentifier> identifiers = stepRunStorage.getIdentifiersForFlow(flowId);
        log.info("Loading identifiers list took {} ms", System.currentTimeMillis() - startTime);
        for (FlowRunIdentifier identifier : identifiers) {
            identifier.setOverrideDisplayValues(true);
            firstStep.contextMap.put(identifier, new StepRunContext<>());
        }
        flowIdentifierLoadingState.put(firstStep, LoadedState.LOADED);
        listeners.forEach(listener -> listener.flowRunsLoaded(firstStep));
    }

    public boolean isArchived(Step<?> rootStep, FlowRunIdentifier identifier) {
        return !identifierPropagationLoadingTime.containsKey(identifier);
    }

    private void injectStepGroupAutowiring() {
        for (Step<?> step : allSteps) {
            Class<?> stepClass = step.getClass();
            Field[] fields = ReflectionUtils.getFields(stepClass);
            for (Field field : fields) {
                if (field.isAnnotationPresent(StepGroupAutowired.class)) {
                    String stepFullQualifier = qualifierInspector.getQualifierForBean(step);
                    String stepQualifier = "";
                    if (stepFullQualifier.contains("_")) {
                        stepQualifier = stepFullQualifier.substring(stepFullQualifier.indexOf("_") + 1) + "_";
                    }
                    String qualifier = field.getAnnotation(StepGroupAutowired.class).value();
                    field.setAccessible(true);
                    for (Step<?> t : allSteps) {
                        String fullQualifier = qualifierInspector.getQualifierForBean(t);
                        if (fullQualifier.equals(field.getType().getSimpleName() + "_" + stepQualifier + qualifier)) {
                            try {
                                field.set(step, t);
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }
        }
    }

    public void setCronEnabled(Step<?> step, boolean enabled) {
        if (enabled) {
            disabledCronSteps.remove(qualifierInspector.getQualifierForBean(step));
        } else {
            disabledCronSteps.add(qualifierInspector.getQualifierForBean(step));
        }
        try {
            storageService.store("disabledCronSteps.json", objectMapper.writeValueAsString(disabledCronSteps));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        notifyListeners(step, null);
    }

    public boolean isCronEnabled(Step<?> step) {
        return !disabledCronSteps.contains(qualifierInspector.getQualifierForBean(step));
    }

    public void loadIdentifiersFor(Step<?> rootStep) {

    }

    private void buildStepDependencyTree() {

        for (Step<?> step : allSteps) {
            // get the class of the step
            Class<?> stepClass = step.getClass();

            if (stepClass.isAnnotationPresent(StepGroup.class)) {
                StepGroup group = stepClass.getAnnotation(StepGroup.class);
                int groupNumber = group.value() + 10000;
                step.setClusterId(groupNumber);
                ClusterIdGenerator.putClusterDetails(groupNumber, group.name(), group.icon());
            }
            // get the fields of the class
            Field[] fields = ReflectionUtils.getFields(stepClass);
            boolean hasDependencies = false;
            for (Field field : fields) {
                // check if the field has the @OnSuccess annotation
                if (field.isAnnotationPresent(OnSuccess.class) ||
                        field.isAnnotationPresent(OnComplete.class) ||
                        field.isAnnotationPresent(OnUpstreamFailure.class)) {
                    hasDependencies = true;
                    Step<?> dependentStep = null;
                    try {
                        field.setAccessible(true);
                        dependentStep = (Step<?>) field.get(step);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }

                    if (dependentStep != null) {
                        // add the dependent step to the step dependency tree
                        stepDependencyTree.computeIfAbsent(dependentStep, k -> new ArrayList<>()).add(step);
                    }
                }
            }
            if (!hasDependencies) {
                firstSteps.add(step);
            }
        }
    }

    public FlowRunIdentifier generateRunIdentifier() {
        return new FlowRunIdentifier();
    }

    public <D> FlowRunIdentifier executeFlow(Step<D> firstStep, D parameter, boolean isBackground, String source, String sourceIcon) {
        return this.executeFlow(firstStep, parameter, new FlowRunIdentifier(), isBackground, source, sourceIcon);
    }

    public <D> FlowRunIdentifier executeFlow(Step<D> firstStep, D parameter, FlowRunIdentifier identifier, boolean isBackground, String source, String sourceIcon) {
        identifier.setSourceName(source);
        identifier.setSourceIconPath(sourceIcon);
        identifier.setTaskId(firstStep.getClass().getSimpleName());
        identifierLoadedStateMap.put(identifier, LoadedState.LOADED);
        identifierPropagationLoadingTime.put(identifier, System.nanoTime());
        identifier.setRunning(true);
        firstStep.getContext(identifier).setData(parameter);
        identifier.setInvocationType(InvocationType.MANUAL);
        executors.submit(identifier, () -> executeStep(firstStep, identifier, 1));
        identifier.setBackground(isBackground);

        if (! identifier.isBackground()) {
            notifyRunAdded(firstStep, identifier, true);
        } else {
            rateCounters.computeIfAbsent(firstStep, k -> new RateCounter(5000)).recordInvocation();
        }

        return identifier;
    }

    public double getRate(Step<?> firstStep) {
        return rateCounters.computeIfAbsent(firstStep, k -> new RateCounter(5000)).getInvocationsPerSecond();
    }

    public StepStatus getFlowStatus(Step<?> step, FlowRunIdentifier identifier, Step<?> excludeStep) {
        List<Step<?>> flattened = flattenSteps(step);
        if (flattened.stream().filter(t -> t != excludeStep).anyMatch(t -> t.getStatus(identifier) == StepStatus.RUNNING) ||
                flattened.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.READY)) {
            return StepStatus.RUNNING;
        }
        if (flattened.stream().filter(t -> t != excludeStep).anyMatch(t -> t.getStatus(identifier) == StepStatus.FAILED)) {
            return StepStatus.FAILED;
        }
        return StepStatus.SUCCESS;
    }

    public List<Throwable> collectFlowExceptions(Step<?> step, FlowRunIdentifier identifier) {
        log.info("Collecting exceptions for flow {} and identifier {}", getFlowId(step), identifier);
        List<Throwable> exceptions = flattenSteps(step).stream()
                .map(t -> t.getContext(identifier).getException())
                .filter(Objects::nonNull)
                .toList();
        return exceptions;
    }

    private void executeStep(Step<?> step, FlowRunIdentifier flowRunIdentifier, int retiesLeft) {
        boolean success = false;

        if (step.getStatus(flowRunIdentifier) != StepStatus.FAILED_TRANSITIVELY) {
            synchronized (flowRunIdentifier) {
                step.getContext(flowRunIdentifier).setStatus(StepStatus.RUNNING);
                notifyListeners(step, flowRunIdentifier);
            }
            putThreadContextParams(step, flowRunIdentifier);
            try {
                boolean isDone = true;

                step.run();

                try {
                    isDone = step.probe();
                    success = true;
                } catch (Throwable t) {
                    log.error("Exception while probing step {}", step.getClass().getSimpleName(), t);
                    step.getContext(flowRunIdentifier).setException(t);
                }

                if (!isDone) {
                    long interval = step.getProbeInterval(flowRunIdentifier);
                    TimeUnit unit = step.getProbeTimeUnit(flowRunIdentifier);
                    executors.schedule(flowRunIdentifier, () -> executeProbe(step, flowRunIdentifier, Instant.now()), interval, unit);
                    return;
                }
            } catch (Throwable t) {
                log.error("Exception while running step {}", step.getClass().getSimpleName(), t);
                step.getContext(flowRunIdentifier).setException(t);
                if (retiesLeft > 0 && step.getClass().isAnnotationPresent(StepRetry.class)) {
                    StepRetry retry = step.getClass().getAnnotation(StepRetry.class);
                    executors.schedule(flowRunIdentifier, () -> executeStep(step, flowRunIdentifier, retiesLeft - 1), retry.delay(), retry.unit());
                    return;
                }

            }
        }

        if (step.getStatus(flowRunIdentifier) == StepStatus.FAILED_TRANSITIVELY) {
            setStatusAndContinue(step, flowRunIdentifier, StepStatus.FAILED_TRANSITIVELY);
        } else {
            setStatusAndContinue(step, flowRunIdentifier, success ? StepStatus.SUCCESS : StepStatus.FAILED);
        }
    }

    private void putThreadContextParams(Step<?> step, FlowRunIdentifier flowRunIdentifier) {
        MDC.put("task", qualifierInspector.getQualifierForBean(step));
        MDC.put("runId", flowRunIdentifier.getId());
    }

    private void executeProbe(Step<?> step, FlowRunIdentifier flowRunIdentifier, Instant startTime) {
        try {

            synchronized (flowRunIdentifier) {
                if (flowRunIdentifier.isPauseRequested()) {
                    step.getContext(flowRunIdentifier).setStatus(StepStatus.PAUSED_PROBING);
                    notifyListeners(step, flowRunIdentifier);
                    return;
                }
            }

            Duration timeout = step.getProbeTimeout(flowRunIdentifier);

            putThreadContextParams(step, flowRunIdentifier);

            if (Duration.between(startTime, Instant.now()).compareTo(timeout) > 0) {
                setStatusAndContinue(step, flowRunIdentifier, StepStatus.FAILED);
                log.error("Step {} timed out", step.getClass().getSimpleName());
                return;
            }

            boolean canContinue = true;

            try {
                canContinue = step.probe();
            } catch (Throwable t) {
                log.error("Exception while probing step {}", step.getClass().getSimpleName(), t);
                step.getContext(flowRunIdentifier).setException(t);
                setStatusAndContinue(step, flowRunIdentifier, StepStatus.FAILED);
                return;
            }

            if (canContinue) {
                setStatusAndContinue(step, flowRunIdentifier, StepStatus.SUCCESS);
            } else {
                long interval = step.getProbeInterval(flowRunIdentifier);
                TimeUnit unit = step.getProbeTimeUnit(flowRunIdentifier);
                executors.schedule(flowRunIdentifier, () -> executeProbe(step, flowRunIdentifier, startTime), interval, unit);
            }
        } catch (Throwable t) {
            log.error("Exception while probing step {}", step.getClass().getSimpleName(), t);
            setStatusAndContinue(step, flowRunIdentifier, StepStatus.FAILED);
        }

    }

    private void setStatusAndContinue(Step<?> step, FlowRunIdentifier identifier, StepStatus status) {
        synchronized (identifier) {
            step.getContext(identifier).setStatus(status);

            List<Step<?>> dependentSteps = stepDependencyTree.get(step);

            putThreadContextParams(step, identifier);

            boolean isUpstreamBlocked = false;


            isUpstreamBlocked = (dependentSteps != null && dependentSteps.stream().filter(dependentStep -> isOnSuccess(step, dependentStep)).anyMatch(dependentStep -> dependentStep.getStatus(identifier) == StepStatus.FAILED_TRANSITIVELY));

            // handle rewind on error
            if (status == StepStatus.FAILED) {
                FailureBehavior failureBehavior = getFailureBehavior(step);
                if (failureBehavior == FailureBehavior.PAUSE) {
                    identifier.setPauseRequested(true);
                    step.getContext(identifier).setStatus(StepStatus.PAUSED_FAILURE);
                    notifyListeners(step, identifier);
                }
            }

            if (status == StepStatus.SUCCESS && identifier.isPauseRequested()) {
                step.getContext(identifier).setStatus(StepStatus.PAUSED_SUCCESS);
//                System.out.println("setStatusAndContinue: Setting " + step.getClass().getSimpleName() + " to PAUSED_SUCCESS");
                notifyListeners(step, identifier);
                return;
            }

            if (status == StepStatus.FAILED || isUpstreamBlocked) {
                // step has failed or has a dependent task that is blocked due to failure
                if (status == StepStatus.SUCCESS) {
                    try {
                        step.getContext(identifier).setStatus(StepStatus.REWINDING);
                        notifyListeners(step, identifier);

                        step.rewind();
                        step.getContext(identifier).setStatus(StepStatus.REWIND_SUCCESS);
                    } catch (Throwable t) {
                        log.error("Exception while rewinding task {}", step.getClass().getSimpleName(), t);
                        step.getContext(identifier).setStatus(StepStatus.REWIND_FAILED);
                    }
                }
                if (!identifier.isPauseRequested()) {
                    // rewind
                    // go over all backward dependencies
                    for (Field field : ReflectionUtils.getFields(step.getClass())) {
                        if (field.isAnnotationPresent(OnSuccess.class)) {
                            try {
                                field.setAccessible(true);
                                Step<?> dependency = (Step<?>) field.get(step);
                                if (canRewind(dependency, identifier)) {
                                    executors.submit(identifier, () -> rewind(dependency, identifier, true));
                                }
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }
            if (status == StepStatus.FAILED_TRANSITIVELY) {
                propagateFailuresBack(step, identifier);
            }
            // invoke dependent steps (forward direction)
            if (dependentSteps != null) {
                if (!identifier.isPauseRequested() || status == StepStatus.FAILED || status == StepStatus.FAILED_TRANSITIVELY) {
                    for (Step<?> dependentStep : dependentSteps) {
                        if (canExecute(dependentStep, identifier)) {
                            if (dependentStep.getClass().isAnnotationPresent(StepRetry.class)) {
                                StepRetry retry = dependentStep.getClass().getAnnotation(StepRetry.class);
                                executors.submit(identifier, () -> executeStep(dependentStep, identifier, retry.maxRetries()));
                            } else {
                                executors.submit(identifier, () -> executeStep(dependentStep, identifier, 1));
                            }
                        }
                    }
                }
            }

            if (step.getStatus(identifier) == StepStatus.SUCCESS && step.getClass().isAnnotationPresent(StepRewindTrigger.class)) {
                StepRewindTrigger trigger = step.getClass().getAnnotation(StepRewindTrigger.class);
                StepRewindType type = trigger.value();
                if (type == StepRewindType.AUTOMATIC) {
                    step.getContext(identifier).setStatus(StepStatus.PENDING_REWIND);
                    notifyListeners(step, identifier);
                    executors.submit(identifier, () -> rewind(step, identifier, false));
                } else if (type == StepRewindType.MANUAL) {
                    identifier.setRewindArmed(true);
                }
            }
            notifyListeners(step, identifier);
        }
    }

    private boolean isOnSuccess(Step<?> step, Step<?> dependentStep) {
        for (Field field : ReflectionUtils.getFields(dependentStep.getClass())) {
            if (field.isAnnotationPresent(OnSuccess.class)) {
                try {
                    field.setAccessible(true);
                    Step<?> dependency = (Step<?>) field.get(dependentStep);
                    if (dependency == step) {
                        return true;
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return false;
    }

    public void rewindAllRewindableSteps(Step<?> firstStep, FlowRunIdentifier identifier) {
        identifier.setRunning(true);
        identifier.setPaused(false);
        identifier.setPauseRequested(false);

        List<Step<?>> flattened = flattenSteps(firstStep);

        List<Step<?>> pausedFail = flattened.stream().filter(s -> s.getContext(identifier).getStatus() == StepStatus.PAUSED_FAILURE).toList();
        List<Step<?>> pausedSuccess = flattened.stream().filter(s -> s.getContext(identifier).getStatus() == StepStatus.PAUSED_SUCCESS).toList();
        List<Step<?>> pausedProbing = flattened.stream().filter(s -> s.getContext(identifier).getStatus() == StepStatus.PAUSED_PROBING).toList();
        List<Step<?>> pausedRewindSuccess = flattened.stream().filter(s -> s.getContext(identifier).getStatus() == StepStatus.PAUSED_REWIND_SUCCESS).toList();
        List<Step<?>> pausedRewindFailures = flattened.stream().filter(s -> s.getContext(identifier).getStatus() == StepStatus.PAUSED_REWIND_FAILURE).toList();

        pausedRewindFailures.forEach(s -> {
            s.getContext(identifier).setStatus(StepStatus.PENDING_REWIND);
            notifyListeners(s, identifier);
            executors.submit(identifier, () -> rewind(s, identifier, true));
        });

        pausedRewindSuccess.forEach(s -> {
            s.getContext(identifier).setStatus(StepStatus.REWIND_SUCCESS);
            notifyListeners(s, identifier);
            for (Field field : ReflectionUtils.getFields(s.getClass())) {
                if (field.isAnnotationPresent(OnSuccess.class)) {
                    try {
                        field.setAccessible(true);
                        Step<?> dependency = (Step<?>) field.get(s);
                        if (canRewind(dependency, identifier)) {
                            executors.submit(identifier, () -> rewind(dependency, identifier, false));
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });

        if (!pausedFail.isEmpty() || !pausedSuccess.isEmpty() || !pausedProbing.isEmpty()) {
            // flow is paused
            identifier.setPaused(false);
            identifier.setPauseRequested(false);
            for (Step<?> step : pausedSuccess) {
                executors.submit(identifier, () -> rewind(step, identifier, false));
            }
            for (Step<?> step : pausedFail) {
                step.getContext(identifier).setStatus(StepStatus.FAILED);
                notifyListeners(step, identifier);
                for (Field field : ReflectionUtils.getFields(step.getClass())) {
                    if (field.isAnnotationPresent(OnSuccess.class)) {
                        try {
                            field.setAccessible(true);
                            Step<?> dependency = (Step<?>) field.get(step);
                            if (canRewind(dependency, identifier)) {
                                executors.submit(identifier, () -> rewind(dependency, identifier, false));
                            }
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
            for (Step<?> step : pausedProbing) {
                executors.submit(identifier, () -> rewind(step, identifier, false));
            }
        } else {
               List<Step<?>> rewindableSteps = flattened.stream().filter(t -> t.getClass().isAnnotationPresent(StepRewindTrigger.class)).toList();
            for (Step<?> step : rewindableSteps) {
                if (step.getStatus(identifier) == StepStatus.SUCCESS) {
                    executors.submit(identifier, () -> rewind(step, identifier, false));
                }
            }
        }
    }

    private void propagateFailuresBack(Step<?> step, FlowRunIdentifier identifier) {
        synchronized (identifier) {
            for (Field field : ReflectionUtils.getFields(step.getClass())) {
                if (field.isAnnotationPresent(OnSuccess.class)) {
                    try {
                        field.setAccessible(true);
                        Step<?> dependency = (Step<?>) field.get(step);
                        if (dependency.getContext(identifier).getStatus() == StepStatus.NOT_STARTED) {
                            dependency.getContext(identifier).setStatus(StepStatus.FAILED_TRANSITIVELY);
                            notifyListeners(dependency, identifier);
                            propagateFailuresBack(dependency, identifier);
                        } else if (dependency.getContext(identifier).getStatus() == StepStatus.SUCCESS && hasNoActiveDependentSteps(dependency, identifier)) {
                            if (!identifier.isPauseRequested()) {
                                executors.submit(identifier, () -> rewind(dependency, identifier, true));
                            } else {
                                dependency.getContext(identifier).setStatus(StepStatus.PAUSED_SUCCESS);
//                                System.out.println("propagateBackwards: Setting " + step.getClass().getSimpleName() + " to PAUSED_SUCCESS");
                                notifyListeners(dependency, identifier);
                            }
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private boolean hasNoActiveDependentSteps(Step<?> step, FlowRunIdentifier identifier) {
        List<Step<?>> dependentSteps = stepDependencyTree.get(step);
        if (dependentSteps == null) {
            return true;
        }
        return dependentSteps.stream().allMatch(dependentStep -> dependentStep.getStatus(identifier) == StepStatus.NOT_STARTED ||
                dependentStep.getStatus(identifier) == StepStatus.FAILED_TRANSITIVELY);
    }

    public void pause(FlowRunIdentifier identifier) {
        identifier.setPauseRequested(true);
    }

    public void resume(Step<?> firstStep, FlowRunIdentifier identifier) {
        List<Step<?>> flattened = flattenSteps(firstStep);
        identifier.setPaused(false);
        identifier.setPauseRequested(false);
        identifier.setOverrideDisplayValues(false);
        identifier.setRunning(true);

        // mark all transitive failed steps as NOT_STARTED
        List<Step<?>> failedTransitively = flattened.stream().filter(s -> s.getContext(identifier).getStatus() == StepStatus.FAILED_TRANSITIVELY).toList();
        failedTransitively.forEach(s -> {
            s.getContext(identifier).setStatus(StepStatus.NOT_STARTED);
            notifyListeners(s, identifier);
        });
        // TODO: also clear any steps downstream of the transitive failures (onFailure, OnComplete etc)

        List<Step<?>> pausedFail = flattened.stream().filter(s -> s.getContext(identifier).getStatus() == StepStatus.PAUSED_FAILURE).toList();
        List<Step<?>> pausedSuccess = flattened.stream().filter(s -> s.getContext(identifier).getStatus() == StepStatus.PAUSED_SUCCESS).toList();
        List<Step<?>> pausedProbing = flattened.stream().filter(s -> s.getContext(identifier).getStatus() == StepStatus.PAUSED_PROBING).toList();
        List<Step<?>> pausedRewindSuccess = flattened.stream().filter(s -> s.getContext(identifier).getStatus() == StepStatus.PAUSED_REWIND_SUCCESS).toList();
        List<Step<?>> pausedRewindFailures = flattened.stream().filter(s -> s.getContext(identifier).getStatus() == StepStatus.PAUSED_REWIND_FAILURE).toList();

        pausedFail.forEach(s -> {
            s.getContext(identifier).setStatus(StepStatus.READY);
            if (s.getClass().isAnnotationPresent(StepRetry.class)) {
                StepRetry retry = s.getClass().getAnnotation(StepRetry.class);
                executors.submit(identifier, () -> executeStep(s, identifier, retry.maxRetries()));
            } else {
                executors.submit(identifier, () -> executeStep(s, identifier, 1));
            }
        });

        pausedSuccess.forEach(s -> {
            setStatusAndContinue(s, identifier, StepStatus.SUCCESS);
        });

        pausedProbing.forEach(s -> {
            long interval = s.getProbeInterval(identifier);
            TimeUnit unit = s.getProbeTimeUnit(identifier);
            s.getContext(identifier).setStatus(StepStatus.RUNNING);
            notifyListeners(s, identifier);
            executors.schedule(identifier, () -> executeProbe(s, identifier, Instant.now()), interval, unit);
        });

        pausedRewindFailures.forEach(s -> {
            s.getContext(identifier).setStatus(StepStatus.PENDING_REWIND);
            notifyListeners(s, identifier);
            executors.submit(identifier, () -> rewind(s, identifier, true));
        });

        pausedRewindSuccess.forEach(s -> {
            s.getContext(identifier).setStatus(StepStatus.REWIND_SUCCESS);
            notifyListeners(s, identifier);
            for (Field field : ReflectionUtils.getFields(s.getClass())) {
                if (field.isAnnotationPresent(OnSuccess.class)) {
                    try {
                        field.setAccessible(true);
                        Step<?> dependency = (Step<?>) field.get(s);
                        if (canRewind(dependency, identifier)) {
                            executors.submit(identifier, () -> rewind(dependency, identifier, false));
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }

    public boolean canExecute(Step<?> step, FlowRunIdentifier flowRunIdentifier) {
        synchronized (flowRunIdentifier) {
            StepRunContext<?> context = step.getContext(flowRunIdentifier);

            if (context.getStatus() != StepStatus.NOT_STARTED) {
                return false;
            }

            Class<?> taskClass = step.getClass();

            StepLogicOperator operator = StepLogicOperator.AND;

            if (taskClass.isAnnotationPresent(StepTriggerLogic.class)) {
                operator  = taskClass.getAnnotation(StepTriggerLogic.class).value();
            }

            boolean canProceed = operator == StepLogicOperator.AND;
            boolean allConditionsMet = operator == StepLogicOperator.AND;


            if (! canProceed) {
                log.info("Step {} can't proceed", taskClass.getSimpleName());
            }

            // get the fields of the class
            Field[] fields = ReflectionUtils.getFields(taskClass);
            for (Field field : fields) {
                // check if the field has the @OnSuccess annotation
                if (field.isAnnotationPresent(OnSuccess.class)) {
                    try {
                        field.setAccessible(true);
                        Step<?> value = (Step<?>) field.get(step);
                        if (operator == StepLogicOperator.AND) {
                            if (value.getStatus(flowRunIdentifier).isFailed() || value.getStatus(flowRunIdentifier) == StepStatus.FAILED_TRANSITIVELY) {
                                allConditionsMet = false;
                            }
                            if (value.getStatus(flowRunIdentifier) == StepStatus.RUNNING || value.getStatus(flowRunIdentifier) == StepStatus.READY ||
                                    value.getStatus(flowRunIdentifier) == StepStatus.NOT_STARTED ||
                                    (value.getStatus(flowRunIdentifier) == StepStatus.SUCCESS && StringUtils.isNotEmpty(field.getAnnotation(OnSuccess.class).value()) && !field.getAnnotation(OnSuccess.class).value().equals(value.getContext(flowRunIdentifier).getResult())) ||
                                    (value.getStatus(flowRunIdentifier) == StepStatus.FAILED && !field.getAnnotation(OnSuccess.class).value().equals(value.getContext(flowRunIdentifier).getResult()))) {
                                canProceed = false;
                            }
                        } else if (operator == StepLogicOperator.OR) {
                            if (value.getStatus(flowRunIdentifier) == StepStatus.SUCCESS && field.getAnnotation(OnSuccess.class).value().equals(value.getContext(flowRunIdentifier).getResult())) {
                                allConditionsMet = true;
                                canProceed = true;
                                break;
                            }
                            if (value.getStatus(flowRunIdentifier).isFailed() || value.getStatus(flowRunIdentifier) == StepStatus.FAILED_TRANSITIVELY) {
                                canProceed = true;
                            }
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (field.isAnnotationPresent(OnComplete.class)) {
                    try {
                        field.setAccessible(true);
                        Step<?> value = (Step<?>) field.get(step);
                        if (operator == StepLogicOperator.AND) {
                            if (value.getContext(flowRunIdentifier).getStatus() != StepStatus.SUCCESS &&
                                    value.getContext(flowRunIdentifier).getStatus() != StepStatus.FAILED &&
                                    value.getContext(flowRunIdentifier).getStatus() != StepStatus.FAILED_TRANSITIVELY) {
                                canProceed = false;
                            }
                        } else if (operator == StepLogicOperator.OR) {
                            if (value.getContext(flowRunIdentifier).getStatus() == StepStatus.SUCCESS ||
                                    value.getContext(flowRunIdentifier).getStatus() == StepStatus.FAILED ||
                                    value.getContext(flowRunIdentifier).getStatus() == StepStatus.FAILED_TRANSITIVELY) {
                                canProceed = true;
                                allConditionsMet = true;
                                break;
                            }
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (field.isAnnotationPresent(OnUpstreamFailure.class)) {
                    try {
                        field.setAccessible(true);
                        Step<?> value = (Step<?>) field.get(step);
                        StepStatus status = value.getContext(flowRunIdentifier).getStatus();
                        if (operator == StepLogicOperator.AND) {
                            if (status != StepStatus.FAILED && status != StepStatus.FAILED_TRANSITIVELY) {
                                allConditionsMet = false;
                            }
                            if (status == StepStatus.RUNNING || status == StepStatus.READY || status == StepStatus.SUCCESS) {
                                canProceed = false;
                            }
                        } else if (operator == StepLogicOperator.OR) {
                            if (status == StepStatus.FAILED || status == StepStatus.FAILED_TRANSITIVELY) {
                                canProceed = true;
                                allConditionsMet = true;
                                break;
                            }
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            if (allConditionsMet) {
                if (canProceed) {
                    context.setStatus(StepStatus.READY);
                }
            } else {
                context.setStatus(StepStatus.FAILED_TRANSITIVELY);
                return true;
            }

            return canProceed;
        }
    }

    private boolean canRewind(Step<?> step, FlowRunIdentifier identifier) {
        putThreadContextParams(step, identifier);
        synchronized (identifier) {
            if (step.getStatus(identifier) != StepStatus.SUCCESS) {
                return false;
            }
            for (Step<?> dependentStep : stepDependencyTree.get(step)) {
                for (Field f : ReflectionUtils.getFields(dependentStep.getClass())) {
                    if (f.isAnnotationPresent(OnSuccess.class)) {
                        try {
                            f.setAccessible(true);
                            Step<?> dependency = (Step<?>) f.get(dependentStep);
                            if (dependency == step && dependentStep.getStatus(identifier) == StepStatus.NOT_STARTED
                                    && (StringUtils.isEmpty(f.getAnnotation(OnSuccess.class).value()))) {
                                return false;
                            }
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                if (dependentStep.getStatus(identifier) == StepStatus.SUCCESS ||
                        dependentStep.getStatus(identifier) == StepStatus.RUNNING ||
                        dependentStep.getStatus(identifier) == StepStatus.READY ||
                        dependentStep.getStatus(identifier) == StepStatus.PENDING_REWIND ||
                        dependentStep.getStatus(identifier) == StepStatus.REWINDING) {
                    return false;
                }
            }
        }
        step.getContext(identifier).setStatus(StepStatus.PENDING_REWIND);
        notifyListeners(step, identifier);
        return true;
    }

    private void rewind(Step<?> step, FlowRunIdentifier identifier, boolean isFailure) {
        putThreadContextParams(step, identifier);
        try {
            synchronized (identifier) {
                step.getContext(identifier).setStatus(StepStatus.REWINDING);
                notifyListeners(step, identifier);
            }
            step.rewind();
            step.getContext(identifier).setStatus(StepStatus.REWIND_SUCCESS);

            if (identifier.isPauseRequested()) {
                step.getContext(identifier).setStatus(StepStatus.PAUSED_REWIND_SUCCESS);
                notifyListeners(step, identifier);
                return;
            }

        } catch (Throwable t) {
            log.error("Exception while rewinding step {}", step.getClass().getSimpleName(), t);
            step.getContext(identifier).setStatus(StepStatus.REWIND_FAILED);

            if (getFailureBehavior(step) == FailureBehavior.PAUSE) {
                identifier.setPauseRequested(true);
            }

            if (identifier.isPauseRequested()) {
                step.getContext(identifier).setStatus(StepStatus.PAUSED_REWIND_FAILURE);
                notifyListeners(step, identifier);
                return;
            }
        }



        for (Field field : ReflectionUtils.getFields(step.getClass())) {
            if (field.isAnnotationPresent(OnSuccess.class)) {
                try {
                    field.setAccessible(true);
                    Step<?> dependency = (Step<?>) field.get(step);
                    if (canRewind(dependency, identifier)) {
                        executors.submit(identifier, () -> rewind(dependency, identifier, isFailure));
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        synchronized (identifier) {
            notifyListeners(step, identifier);
        }
    }

    public StepStatus getFlowStatus(FlowRunIdentifier identifier, List<Step<?>> flowSteps) {
        if (flowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.RUNNING) ||
                flowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.READY)) {
            return StepStatus.RUNNING;
        }
        if (flowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.PENDING_REWIND) ||
                flowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.REWINDING)) {
            return StepStatus.REWINDING;
        }
        if (flowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.PAUSED_FAILURE)) {
            return StepStatus.PAUSED_FAILURE;
        }
        if (flowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.FAILED)) {
            return StepStatus.FAILED;
        }
        if (flowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.PAUSED_PROBING)) {
            return StepStatus.PAUSED_PROBING;
        }
        if (flowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.PAUSED_SUCCESS)) {
            return StepStatus.PAUSED_SUCCESS;
        }

        if (flowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.PAUSED_REWIND_FAILURE)) {
            return StepStatus.PAUSED_REWIND_FAILURE;
        }

        if (flowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.PAUSED_REWIND_SUCCESS)) {
            return StepStatus.PAUSED_REWIND_SUCCESS;
        }

        if (flowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.SUCCESS ||
                t.getStatus(identifier) == StepStatus.REWIND_SUCCESS || t.getStatus(identifier) == StepStatus.REWIND_FAILED)) {
            return StepStatus.SUCCESS;
        }
        return StepStatus.NOT_STARTED;

    }

    public void addListener(StepListener listener) {
        listeners.add(listener);
    }

    public void removeListener(StepListener listener) {
        listeners.remove(listener);
    }

    public void notifyListeners(Step<?> step, FlowRunIdentifier identifier) {
        listeners.forEach(listener -> listener.stepChanged(step, identifier));
    }

    private void runStopped(Step<?> firstStep, FlowRunIdentifier identifier) {
        if (identifier != null && executors.getCounter(identifier) == 0) {
            StepStatus rootStatus = getFlowStatus(identifier, flattenSteps(firstStep));
            if (rootStatus == StepStatus.SUCCESS || rootStatus == StepStatus.FAILED || rootStatus.isPaused()) {
                log.info("Storing step context for flow {} and identifier {}", getFlowId(firstStep), identifier);
                String flowId = getFlowId(firstStep);
                identifier.setFlowStatus(rootStatus);
                identifier.setTags(getTags(firstStep, identifier).stream().map(TaskTagItem::new).toList());
//                System.out.println("Setting identifier to not running");
                identifier.setRunning(false);
                if (identifier.isPauseRequested() && rootStatus.isPaused()) {
                    identifier.setPaused(true);
                    notifyListeners(firstStep, identifier);
                }
                RunRetentionConfig retentionConfig = firstStep.getClass().getAnnotation(RunRetentionConfig.class);
                boolean shouldSave = true;
                if (retentionConfig != null) {
                    if (retentionConfig.clearSuccessfulRuns() && rootStatus == StepStatus.SUCCESS) {
                        shouldSave = false;
                    }
                    if (retentionConfig.clearFailedRuns() && rootStatus == StepStatus.FAILED) {
                        shouldSave = false;
                    }
                }
                if (!identifier.isBackground() || shouldSave) {
                    stepRunStorage.storeIdentifier(flowId, firstStep, identifier);
                    List<Step<?>> flattened = flattenSteps(firstStep);
                    stepRunStorage.storeStepContext(flowId, flattened, identifier);
                    appender.storeLogs(flowId, flattened, identifier);
                } else {
                    executors.schedule(identifier, () -> {
                        deleteRun(firstStep, identifier);
                    }, rootStatus == StepStatus.FAILED ? retentionConfig.failureTTLMillis() : retentionConfig.successfulTTLMillis(), TimeUnit.MILLISECONDS);
                }
            }
//            if (rootStatus != StepStatus.RUNNING && rootStatus != StepStatus.READY && rootStatus != StepStatus.REWINDING && rootStatus != StepStatus.PENDING_REWIND) {
//                if (! identifier.isBackground()) {
//                    listeners.forEach(listener -> listener.stepChanged(step, identifier));
//                }
//            }
            notifyListeners(firstStep, identifier);
        }

    }

    private void deleteRun(Step<?> firstStep, FlowRunIdentifier identifier) {
        List<Step<?>> flattened = flattenSteps(firstStep);
        flattened.forEach(s -> {
            s.contextMap.remove(identifier);
        });
        if (!identifier.isBackground()) {
            listeners.forEach(listener -> listener.runRemoved(firstStep, identifier));
        }
    }

    public List<Step<?>> flattenSteps(Step<?> firstStep) {
        List<Step<?>> visitedSteps = new ArrayList<>();
        return flattenSteps(firstStep, visitedSteps);
    }

    private List<Step<?>> flattenSteps(Step<?> step, List<Step<?>> visitedSteps) {
        if (!visitedSteps.contains(step)) {
            visitedSteps.add(step);
            List<Step<?>> dependentTasks = getChildSteps(step);
            if (dependentTasks != null) {
                dependentTasks.forEach(dt -> flattenSteps(dt, visitedSteps));
            }
        }
        return visitedSteps;
    }

    private String getFlowId(Step<?> step) {
        if (step.getClass().isAnnotationPresent(StepFlow.class)) {
            return step.getClass().getAnnotation(StepFlow.class).value();
        }
        return step.getClass().getSimpleName();
    }

    public void notifyRunAdded(Step<?> step, FlowRunIdentifier identifier, boolean userInitiated) {
        listeners.forEach(listener -> listener.runAdded(step, identifier, userInitiated));
    }

    /*
     * Returns the root step of the step tree that contains the given step.
     */
    public Step<?> getFirstStep(Step<?> step) {
        for (Step<?> firstStep : firstSteps) {
            if (containsStep(firstStep, step)) {
                return firstStep;
            }
        }
        return null;
    }

    /*
     * Returns true if the step tree contains the given step.
     */
    private boolean containsStep(Step<?> firstStep, Step<?> step) {
        if (firstStep.equals(step)) {
            return true;
        }
        List<Step<?>> dependentSteps = stepDependencyTree.get(firstStep);
        if (dependentSteps != null) {
            for (Step<?> dependentStep : dependentSteps) {
                if (containsStep(dependentStep, step)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<Step<?>> getChildSteps(Step<?> step) {
        List<Step<?>> children = stepDependencyTree.get(step);
        if (children == null) {
            return new ArrayList<>();
        }
        return children;
    }

    public List<StepTag> getTags(Step<?> firstStep, FlowRunIdentifier identifier) {
        List<StepTag> tags = new ArrayList<>();
        getTags(firstStep, identifier, tags);
        return tags;
    }

    private void getTags(Step<?> step, FlowRunIdentifier identifier, List<StepTag> tags) {
        StepStatus status = step.getStatus(identifier);
        if (status != StepStatus.NOT_STARTED && status != StepStatus.FAILED_TRANSITIVELY) {
            if (step.getClass().isAnnotationPresent(StepTag.class)) {
                if (!tags.contains(step.getClass().getAnnotation(StepTag.class))) {
                    tags.add(step.getClass().getAnnotation(StepTag.class));
                }
            }
            List<Step<?>> dependentSteps = stepDependencyTree.get(step);
            if (dependentSteps != null) {
                dependentSteps.forEach(dependentStep -> {
                    if (dependentStep.getStatus(identifier) != StepStatus.NOT_STARTED) {
                        getTags(dependentStep, identifier, tags);
                    }
                });
            }
        }
    }

    FailureBehavior getFailureBehavior(Step<?> step) {
        Step<?> firstStep = getFirstStep(step);
        if (firstStep.getClass().isAnnotationPresent(PauseableFlow.class)) {
            return firstStep.getClass().getAnnotation(PauseableFlow.class).value();
        }
        return FailureBehavior.REWIND;
    }

}
