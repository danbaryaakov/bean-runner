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
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.beanrunner.core.annotations.*;
import org.beanrunner.core.settings.ConfigurationSettings;
import org.slf4j.MDC;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class Step<D> implements ConfigurationSettings {

    private final Map<TaskRunIdentifier, TaskContext<D>> contextMap = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private int clusterId;

    protected void run() {
    }

    protected boolean probe() {
        return true;
    }

    protected void rewind() {
    }

    protected final void setRunProperty(String key, String value) {
        TaskRunIdentifier taskRunIdentifier = contextMap.keySet().stream().filter(k -> k.getId().equals(MDC.get("runId"))).findFirst().orElse(null);
        if (taskRunIdentifier != null) {
            taskRunIdentifier.getRunProperties().put(key, value);
        }
    }

    public TaskContext<D> getContext(TaskRunIdentifier taskRunIdentifier) {
        return contextMap.computeIfAbsent(taskRunIdentifier, k -> new TaskContext<>());
    }

    public TaskStatus getStatus(TaskRunIdentifier taskRunIdentifier) {
        TaskContext<D> context = contextMap.get(taskRunIdentifier);
        return context == null ? TaskStatus.NOT_STARTED : context.getStatus();
    }

    public String getResult(TaskRunIdentifier taskRunIdentifier) {
        TaskContext<D> context = contextMap.get(taskRunIdentifier);
        return context == null ? "" : context.getResult();
    }

    public D getData(TaskRunIdentifier taskRunIdentifier) {
        TaskContext<D> context = contextMap.get(taskRunIdentifier);
        return context == null ? null : context.getData();
    }

    public D getData() {
        String runId = MDC.get("runId");
        return contextMap.get(new TaskRunIdentifier(runId)).getData();
    }

    protected void setData(D data) {
        String runId = MDC.get("runId");
        contextMap.get(new TaskRunIdentifier(runId)).setData(data);
    }

    public String getResult() {
        String runId = MDC.get("runId");
        return contextMap.get(new TaskRunIdentifier(runId)).getResult();
    }

    protected void setResult(String result) {
        String runId = MDC.get("runId");
        contextMap.get(new TaskRunIdentifier(runId)).setResult(result);
    }

    protected void setProbeInterval(long interval, TimeUnit unit) {
        String runId = MDC.get("runId");
        contextMap.get(new TaskRunIdentifier(runId)).setProbeInterval(interval);
        contextMap.get(new TaskRunIdentifier(runId)).setProbeTimeUnit(unit);
    }

    protected void setProbeTimeout(Duration timeout) {
        String runId = MDC.get("runId");
        contextMap.get(new TaskRunIdentifier(runId)).setTimeout(timeout);
    }

    public boolean canExecute(TaskRunIdentifier taskRunIdentifier) {
        synchronized (taskRunIdentifier) {
            TaskContext<D> context = getContext(taskRunIdentifier);

            if (context.getStatus() != TaskStatus.NOT_STARTED) {
                return false;
            }

            Class<?> taskClass = this.getClass();

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
                        Step<?> value = (Step<?>) field.get(this);
                        if (operator == StepLogicOperator.AND) {
                            if (value.getStatus(taskRunIdentifier) == TaskStatus.FAILED || value.getStatus(taskRunIdentifier) == TaskStatus.FAILED_TRANSITIVELY) {
                                allConditionsMet = false;
                            }
                            if (value.getStatus(taskRunIdentifier) == TaskStatus.RUNNING || value.getStatus(taskRunIdentifier) == TaskStatus.READY ||
                                    value.getStatus(taskRunIdentifier) == TaskStatus.NOT_STARTED ||
                                    (value.getStatus(taskRunIdentifier) == TaskStatus.SUCCESS && StringUtils.isNotEmpty(field.getAnnotation(OnSuccess.class).value()) && !field.getAnnotation(OnSuccess.class).value().equals(value.getContext(taskRunIdentifier).getResult())) ||
                                    (value.getStatus(taskRunIdentifier) == TaskStatus.FAILED && !field.getAnnotation(OnSuccess.class).value().equals(value.getContext(taskRunIdentifier).getResult()))) {
                                canProceed = false;
                            }
                        } else if (operator == StepLogicOperator.OR) {
                            if (value.getStatus(taskRunIdentifier) == TaskStatus.SUCCESS && field.getAnnotation(OnSuccess.class).value().equals(value.getContext(taskRunIdentifier).getResult())) {
                                allConditionsMet = true;
                                canProceed = true;
                                break;
                            }
                            if (value.getStatus(taskRunIdentifier) == TaskStatus.FAILED || value.getStatus(taskRunIdentifier) == TaskStatus.FAILED_TRANSITIVELY) {
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
                        Step<?> value = (Step<?>) field.get(this);
                        if (operator == StepLogicOperator.AND) {
                            if (value.getContext(taskRunIdentifier).getStatus() != TaskStatus.SUCCESS &&
                                    value.getContext(taskRunIdentifier).getStatus() != TaskStatus.FAILED &&
                                    value.getContext(taskRunIdentifier).getStatus() != TaskStatus.FAILED_TRANSITIVELY) {
                                canProceed = false;
                            }
                        } else if (operator == StepLogicOperator.OR) {
                            if (value.getContext(taskRunIdentifier).getStatus() == TaskStatus.SUCCESS ||
                                    value.getContext(taskRunIdentifier).getStatus() == TaskStatus.FAILED ||
                                    value.getContext(taskRunIdentifier).getStatus() == TaskStatus.FAILED_TRANSITIVELY) {
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
                        Step<?> value = (Step<?>) field.get(this);
                        TaskStatus status = value.getContext(taskRunIdentifier).getStatus();
                        if (operator == StepLogicOperator.AND) {
                            if (status != TaskStatus.FAILED && status != TaskStatus.FAILED_TRANSITIVELY) {
                                allConditionsMet = false;
                            }
                            if (status == TaskStatus.RUNNING || status == TaskStatus.READY || status == TaskStatus.SUCCESS) {
                                canProceed = false;
                            }
                        } else if (operator == StepLogicOperator.OR) {
                            if (status == TaskStatus.FAILED || status == TaskStatus.FAILED_TRANSITIVELY) {
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
                    context.setStatus(TaskStatus.READY);
                }
            } else {
                context.setStatus(TaskStatus.FAILED_TRANSITIVELY);
                return true;
            }

            return canProceed;
        }
    }

    public Set<TaskRunIdentifier> getIdentifiers() {
        return contextMap.keySet();
    }


    public long getProbeInterval(TaskRunIdentifier taskRunIdentifier) {
        return getContext(taskRunIdentifier).getProbeInterval();
    }

    public TimeUnit getProbeTimeUnit(TaskRunIdentifier taskRunIdentifier) {
        return getContext(taskRunIdentifier).getProbeTimeUnit();
    }

    public Duration getProbeTimeout(TaskRunIdentifier taskRunIdentifier) {
        return getContext(taskRunIdentifier).getTimeout();
    }

}
