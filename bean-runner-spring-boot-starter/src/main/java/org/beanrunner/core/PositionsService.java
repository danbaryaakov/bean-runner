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
import org.beanrunner.core.storage.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PositionsService {
    private final Map<String, NodePosition> positions = new ConcurrentHashMap<>();
    private final StorageService storageService;

    public PositionsService(@Autowired StorageService storageService) {
        this.storageService = storageService;
        Optional<String> content = storageService.read("positions.json");
        if (content.isPresent()) {
            try {
                Map<String, NodePosition> positions = new ObjectMapper().readValue(content.get(), new TypeReference<>() {
                    @Override
                    public Type getType() {
                        return super.getType();
                    }
                });
                this.positions.putAll(positions);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else {
           loadDefaultPositions();
        }
    }

    public void loadDefaultPositions() {

        try {

            InputStream stream = getClass().getResourceAsStream("/default_positions.json");
            if (stream == null) {
                return;
            }
            Map<String, NodePosition> positions = new ObjectMapper().readValue(stream, new TypeReference<>() {
                @Override
                public Type getType() {
                    return super.getType();
                }
            });
            this.positions.putAll(positions);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            storageService.store("positions.json", new ObjectMapper().writeValueAsString(this.positions));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void setPosition(String taskId, int x, int y) {
        positions.put(taskId, new NodePosition(x, y));
        try {
            storageService.store("positions.json", new ObjectMapper().writeValueAsString(positions));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void setPositions(Map<String, NodePosition> positions) {
        this.positions.putAll(positions);
        try {
            storageService.store("positions.json", new ObjectMapper().writeValueAsString(this.positions));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public NodePosition getPosition(String taskId) {
        return positions.get(taskId);
    }

    public String getPositionsJson() {
        try {
            return new ObjectMapper().writeValueAsString(positions);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
