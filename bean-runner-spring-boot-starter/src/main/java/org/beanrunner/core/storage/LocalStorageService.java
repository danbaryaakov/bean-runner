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

package org.beanrunner.core.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stores files in the local file system.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "bean-runner.storage.type", havingValue = "local")
public class LocalStorageService implements StorageService, InitializingBean {

    @Value("${bean-runner.storage.local.path}")
    private String storagePath;


    private void createStorageIfNecessary() {
        Path path = Paths.get(storagePath);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RuntimeException("Could not create storage directory", e);
            }
        }
    }

    @Override
    public void store(String path, String content) {
        Path filePath = Paths.get(storagePath, path);
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, content.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Could not store file at path: " + path, e);
        }
    }

    @Override
    public Optional<String> read(String path) {
        Path filePath = Paths.get(storagePath, path);
        try {
            if (!Files.exists(filePath)) {
                return Optional.empty();
            }
            return Optional.of(Files.readString(filePath));
        } catch (IOException e) {
            throw new RuntimeException("Could not read file at path: " + path, e);
        }
    }

    @Override
    public List<String> list(String path) {
        Path filePath = Paths.get(storagePath, path);

        List<String> result = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(filePath)) {
            for (Path entry : stream) {
                result.add(entry.getFileName().toString());
            }
        } catch (IOException | DirectoryIteratorException e) {
//            log.error("Error reading directory: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        createStorageIfNecessary();
    }
}
