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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.server.PathParam;
import org.beanrunner.core.annotations.HttpInvokable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api")
public class HttpController {

    private final List<FlowInvoker<?, ?>> invokers;
    private final Map<String, StepStatus> stateMap = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final StepManager stepManager;

    public HttpController(@Autowired StepManager stepManager, @Autowired List<FlowInvoker<?, ?>> allInvokers) {
        this.stepManager = stepManager;
        this.invokers = allInvokers.stream()
                .filter(invoker -> invoker.getClass().isAnnotationPresent(HttpInvokable.class))
                .toList();
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/invoke/{flowId}")
    public <P, R> String start(@PathVariable("flowId") String flowId, @RequestBody String body) {
        FlowInvoker<P, R> invoker = (FlowInvoker<P, R>) invokers.stream()
                .filter(inv -> inv.getClass().getAnnotation(HttpInvokable.class).flowId().equals(flowId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Flow not found"));

        Type type = invoker.getClass().getGenericSuperclass();

        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;

            // Get the actual type arguments
            Type[] typeArguments = parameterizedType.getActualTypeArguments();

            Type parameterType = typeArguments[0];
            Class<P> clazz = (Class<P>) parameterType;

            P parameter = objectMapper.convertValue(body, clazz);
            AtomicReference<Object> state = new AtomicReference<>();

            String identifier = invoker.runAsync(parameter, (i, result) -> {
                stateMap.put(i, StepStatus.SUCCESS);
            }, (i, errors) -> {
                stateMap.put(i, StepStatus.FAILED);
            });
            stateMap.putIfAbsent(identifier, StepStatus.RUNNING);
            return identifier;
        }
        throw new RuntimeException("Could not determine parameter type");
    }

    @GetMapping("/status/{identifier}")
    public StepStatus getFlowStatus(@PathVariable("identifier") String identifier) {
        return stateMap.getOrDefault(identifier, StepStatus.NOT_STARTED);
    }
//
//    public String rewindFlow(@PathVariable("flowId") String flowId, @PathVariable("identifier") String identifier) {
//        FlowInvoker<?, ?> invoker = invokers.stream()
//                .filter(inv -> inv.getClass().getAnnotation(HttpInvokable.class).flowId().equals(flowId))
//                .findFirst()
//                .orElseThrow(() -> new IllegalArgumentException("Flow not found"));
//
//        stepManager.rewindAllRewindableSteps(invoker.getFirstStep(), idetifier);
//        return "OK";
//    }

}
