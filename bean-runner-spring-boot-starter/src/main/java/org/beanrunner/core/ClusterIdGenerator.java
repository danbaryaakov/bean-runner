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

import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ClusterIdGenerator {

    private static final AtomicInteger clusterId = new AtomicInteger(0);

    private static final ConcurrentHashMap<Integer, ClusterDetails> clusterDetails = new ConcurrentHashMap<>();

    public static int nextClusterId(String name, String iconPath) {
        int id = clusterId.incrementAndGet();
        clusterDetails.put(id, ClusterDetails.builder().name(name).iconPath(iconPath).build());
        return id;
    }

    public static ClusterDetails getClusterdetails(int id) {
        return clusterDetails.get(id);
    }

    public static void putClusterDetails(int id, String name, String icon) {
        ClusterDetails details = clusterDetails.computeIfAbsent(id, clusterDetails -> new ClusterDetails());
        if (StringUtils.isNotBlank(name)) {
            details.setName(name);
        }
        if (StringUtils.isNotBlank(icon)) {
            details.setIconPath(icon);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClusterDetails {
        private String name;
        private String iconPath;
    }

}
