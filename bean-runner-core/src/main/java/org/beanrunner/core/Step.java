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

    private final Map<FlowRunIdentifier, StepRunContext<D>> contextMap = new ConcurrentHashMap<>();

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
        FlowRunIdentifier flowRunIdentifier = contextMap.keySet().stream().filter(k -> k.getId().equals(MDC.get("runId"))).findFirst().orElse(null);
        if (flowRunIdentifier != null) {
            flowRunIdentifier.getRunProperties().put(key, value);
        }
    }

    public StepRunContext<D> getContext(FlowRunIdentifier flowRunIdentifier) {
        return contextMap.computeIfAbsent(flowRunIdentifier, k -> new StepRunContext<>());
    }

    public StepStatus getStatus(FlowRunIdentifier flowRunIdentifier) {
        StepRunContext<D> context = contextMap.get(flowRunIdentifier);
        return context == null ? StepStatus.NOT_STARTED : context.getStatus();
    }

    public String getResult(FlowRunIdentifier flowRunIdentifier) {
        StepRunContext<D> context = contextMap.get(flowRunIdentifier);
        return context == null ? "" : context.getResult();
    }

    public D getData(FlowRunIdentifier flowRunIdentifier) {
        StepRunContext<D> context = contextMap.get(flowRunIdentifier);
        return context == null ? null : context.getData();
    }

    public D getData() {
        String runId = MDC.get("runId");
        return contextMap.get(new FlowRunIdentifier(runId)).getData();
    }

    protected void setData(D data) {
        String runId = MDC.get("runId");
        contextMap.get(new FlowRunIdentifier(runId)).setData(data);
    }

    public String getResult() {
        String runId = MDC.get("runId");
        return contextMap.get(new FlowRunIdentifier(runId)).getResult();
    }

    protected void setResult(String result) {
        String runId = MDC.get("runId");
        contextMap.get(new FlowRunIdentifier(runId)).setResult(result);
    }

    protected void setProbeInterval(long interval, TimeUnit unit) {
        String runId = MDC.get("runId");
        contextMap.get(new FlowRunIdentifier(runId)).setProbeInterval(interval);
        contextMap.get(new FlowRunIdentifier(runId)).setProbeTimeUnit(unit);
    }

    protected void setProbeTimeout(Duration timeout) {
        String runId = MDC.get("runId");
        contextMap.get(new FlowRunIdentifier(runId)).setTimeout(timeout);
    }

    public boolean canExecute(FlowRunIdentifier flowRunIdentifier) {
        synchronized (flowRunIdentifier) {
            StepRunContext<D> context = getContext(flowRunIdentifier);

            if (context.getStatus() != StepStatus.NOT_STARTED) {
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
                        Step<?> value = (Step<?>) field.get(this);
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
                        Step<?> value = (Step<?>) field.get(this);
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

    public Set<FlowRunIdentifier> getIdentifiers() {
        return contextMap.keySet();
    }


    public long getProbeInterval(FlowRunIdentifier flowRunIdentifier) {
        return getContext(flowRunIdentifier).getProbeInterval();
    }

    public TimeUnit getProbeTimeUnit(FlowRunIdentifier flowRunIdentifier) {
        return getContext(flowRunIdentifier).getProbeTimeUnit();
    }

    public Duration getProbeTimeout(FlowRunIdentifier flowRunIdentifier) {
        return getContext(flowRunIdentifier).getTimeout();
    }

}
