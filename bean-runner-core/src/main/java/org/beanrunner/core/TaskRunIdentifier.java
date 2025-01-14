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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@Getter
@Setter
@ToString
public class TaskRunIdentifier {

    private final long timestamp;
    private final String id;
    private InvocationType invocationType;
    private String sourceName;
    private String sourceIconPath;

    @JsonIgnore
    private boolean isRunning;

    private TaskStatus flowStatus;
    private List<TaskTagItem> tags;

    @Getter
    private Map<String, String> runProperties = new ConcurrentHashMap<>();

    @JsonIgnore
    private String taskId;

    @JsonIgnore
    private boolean overrideDisplayValues;

    private boolean rewindArmed;

    public TaskRunIdentifier() {
        id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }

    public TaskRunIdentifier(String id) {
        this.id = id;
        this.timestamp = System.currentTimeMillis();
    }

    public TaskRunIdentifier(String id, long timestamp) {
        this.id = id;
        this.timestamp = timestamp;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TaskRunIdentifier that = (TaskRunIdentifier) o;
        return id.equals(that.id);
    }

    public int hashCode() {
        return id.hashCode();
    }


}
