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
import org.beanrunner.core.settings.ConfigurationSettings;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class Step<D> implements ConfigurationSettings {

    protected final Map<FlowRunIdentifier, StepRunContext<D>> contextMap = new ConcurrentHashMap<>();

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

    public Throwable getException() {
        String runId = MDC.get("runId");
        return contextMap.get(new FlowRunIdentifier(runId)).getException();
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

    public void putContext(FlowRunIdentifier flowRunIdentifier, StepRunContext<?> context) {
        contextMap.put(flowRunIdentifier, (StepRunContext<D>) context);
    }

}
