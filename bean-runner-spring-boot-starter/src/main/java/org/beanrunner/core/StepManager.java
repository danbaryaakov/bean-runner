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
    @Getter
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(4);
    private final QualifierInspector qualifierInspector;
    private final StepRunStorage stepRunStorage;
    private final Map<Step<?>, Long> identifierStartLoadTime = new ConcurrentHashMap<>();
    private final Map<FlowRunIdentifier, Long> identifierPropagationLoadingTime = new ConcurrentHashMap<>();
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
                       @Autowired StorageService storageService) {
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
    }

    public void loadFlowIdentifiersFromStorageIfNecessary(Step<?> rootStep) {
        long now = System.nanoTime();
        long started = identifierStartLoadTime.computeIfAbsent(rootStep, key -> now);
        if (started == now) {
            String flowId = getFlowId(rootStep);
            List<FlowRunIdentifier> identifiers = stepRunStorage.getIdentifiersForFlow(flowId);
            for (FlowRunIdentifier identifier : identifiers) {
                stepRunStorage.loadStepContext(flowId, rootStep, identifier);
                stepRunStorage.loadIdentifier(flowId, rootStep, identifier);
                identifier.setOverrideDisplayValues(true);
            }
        }
    }

    public void loadAndPropagateIdentifierIfNecessary(Step<?> rootStep, FlowRunIdentifier identifier) {
        long now = System.nanoTime();
        long started = identifierPropagationLoadingTime.computeIfAbsent(identifier, key -> now);
        if (started == now) {
            String flowId = getFlowId(rootStep);
            List<Step<?>> steps = flattenSteps(rootStep);
            for (Step<?> step : steps) {
                stepRunStorage.loadStepContext(flowId, step, identifier);
                appender.loadLogs(getFlowId(rootStep), step, identifier);
            }
        }
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
        identifierPropagationLoadingTime.put(identifier, System.nanoTime());
        identifier.setRunning(true);
        firstStep.getContext(identifier).setData(parameter);
        identifier.setInvocationType(InvocationType.MANUAL);
        executorService.submit(() -> executeStep(firstStep, identifier, 1));
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

    public StepStatus getFlowStatus(Step<?> firstStep, FlowRunIdentifier flowRunIdentifier, HashSet<Step<?>> visitedSteps) {
        StepStatus status = firstStep.getStatus(flowRunIdentifier);
        List<Step<?>> dependentSteps = stepDependencyTree.get(firstStep);
        if (status == StepStatus.FAILED) {
            return StepStatus.FAILED;
        }
        if (status == StepStatus.RUNNING || status == StepStatus.READY) {
            return StepStatus.RUNNING;
        }
        if (dependentSteps != null) {
            List<StepStatus> statuses = dependentSteps.stream().map(dependentStep -> getStepStatus(dependentStep, flowRunIdentifier, visitedSteps, false)).toList();
            if (statuses.contains(StepStatus.RUNNING) || statuses.contains(StepStatus.READY)) {
                return StepStatus.RUNNING;
            }
            if (statuses.contains(StepStatus.FAILED)) {
                return StepStatus.FAILED;
            }
        }

        return status;
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
                    scheduledExecutorService.schedule(() -> executeProbe(step, flowRunIdentifier, Instant.now()), interval, unit);
                    return;
                }
            } catch (Throwable t) {
                log.error("Exception while running step {}", step.getClass().getSimpleName(), t);
                step.getContext(flowRunIdentifier).setException(t);
                if (retiesLeft > 0 && step.getClass().isAnnotationPresent(StepRetry.class)) {
                    StepRetry retry = step.getClass().getAnnotation(StepRetry.class);
                    scheduledExecutorService.schedule(() -> executeStep(step, flowRunIdentifier, retiesLeft - 1), retry.delay(), retry.unit());
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
                scheduledExecutorService.schedule(() -> executeProbe(step, flowRunIdentifier, startTime), interval, unit);
            }
        } catch (Throwable t) {
            log.error("Exception while probing step {}", step.getClass().getSimpleName(), t);
            setStatusAndContinue(step, flowRunIdentifier, StepStatus.FAILED);
        }

    }

    private void setStatusAndContinue(Step<?> step, FlowRunIdentifier identifier, StepStatus status) {

        step.getContext(identifier).setStatus(status);

        List<Step<?>> dependentSteps = stepDependencyTree.get(step);

        putThreadContextParams(step, identifier);

        boolean isUpstreamBocked = false;
        synchronized (identifier) {
            isUpstreamBocked = (dependentSteps != null && dependentSteps.stream().filter(dependentStep -> isOnSuccess(step, dependentStep)).anyMatch(dependentStep -> dependentStep.getStatus(identifier) == StepStatus.FAILED_TRANSITIVELY));
        }
        // handle rewind on error
        if (status == StepStatus.FAILED || isUpstreamBocked) {
            // step has failed or has a dependent task that is blocked due to failure
            if (status == StepStatus.SUCCESS) {
                try {
                    synchronized (identifier) {
                        step.getContext(identifier).setStatus(StepStatus.REWINDING);
                        notifyListeners(step, identifier);
                    }
                    step.rewind();
                    step.getContext(identifier).setStatus(StepStatus.REWIND_SUCCESS);
                } catch (Throwable t) {
                    log.error("Exception while rewinding task {}", step.getClass().getSimpleName(), t);
                    step.getContext(identifier).setStatus(StepStatus.REWIND_FAILED);
                }
            }
            synchronized (identifier) {
                // go over all backward dependencies
                for (Field field : ReflectionUtils.getFields(step.getClass())) {
                    if (field.isAnnotationPresent(OnSuccess.class)) {
                        try {
                            field.setAccessible(true);
                            Step<?> dependency = (Step<?>) field.get(step);
                            if (canRewind(dependency, identifier)) {
                                executorService.submit(() -> rewind(dependency, identifier, true));
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
            for (Step<?> dependentStep : dependentSteps) {
                if (canExecute(dependentStep, identifier)) {
                    if (dependentStep.getClass().isAnnotationPresent(StepRetry.class)) {
                        StepRetry retry = dependentStep.getClass().getAnnotation(StepRetry.class);
                        executorService.submit(() -> executeStep(dependentStep, identifier, retry.maxRetries()));
                    } else {
                        executorService.submit(() -> executeStep(dependentStep, identifier, 1));
                    }

                }
            }
        }

        synchronized (identifier) {
            if (step.getStatus(identifier) == StepStatus.SUCCESS && step.getClass().isAnnotationPresent(StepRewindTrigger.class)) {
                StepRewindTrigger trigger = step.getClass().getAnnotation(StepRewindTrigger.class);
                StepRewindType type = trigger.value();
                if (type == StepRewindType.AUTOMATIC) {
                    step.getContext(identifier).setStatus(StepStatus.PENDING_REWIND);
                    notifyListeners(step, identifier);
                    executorService.submit(() -> rewind(step, identifier, false));
                } else if (type == StepRewindType.MANUAL){
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
        List<Step<?>> rewindableSteps = flattenSteps(firstStep).stream().filter(t -> t.getClass().isAnnotationPresent(StepRewindTrigger.class)).toList();
        for (Step<?> step : rewindableSteps) {
            if (step.getStatus(identifier) == StepStatus.SUCCESS) {
                executorService.submit(() -> rewind(step, identifier, false));
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
                        } else if (dependency.getContext(identifier).getStatus() == StepStatus.SUCCESS) {
                            executorService.submit(() -> rewind(dependency, identifier, true));
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
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
                            if (value.getStatus(flowRunIdentifier) == StepStatus.FAILED || value.getStatus(flowRunIdentifier) == StepStatus.FAILED_TRANSITIVELY) {
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
                            if (value.getStatus(flowRunIdentifier) == StepStatus.FAILED || value.getStatus(flowRunIdentifier) == StepStatus.FAILED_TRANSITIVELY) {
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
        } catch (Throwable t) {
            log.error("Exception while rewinding step {}", step.getClass().getSimpleName(), t);
            step.getContext(identifier).setStatus(StepStatus.REWIND_FAILED);
        }

        for (Field field : ReflectionUtils.getFields(step.getClass())) {
            if (field.isAnnotationPresent(OnSuccess.class)) {
                try {
                    field.setAccessible(true);
                    Step<?> dependency = (Step<?>) field.get(step);
                    if (canRewind(dependency, identifier)) {
                        executorService.submit(() -> rewind(dependency, identifier, isFailure));
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

    public StepStatus getStepStatus(Step<?> step, FlowRunIdentifier flowRunIdentifier, boolean skipSelf) {
//        List<Step<?>> flattened = flattenTasks(step).stream().filter(t -> t != step || !skipSelf).toList();
//        if (flattened.stream().anyMatch(t -> t.getStatus(taskRunIdentifier) == TaskStatus.RUNNING) ||
//                flattened.stream().anyMatch(t -> t.getStatus(taskRunIdentifier) == TaskStatus.READY) ||
//                flattened.stream().anyMatch(t -> t.getStatus(taskRunIdentifier) == TaskStatus.PENDING_REWIND) ||
//                flattened.stream().anyMatch(t -> t.getStatus(taskRunIdentifier) == TaskStatus.REWINDING)) {
//            return TaskStatus.RUNNING;
//        }
//        if (flattened.stream().anyMatch(t -> t.getStatus(taskRunIdentifier) == TaskStatus.FAILED)) {
//            return TaskStatus.FAILED;
//        }
//        if (flattened.stream().anyMatch(t -> t.getStatus(taskRunIdentifier) == TaskStatus.SUCCESS ||
//                t.getStatus(taskRunIdentifier) == TaskStatus.REWIND_SUCCESS || t.getStatus(taskRunIdentifier) == TaskStatus.REWIND_FAILED)) {
//            return TaskStatus.SUCCESS;
//        }
//        return TaskStatus.NOT_STARTED;

        return getStepStatus(step, flowRunIdentifier, new HashSet<>(), skipSelf);
    }

    public StepStatus getStepStatus(Step<?> step, FlowRunIdentifier flowRunIdentifier, HashSet<Step<?>> visitedSteps, boolean skipSelf) {
        StepStatus status = step.getStatus(flowRunIdentifier);
        if (visitedSteps.contains(step)) {
            if (status == StepStatus.READY || status == StepStatus.RUNNING) {
                return StepStatus.RUNNING;
            }
            if (status == StepStatus.PENDING_REWIND || status == StepStatus.REWINDING) {
                return StepStatus.REWINDING;
            }
            return status;
        }
        visitedSteps.add(step);
        List<Step<?>> dependentSteps = stepDependencyTree.get(step);
        if (status == StepStatus.RUNNING || status == StepStatus.READY) {
            return StepStatus.RUNNING;
        }
        if (status == StepStatus.PENDING_REWIND || status == StepStatus.REWINDING) {
            return StepStatus.REWINDING;
        }
        if (dependentSteps != null) {
            List<StepStatus> statuses = dependentSteps.stream().map(dependentStep -> getStepStatus(dependentStep, flowRunIdentifier, visitedSteps, false)).toList();
            if (statuses.contains(StepStatus.RUNNING) || statuses.contains(StepStatus.READY)) {
                return StepStatus.RUNNING;
            }
            if (statuses.contains(StepStatus.PENDING_REWIND) || statuses.contains(StepStatus.REWINDING)) {
                return StepStatus.REWINDING;
            }
            if (statuses.contains(StepStatus.FAILED)) {
                return StepStatus.FAILED;
            }
        }
        if (status == StepStatus.REWIND_FAILED || status == StepStatus.REWIND_SUCCESS) {
            return StepStatus.SUCCESS;
        }
        if (skipSelf) {
            return StepStatus.NOT_STARTED;
        }
        return status;
    }

    public void addListener(StepListener listener) {
        listeners.add(listener);
    }

    public void removeListener(StepListener listener) {
        listeners.remove(listener);
    }

    public void notifyListeners(Step<?> step, FlowRunIdentifier identifier) {
        listeners.forEach(listener -> listener.stepChanged(step, identifier));
        Step<?> firstStep = getFirstStep(step);
        if (identifier != null) {
            StepStatus rootStatus = getStepStatus(firstStep, identifier, false);
            if (rootStatus == StepStatus.SUCCESS || rootStatus == StepStatus.FAILED) {
//            log.info("Storing step context for flow {} and identifier {}", getFlowId(firstStep), identifier);
                String flowId = getFlowId(firstStep);
                identifier.setFlowStatus(rootStatus);
                identifier.setTags(getTags(firstStep, identifier).stream().map(TaskTagItem::new).toList());

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
                    flattenSteps(firstStep).forEach(t -> {
                        stepRunStorage.saveStepContext(flowId, t, identifier);
                        appender.storeLogs(flowId, t, identifier);
                    });
                } else {
                    scheduledExecutorService.schedule(() -> {
                        deleteRun(firstStep, identifier);
                    }, rootStatus == StepStatus.FAILED ? retentionConfig.failureTTLMillis() : retentionConfig.successfulTTLMillis(), TimeUnit.MILLISECONDS);
                }
            }
            if (rootStatus != StepStatus.RUNNING && rootStatus != StepStatus.READY && rootStatus != StepStatus.REWINDING && rootStatus != StepStatus.PENDING_REWIND) {
                identifier.setRunning(false);
                if (! identifier.isBackground()) {
                    listeners.forEach(listener -> listener.stepChanged(step, identifier));
                }
            }
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

}
