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

package org.beanrunner.core.storage.runs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.QualifierInspector;
import org.beanrunner.core.Step;
import org.beanrunner.core.StepRunContext;
import org.beanrunner.core.FlowRunIdentifier;
import org.beanrunner.core.storage.StorageService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class StepRunStorage implements InitializingBean {

    @Autowired
    private StorageService storageService;

    @Autowired
    QualifierInspector qualifierInspector;

    private ObjectMapper objectMapper = new ObjectMapper();

    public List<FlowRunIdentifier> getIdentifiersForFlow(String flowId) {
        List<String> folders = storageService.list("runs/" + flowId + "/");
        return folders.stream().flatMap(folder -> {
            String[] parts = folder.split("_");
            if (parts.length == 2) {
                String id = parts[0];
                String timestamp = parts[1];
                try {
                    return Stream.of(new FlowRunIdentifier(id, Long.parseLong(timestamp)));
                } catch (Throwable t) {
                    // ignore
                }
                return Stream.empty();
            }
            return Stream.empty();
        }).collect(Collectors.toList());
    }

    public <D> void loadStepContext(String flowId, Step<D> step, FlowRunIdentifier id) {
        String stepIdentifier = qualifierInspector.getQualifierForBean(step);
        Optional<String> json = storageService.read("runs/" + flowId + "/" + id.getId() + "_" + id.getTimestamp() + "/" + stepIdentifier + ".json");
        if (json.isPresent()) {
            try {
                StepRunContext<?> context = step.getContext(id);
                objectMapper.readerForUpdating(context).readValue(json.get());
            } catch (Exception e) {
                log.error("Failed to load step context", e);
            }
        }
    }

    public void storeIdentifier(String flowId, Step<?> step, FlowRunIdentifier identifier) {
        try {
            String content = objectMapper.writeValueAsString(identifier);
            storageService.store("runs/" + flowId + "/" + identifier.getId() + "_" + identifier.getTimestamp() + "/" + identifier.getId() + ".json", content);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize identifier to JSON", e);
        }
    }

    public void loadIdentifier(String flowId, Step<?> step, FlowRunIdentifier identifier) {
        Optional<String> json = storageService.read("runs/" + flowId + "/" + identifier.getId() + "_" + identifier.getTimestamp() + "/" + identifier.getId() + ".json");
        if (json.isPresent()) {
            try {
                objectMapper.readerForUpdating(identifier).readValue(json.get());
            } catch (Exception e) {
                log.error("Failed to load step context", e);
            }
        }
    }

    public void saveStepContext(String flowId, Step<?> step, FlowRunIdentifier id) {
        String stepIdentifier = qualifierInspector.getQualifierForBean(step);
        try {
            StepRunContext<?> context = step.getContext(id);
            String json = objectMapper.writeValueAsString(context);
            storageService.store("runs/" + flowId + "/" + id.getId() + "_" + id.getTimestamp() + "/" + stepIdentifier + ".json", json);
        } catch (Exception e) {
            log.error("Failed to save step context", e);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

}
