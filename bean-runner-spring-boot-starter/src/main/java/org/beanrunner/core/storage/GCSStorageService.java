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

import com.google.cloud.storage.*;
import org.beanrunner.core.views.components.Page;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@ConditionalOnProperty(name = "bean-runner.storage.type", havingValue = "gcs")
public class GCSStorageService implements StorageService, InitializingBean {

    @Value("${bean-runner.storage.gcs.project-id}")
    private String projectId;

    @Value("${bean-runner.storage.gcs.bucket}")
    private String bucket;

    private Storage storage;

    @Override
    public void store(String key, String content) {
        BlobId blobId = BlobId.of(bucket, key);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        storage.create(blobInfo, content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Optional<String> read(String key) {
        BlobId blobId = BlobId.of(bucket, key);
        Blob blob = storage.get(blobId);
        if (blob != null && blob.exists()) {
            return Optional.of(new String(blob.getContent(), StandardCharsets.UTF_8));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public List<String> list(String path) {

        List<String> result = new ArrayList<>();
        Iterable<Blob> blobs = storage.list(
                bucket,
                Storage.BlobListOption.prefix(path),
                Storage.BlobListOption.currentDirectory() // Only list one level under the prefix
        ).iterateAll();

        for (Blob blob : blobs) {
            String name = blob.getName();

            String objectName = name;
            if (objectName.endsWith("/")) {
                objectName = objectName.substring(0, objectName.length() - 1);
            }
            objectName = objectName.substring(objectName.lastIndexOf("/") + 1);

            result.add(objectName);
        }
        return result;
    }

    @Override
    public List<String> loadBatch(List<String> filePaths) {
        // Create a batch request
        return filePaths.stream().parallel().map(filePath -> {
            BlobId blobId = BlobId.of(bucket, filePath);
            return new String(storage.readAllBytes(blobId));
        }).toList();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
    }

}
