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
public class TaskManager {

    private final List<TaskListener> listeners = new CopyOnWriteArrayList<>();
    private final Optional<TaskScheduler> scheduler;
    @Getter
    private final List<Step<?>> tasks;
    private final Map<Step<?>, List<Step<?>>> taskDependencyTree = new HashMap<>();
    @Getter
    private final List<Step<?>> rootTasks = new ArrayList<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(4);
    private final QualifierInspector qualifierInspector;

    private final StepRunStorage stepRunStorage;

    private final Map<Step<?>, Long> identifierStartLoadTime = new ConcurrentHashMap<>();
    private final Map<TaskRunIdentifier, Long> identifierPropagationLoadingTime = new ConcurrentHashMap<>();
    private final CustomSpringLogbackAppender appender;
    private final StorageService storageService;

    private Set<String> disabledCronSteps = new HashSet<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public TaskManager(@Autowired Optional<TaskScheduler> scheduler,
                       @Autowired DynamicBeanRegistrar dynamicBeanRegistrar,
                       @Autowired List<Step<?>> tasks,
                       @Autowired QualifierInspector qualifierInspector,
                       @Autowired StepRunStorage stepRunStorage,
                       @Autowired CustomSpringLogbackAppender appender,
                       @Autowired StorageService storageService) {
        this.tasks = tasks;
        this.scheduler = scheduler;
        this.qualifierInspector = qualifierInspector;
        this.stepRunStorage = stepRunStorage;
        this.appender = appender;
        this.storageService = storageService;
        injectPostAutowiredDependencies();

        buildTaskDependencyTree();

        storageService.read("disabledCronSteps.json").ifPresent(json -> {
            try {
                disabledCronSteps = objectMapper.readValue(json, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                log.error("Failed to read disabled cron steps", e);
            }
        });

        scheduler.ifPresent(taskScheduler -> rootTasks.forEach(rootTask -> {
            if (rootTask.getClass().isAnnotationPresent(StepSchedule.class)) {
                StepSchedule runAt = rootTask.getClass().getAnnotation(StepSchedule.class);
                taskScheduler.schedule(() -> {
                    if (! disabledCronSteps.contains(qualifierInspector.getQualifierForBean(rootTask))) {
                        execute(rootTask, null, true, "Cron", "images/source-cron.svg");
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
            List<TaskRunIdentifier> identifiers = stepRunStorage.getIdentifiersForFlow(flowId);
            for (TaskRunIdentifier identifier : identifiers) {
                stepRunStorage.loadStepContext(flowId, rootStep, identifier);
                stepRunStorage.loadIdentifier(flowId, rootStep, identifier);
                identifier.setOverrideDisplayValues(true);
            }
        }
    }

    public void loadAndPropagateIdentifierIfNecessary(Step<?> rootStep, TaskRunIdentifier identifier) {
        long now = System.nanoTime();
        long started = identifierPropagationLoadingTime.computeIfAbsent(identifier, key -> now);
        if (started == now) {
            String flowId = getFlowId(rootStep);
            List<Step<?>> tasks = flattenTasks(rootStep);
            for (Step<?> task : tasks) {
                stepRunStorage.loadStepContext(flowId, task, identifier);
                appender.loadLogs(getFlowId(rootStep), task, identifier);
            }
        }
    }

    public boolean isArchived(Step<?> rootStep, TaskRunIdentifier identifier) {
        return !identifierPropagationLoadingTime.containsKey(identifier);
    }

    private void injectPostAutowiredDependencies() {
        for (Step<?> task : tasks) {
            Class<?> taskClass = task.getClass();
            Field[] fields = ReflectionUtils.getFields(taskClass);
            for (Field field : fields) {
                if (field.isAnnotationPresent(StepGroupAutowired.class)) {
                    String taskFullQualifier = qualifierInspector.getQualifierForBean(task);
                    String stepQualifier = "";
                    if (taskFullQualifier.contains("_")) {
                        stepQualifier = taskFullQualifier.substring(taskFullQualifier.indexOf("_") + 1) + "_";
                    }
                    String qualifier = field.getAnnotation(StepGroupAutowired.class).value();
                    field.setAccessible(true);
                    for (Step<?> t : tasks) {
                        String fullQualifier = qualifierInspector.getQualifierForBean(t);
                        if (fullQualifier.equals(field.getType().getSimpleName() + "_" + stepQualifier + qualifier)) {
                            try {
                                field.set(task, t);
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

    private void buildTaskDependencyTree() {

        for (Step<?> task : tasks) {
            // get the class of the task
            Class<?> taskClass = task.getClass();

            if (taskClass.isAnnotationPresent(StepGroup.class)) {
                StepGroup group = taskClass.getAnnotation(StepGroup.class);
                int groupNumber = group.value() + 10000;
                task.setClusterId(groupNumber);
                ClusterIdGenerator.putClusterDetails(groupNumber, group.name(), group.icon());
            }
            // get the fields of the class
            Field[] fields = ReflectionUtils.getFields(taskClass);
            boolean hasDependencies = false;
            for (Field field : fields) {
                // check if the field has the @OnSuccess annotation
                if (field.isAnnotationPresent(OnSuccess.class) ||
                        field.isAnnotationPresent(OnComplete.class) ||
                        field.isAnnotationPresent(OnUpstreamFailure.class)) {
                    hasDependencies = true;
                    // get the class of the field
                    Class<?> fieldClass = field.getType();

                    // find the task that corresponds to the field class
//                    Step<?> dependentTask = tasks.stream()
//                        .filter(t -> t.getClass().equals(fieldClass))
//                        .findFirst()
//                        .orElse(null);
                    Step<?> dependentTask = null;
                    try {
                        field.setAccessible(true);
                        dependentTask = (Step<?>) field.get(task);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }

                    if (dependentTask != null) {
                        // add the dependent task to the task dependency tree
                        taskDependencyTree.computeIfAbsent(dependentTask, k -> new ArrayList<>()).add(task);
                    }
                }
            }
            if (!hasDependencies) {
                rootTasks.add(task);
            }
        }
    }

    public <D> TaskRunIdentifier execute(Step<D> task, D parameter, boolean isBackground, String source, String sourceIcon) {
        TaskRunIdentifier identifier = new TaskRunIdentifier();
        identifier.setSourceName(source);
        identifier.setSourceIconPath(sourceIcon);
        identifier.setTaskId(task.getClass().getSimpleName());
        identifierPropagationLoadingTime.put(identifier, System.nanoTime());
        identifier.setRunning(true);
        task.getContext(identifier).setData(parameter);
        identifier.setInvocationType(InvocationType.MANUAL);
        executorService.submit(() -> executeTask(task, identifier, 1));
        notifyRunAdded(task, identifier, !isBackground);
        return identifier;
    }

    public TaskStatus getFlowStatus(Step<?> step, TaskRunIdentifier identifier, Step<?> excludeStep) {
        List<Step<?>> flattened = flattenTasks(step);
        if (flattened.stream().filter(t -> t != excludeStep).anyMatch(t -> t.getStatus(identifier) == TaskStatus.RUNNING) ||
                flattened.stream().anyMatch(t -> t.getStatus(identifier) == TaskStatus.READY)) {
            return TaskStatus.RUNNING;
        }
        if (flattened.stream().filter(t -> t != excludeStep).anyMatch(t -> t.getStatus(identifier) == TaskStatus.FAILED)) {
            return TaskStatus.FAILED;
        }
        return TaskStatus.SUCCESS;
    }

    public TaskStatus getFlowStatus(Step<?> task, TaskRunIdentifier taskRunIdentifier, HashSet<Step<?>> visitedTasks) {
        TaskStatus status = task.getStatus(taskRunIdentifier);
        List<Step<?>> dependentTasks = taskDependencyTree.get(task);
        if (status == TaskStatus.FAILED) {
            return TaskStatus.FAILED;
        }
        if (status == TaskStatus.RUNNING || status == TaskStatus.READY) {
            return TaskStatus.RUNNING;
        }
        if (dependentTasks != null) {
            List<TaskStatus> statuses = dependentTasks.stream().map(dependentTask -> getTaskStatus(dependentTask, taskRunIdentifier, visitedTasks, false)).toList();
            if (statuses.contains(TaskStatus.RUNNING) || statuses.contains(TaskStatus.READY)) {
                return TaskStatus.RUNNING;
            }
            if (statuses.contains(TaskStatus.FAILED)) {
                return TaskStatus.FAILED;
            }
        }

        return status;
    }

    public List<Throwable> collectFlowExceptions(Step<?> step, TaskRunIdentifier identifier) {
        log.info("Collecting exceptions for flow {} and identifier {}", getFlowId(step), identifier);
        List<Throwable> exceptions = flattenTasks(step).stream()
                .map(t -> t.getContext(identifier).getException())
                .filter(Objects::nonNull)
                .toList();
        return exceptions;
    }

    private void executeTask(Step<?> task, TaskRunIdentifier taskRunIdentifier, int retiesLeft) {
        boolean success = false;

        if (task.getStatus(taskRunIdentifier) != TaskStatus.FAILED_TRANSITIVELY) {
            synchronized (taskRunIdentifier) {
                task.getContext(taskRunIdentifier).setStatus(TaskStatus.RUNNING);
                notifyListeners(task, taskRunIdentifier);
            }
            putThreadContextParams(task, taskRunIdentifier);
            try {
                boolean isDone = true;

                task.run();

                try {
                    isDone = task.probe();
                    success = true;
                } catch (Throwable t) {
                    log.error("Exception while probing task {}", task.getClass().getSimpleName(), t);
                    task.getContext(taskRunIdentifier).setException(t);
                }

                if (!isDone) {
                    long interval = task.getProbeInterval(taskRunIdentifier);
                    TimeUnit unit = task.getProbeTimeUnit(taskRunIdentifier);
                    scheduledExecutorService.schedule(() -> executeProbe(task, taskRunIdentifier, Instant.now()), interval, unit);
                    return;
                }
            } catch (Throwable t) {
                log.error("Exception while running task {}", task.getClass().getSimpleName(), t);
                task.getContext(taskRunIdentifier).setException(t);
                if (retiesLeft > 0 && task.getClass().isAnnotationPresent(StepRetry.class)) {
                    StepRetry retry = task.getClass().getAnnotation(StepRetry.class);
                    scheduledExecutorService.schedule(() -> executeTask(task, taskRunIdentifier, retiesLeft - 1), retry.delay(), retry.unit());
                    return;
                }

            }
        }

        if (task.getStatus(taskRunIdentifier) == TaskStatus.FAILED_TRANSITIVELY) {
            setStatusAndContinue(task, taskRunIdentifier, TaskStatus.FAILED_TRANSITIVELY);
        } else {
            setStatusAndContinue(task, taskRunIdentifier, success ? TaskStatus.SUCCESS : TaskStatus.FAILED);
        }
    }

    private void putThreadContextParams(Step<?> task, TaskRunIdentifier taskRunIdentifier) {
        MDC.put("task", qualifierInspector.getQualifierForBean(task));
        MDC.put("runId", taskRunIdentifier.getId());
    }

    private void executeProbe(Step<?> task, TaskRunIdentifier taskRunIdentifier, Instant startTime) {
        try {
            Duration timeout = task.getProbeTimeout(taskRunIdentifier);

            putThreadContextParams(task, taskRunIdentifier);

            if (Duration.between(startTime, Instant.now()).compareTo(timeout) > 0) {
                setStatusAndContinue(task, taskRunIdentifier, TaskStatus.FAILED);
                log.error("Step {} timed out", task.getClass().getSimpleName());
                return;
            }

            boolean canContinue = true;

            try {
                canContinue = task.probe();
            } catch (Throwable t) {
                log.error("Exception while probing task {}", task.getClass().getSimpleName(), t);
                task.getContext(taskRunIdentifier).setException(t);
                setStatusAndContinue(task, taskRunIdentifier, TaskStatus.FAILED);
                return;
            }

            if (canContinue) {
                setStatusAndContinue(task, taskRunIdentifier, TaskStatus.SUCCESS);
            } else {
                long interval = task.getProbeInterval(taskRunIdentifier);
                TimeUnit unit = task.getProbeTimeUnit(taskRunIdentifier);
                scheduledExecutorService.schedule(() -> executeProbe(task, taskRunIdentifier, startTime), interval, unit);
            }
        } catch (Throwable t) {
            log.error("Exception while probing task {}", task.getClass().getSimpleName(), t);
            setStatusAndContinue(task, taskRunIdentifier, TaskStatus.FAILED);
        }

    }

    private void setStatusAndContinue(Step<?> task, TaskRunIdentifier identifier, TaskStatus status) {

        task.getContext(identifier).setStatus(status);

        List<Step<?>> dependentTasks = taskDependencyTree.get(task);

        putThreadContextParams(task, identifier);

        boolean isUpstreamBocked = false;
        synchronized (identifier) {
            isUpstreamBocked = (dependentTasks != null && dependentTasks.stream().filter(dependentTask -> isOnSuccess(task, dependentTask)).anyMatch(dependentTask -> dependentTask.getStatus(identifier) == TaskStatus.FAILED_TRANSITIVELY));
        }
        // handle rewind on error
        if (status == TaskStatus.FAILED || isUpstreamBocked) {
            // task has failed or has a dependent task that is blocked due to failure
            if (status == TaskStatus.SUCCESS) {
                try {
                    synchronized (identifier) {
                        task.getContext(identifier).setStatus(TaskStatus.REWINDING);
                        notifyListeners(task, identifier);
                    }
                    task.rewind();
                    task.getContext(identifier).setStatus(TaskStatus.REWIND_SUCCESS);
                } catch (Throwable t) {
                    log.error("Exception while rewinding task {}", task.getClass().getSimpleName(), t);
                    task.getContext(identifier).setStatus(TaskStatus.REWIND_FAILED);
                }
            }
            synchronized (identifier) {
                // go over all backward dependencies
                for (Field field : ReflectionUtils.getFields(task.getClass())) {
                    if (field.isAnnotationPresent(OnSuccess.class)) {
                        try {
                            field.setAccessible(true);
                            Step<?> dependency = (Step<?>) field.get(task);
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
        if (status == TaskStatus.FAILED_TRANSITIVELY) {
            propagateFailuresBack(task, identifier);
        }
        // invoke dependent tasks (forward direction)
        if (dependentTasks != null) {
            for (Step<?> dependentTask : dependentTasks) {
                if (dependentTask.canExecute(identifier)) {
                    if (dependentTask.getClass().isAnnotationPresent(StepRetry.class)) {
                        StepRetry retry = dependentTask.getClass().getAnnotation(StepRetry.class);
                        executorService.submit(() -> executeTask(dependentTask, identifier, retry.maxRetries()));
                    } else {
                        executorService.submit(() -> executeTask(dependentTask, identifier, 1));
                    }

                }
            }
        }

        synchronized (identifier) {
            if (task.getStatus(identifier) == TaskStatus.SUCCESS && task.getClass().isAnnotationPresent(StepRewindTrigger.class)) {
                StepRewindTrigger trigger = task.getClass().getAnnotation(StepRewindTrigger.class);
                StepRewindType type = trigger.value();
                if (type == StepRewindType.AUTOMATIC) {
                    task.getContext(identifier).setStatus(TaskStatus.PENDING_REWIND);
                    notifyListeners(task, identifier);
                    executorService.submit(() -> rewind(task, identifier, false));
                } else if (type == StepRewindType.MANUAL){
                    identifier.setRewindArmed(true);
                }
            }
            notifyListeners(task, identifier);
        }
    }

    private boolean isOnSuccess(Step<?> task, Step<?> dependentTask) {
        for (Field field : ReflectionUtils.getFields(dependentTask.getClass())) {
            if (field.isAnnotationPresent(OnSuccess.class)) {
                try {
                    field.setAccessible(true);
                    Step<?> dependency = (Step<?>) field.get(dependentTask);
                    if (dependency == task) {
                        return true;
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return false;
    }

    public void rewindAllRewindableSteps(Step<?> task, TaskRunIdentifier identifier) {
        List<Step<?>> rewindableSteps = flattenTasks(task).stream().filter(t -> t.getClass().isAnnotationPresent(StepRewindTrigger.class)).toList();
        for (Step<?> step : rewindableSteps) {
            if (step.getStatus(identifier) == TaskStatus.SUCCESS) {
                executorService.submit(() -> rewind(step, identifier, false));
            }
        }
    }

    private void propagateFailuresBack(Step<?> task, TaskRunIdentifier identifier) {
        synchronized (identifier) {
            for (Field field : ReflectionUtils.getFields(task.getClass())) {
                if (field.isAnnotationPresent(OnSuccess.class)) {
                    try {
                        field.setAccessible(true);
                        Step<?> dependency = (Step<?>) field.get(task);
                        if (dependency.getContext(identifier).getStatus() == TaskStatus.NOT_STARTED) {
                            dependency.getContext(identifier).setStatus(TaskStatus.FAILED_TRANSITIVELY);
                            notifyListeners(dependency, identifier);
                            propagateFailuresBack(dependency, identifier);
                        } else if (dependency.getContext(identifier).getStatus() == TaskStatus.SUCCESS) {
                            executorService.submit(() -> rewind(dependency, identifier, true));
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private boolean canRewind(Step<?> task, TaskRunIdentifier identifier) {
        putThreadContextParams(task, identifier);
        synchronized (identifier) {
            if (task.getStatus(identifier) != TaskStatus.SUCCESS) {
                return false;
            }
            for (Step<?> dependentTask : taskDependencyTree.get(task)) {
                for (Field f : ReflectionUtils.getFields(dependentTask.getClass())) {
                    if (f.isAnnotationPresent(OnSuccess.class)) {
                        try {
                            f.setAccessible(true);
                            Step<?> dependency = (Step<?>) f.get(dependentTask);
                            if (dependency == task && dependentTask.getStatus(identifier) == TaskStatus.NOT_STARTED
                                    && (StringUtils.isEmpty(f.getAnnotation(OnSuccess.class).value()))) {
                                return false;
                            }
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                if (dependentTask.getStatus(identifier) == TaskStatus.SUCCESS ||
                        dependentTask.getStatus(identifier) == TaskStatus.RUNNING ||
                        dependentTask.getStatus(identifier) == TaskStatus.READY ||
                        dependentTask.getStatus(identifier) == TaskStatus.PENDING_REWIND ||
                        dependentTask.getStatus(identifier) == TaskStatus.REWINDING) {
                    return false;
                }
            }
        }
        task.getContext(identifier).setStatus(TaskStatus.PENDING_REWIND);
        notifyListeners(task, identifier);
        return true;
    }

    private void rewind(Step<?> task, TaskRunIdentifier identifier, boolean isFailure) {
        putThreadContextParams(task, identifier);
        try {
            synchronized (identifier) {
                task.getContext(identifier).setStatus(TaskStatus.REWINDING);
                notifyListeners(task, identifier);
            }
            task.rewind();
            task.getContext(identifier).setStatus(TaskStatus.REWIND_SUCCESS);
        } catch (Throwable t) {
            log.error("Exception while rewinding task {}", task.getClass().getSimpleName(), t);
            task.getContext(identifier).setStatus(TaskStatus.REWIND_FAILED);
        }

        for (Field field : ReflectionUtils.getFields(task.getClass())) {
            if (field.isAnnotationPresent(OnSuccess.class)) {
                try {
                    field.setAccessible(true);
                    Step<?> dependency = (Step<?>) field.get(task);
                    if (canRewind(dependency, identifier)) {
                        executorService.submit(() -> rewind(dependency, identifier, isFailure));
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        synchronized (identifier) {
            notifyListeners(task, identifier);
        }
    }

    public TaskStatus getTaskStatus(Step<?> task, TaskRunIdentifier taskRunIdentifier, boolean skipSelf) {
//        List<Step<?>> flattened = flattenTasks(task).stream().filter(t -> t != task || !skipSelf).toList();
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

        return getTaskStatus(task, taskRunIdentifier, new HashSet<>(), skipSelf);
    }

    public TaskStatus getTaskStatus(Step<?> task, TaskRunIdentifier taskRunIdentifier, HashSet<Step<?>> visitedTasks, boolean skipSelf) {
        TaskStatus status = task.getStatus(taskRunIdentifier);
        if (visitedTasks.contains(task)) {
            if (status == TaskStatus.READY || status == TaskStatus.RUNNING) {
                return TaskStatus.RUNNING;
            }
            if (status == TaskStatus.PENDING_REWIND || status == TaskStatus.REWINDING) {
                return TaskStatus.REWINDING;
            }
            return status;
        }
        visitedTasks.add(task);
        List<Step<?>> dependentTasks = taskDependencyTree.get(task);
        if (status == TaskStatus.RUNNING || status == TaskStatus.READY) {
            return TaskStatus.RUNNING;
        }
        if (status == TaskStatus.PENDING_REWIND || status == TaskStatus.REWINDING) {
            return TaskStatus.REWINDING;
        }
        if (dependentTasks != null) {
            List<TaskStatus> statuses = dependentTasks.stream().map(dependentTask -> getTaskStatus(dependentTask, taskRunIdentifier, visitedTasks, false)).toList();
            if (statuses.contains(TaskStatus.RUNNING) || statuses.contains(TaskStatus.READY)) {
                return TaskStatus.RUNNING;
            }
            if (statuses.contains(TaskStatus.PENDING_REWIND) || statuses.contains(TaskStatus.REWINDING)) {
                return TaskStatus.REWINDING;
            }
            if (statuses.contains(TaskStatus.FAILED)) {
                return TaskStatus.FAILED;
            }
        }
        if (status == TaskStatus.REWIND_FAILED || status == TaskStatus.REWIND_SUCCESS) {
            return TaskStatus.SUCCESS;
        }
        if (skipSelf) {
            return TaskStatus.NOT_STARTED;
        }
        return status;
    }

    public void addListener(TaskListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TaskListener listener) {
        listeners.remove(listener);
    }

    public void notifyListeners(Step<?> task, TaskRunIdentifier identifier) {
        listeners.forEach(listener -> listener.taskChanged(task, identifier));
        Step<?> rootStep = getRootTask(task);
        if (identifier != null) {
            TaskStatus rootStatus = getTaskStatus(rootStep, identifier, false);
            if (rootStatus == TaskStatus.SUCCESS || rootStatus == TaskStatus.FAILED) {
//            log.info("Storing step context for flow {} and identifier {}", getFlowId(rootStep), identifier);
                String flowId = getFlowId(rootStep);
                identifier.setFlowStatus(rootStatus);
                identifier.setTags(getTags(rootStep, identifier).stream().map(TaskTagItem::new).toList());
                stepRunStorage.storeIdentifier(flowId, rootStep, identifier);
                flattenTasks(rootStep).forEach(t -> {
                    stepRunStorage.saveStepContext(flowId, t, identifier);
                    appender.storeLogs(flowId, t, identifier);
                });
            }
            if (rootStatus != TaskStatus.RUNNING && rootStatus != TaskStatus.READY && rootStatus != TaskStatus.REWINDING && rootStatus != TaskStatus.PENDING_REWIND) {
                identifier.setRunning(false);
                listeners.forEach(listener -> listener.taskChanged(task, identifier));
            }
        }
    }

    public List<Step<?>> flattenTasks(Step<?> rootTask) {
        List<Step<?>> tasks = new ArrayList<>();
        return flattenTasks(rootTask, tasks);
    }

    private List<Step<?>> flattenTasks(Step<?> rootTask, List<Step<?>> tasks) {
        if (!tasks.contains(rootTask)) {
            tasks.add(rootTask);
            List<Step<?>> dependentTasks = getSubTasks(rootTask);
            if (dependentTasks != null) {
                dependentTasks.forEach(dt -> flattenTasks(dt, tasks));
            }
        }
        return tasks;
    }

    private String getFlowId(Step<?> step) {
        if (step.getClass().isAnnotationPresent(StepFlow.class)) {
            return step.getClass().getAnnotation(StepFlow.class).value();
        }
        return step.getClass().getSimpleName();
    }

    public void notifyRunAdded(Step<?> task, TaskRunIdentifier identifier, boolean userInitiated) {
        listeners.forEach(listener -> listener.runAdded(task, identifier, userInitiated));
    }

    /*
     * Returns the root task of the task tree that contains the given task.
     */
    public Step<?> getRootTask(Step<?> task) {
        for (Step<?> rootTask : rootTasks) {
            if (containsTask(rootTask, task)) {
                return rootTask;
            }
        }
        return null;
    }

    /*
     * Returns true if the task tree contains the given task.
     */
    private boolean containsTask(Step<?> rootTask, Step<?> task) {
        if (rootTask.equals(task)) {
            return true;
        }
        List<Step<?>> dependentTasks = taskDependencyTree.get(rootTask);
        if (dependentTasks != null) {
            for (Step<?> dependentTask : dependentTasks) {
                if (containsTask(dependentTask, task)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<Step<?>> getSubTasks(Step<?> task) {
        List<Step<?>> children = taskDependencyTree.get(task);
        if (children == null) {
            return new ArrayList<>();
        }
        return children;
    }

    public List<StepTag> getTags(Step<?> rootTask, TaskRunIdentifier identifier) {
        List<StepTag> tags = new ArrayList<>();
        getTags(rootTask, identifier, tags);
        return tags;
    }

    private void getTags(Step<?> task, TaskRunIdentifier identifier, List<StepTag> tags) {
        TaskStatus status = task.getStatus(identifier);
        if (status != TaskStatus.NOT_STARTED && status != TaskStatus.FAILED_TRANSITIVELY) {
            if (task.getClass().isAnnotationPresent(StepTag.class)) {
                if (!tags.contains(task.getClass().getAnnotation(StepTag.class))) {
                    tags.add(task.getClass().getAnnotation(StepTag.class));
                }
            }
            List<Step<?>> dependentTasks = taskDependencyTree.get(task);
            if (dependentTasks != null) {
                dependentTasks.forEach(dependentTask -> {
                    if (dependentTask.getStatus(identifier) != TaskStatus.NOT_STARTED) {
                        getTags(dependentTask, identifier, tags);
                    }
                });
            }
        }
    }

}
