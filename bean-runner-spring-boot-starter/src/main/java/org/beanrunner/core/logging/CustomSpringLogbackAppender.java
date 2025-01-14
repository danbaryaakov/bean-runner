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

package org.beanrunner.core.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.QualifierInspector;
import org.beanrunner.core.Step;
import org.beanrunner.core.TaskRunIdentifier;
import org.beanrunner.core.storage.StorageService;
import org.slf4j.MDC;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class CustomSpringLogbackAppender extends AppenderBase<ILoggingEvent> implements InitializingBean {

    private final Map<String, List<LogEvent>> logEvents = new ConcurrentHashMap<>();

    @Autowired
    private QualifierInspector qualifierInspector;

    @Autowired
    private StorageService storageService;

    private final List<LogListener> listeners = new CopyOnWriteArrayList<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void append(ILoggingEvent eventObject) {
        String taskClass = MDC.get("task");
        String taskRunId = MDC.get("runId");

        if (taskClass != null && taskRunId != null) {
            String key = taskClass + "-" + taskRunId;
            List<LogEvent> events = logEvents.computeIfAbsent(key, k -> new java.util.ArrayList<>());
            LogEvent event = LogEvent.builder()
                    .location(eventObject.getLoggerName())
                    .level(eventObject.getLevel().toString())
                    .message(getMessage(eventObject))
                    .timestamp(eventObject.getInstant())
                    .build();
            events.add(event);
            CompletableFuture.runAsync(() -> {
                listeners.forEach(listener -> listener.logEventAdded(new LogEventAddedEvent(event, taskClass, taskRunId)));
            });
        }
    }

    public void storeLogs(String flowId, Step<?> step, TaskRunIdentifier identifier) {
        String stepIdentifier = qualifierInspector.getQualifierForBean(step);
        List<LogEvent> logs = logEvents.get(stepIdentifier + "-" + identifier.getId());
        if (logs != null) {
            try {
                String jsonString = objectMapper.writeValueAsString(logs);
                storageService.store("logs/" + flowId + "/" + identifier.getId() + "_" + identifier.getTimestamp() + "/" + stepIdentifier + "_log.json", jsonString);
            } catch (JsonProcessingException e) {
                log.error("Failed to store logs", e);
            }
        }
    }

    public void loadLogs(String flowId, Step<?> step, TaskRunIdentifier identifier) {
        String stepIdentifier = qualifierInspector.getQualifierForBean(step);
        Optional<String> json = storageService.read("logs/" + flowId + "/" + identifier.getId() + "_" + identifier.getTimestamp() + "/" + stepIdentifier + "_log.json");
        if (json.isPresent()) {
            try {
                List<LogEvent> logs = objectMapper.readValue(json.get(), new TypeReference<>() {
                    @Override
                    public Type getType() {
                        return super.getType();
                    }
                });
                logEvents.put(stepIdentifier + "-" + identifier.getId(), logs);
            } catch (IOException e) {
                log.error("Failed to load logs", e);
            }
        }
    }

    public List<LogEvent> getEvents(String taskClass, String taskRunId) {
        return logEvents.get(taskClass + "-" + taskRunId);
    }

    private String getMessage(ILoggingEvent e) {
        StringBuilder builder = new StringBuilder();
        builder.append(e.getFormattedMessage());
        IThrowableProxy throwableProxy = e.getThrowableProxy();
        if (throwableProxy != null) {
            // Convert IThrowableProxy to Throwable to access the stack trace
            if (throwableProxy instanceof ThrowableProxy) {
                Throwable throwable = ((ThrowableProxy) throwableProxy).getThrowable();
                builder.append(" : ");
                builder.append(throwable.getMessage());
                builder.append("\n");
                // Print the stack trace to the console
                try (StringWriter sw = new StringWriter();
                     PrintWriter pw = new PrintWriter(sw)) {
                    throwable.printStackTrace(pw);
                    builder.append(sw);
                } catch (Exception ex) {
                    // ignore
                }
            }
        }
        return builder.toString();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        objectMapper.registerModule(new JavaTimeModule());

        // Optional: Disable writing dates as timestamps
//        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    }

    public void addLogEventListener(LogListener listener) {
        listeners.add(listener);
    }

    public void removeLogEventListener(LogListener listener) {
        listeners.remove(listener);
    }

}