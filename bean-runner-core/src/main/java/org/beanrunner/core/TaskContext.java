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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
public class TaskContext<D> {

    private String result = "";

    private TaskStatus status = TaskStatus.NOT_STARTED;

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@bodyClass")
    private D data;

    private long probeInterval = 5;

    private TimeUnit probeTimeUnit = TimeUnit.SECONDS;

    private Duration timeout = Duration.ofMinutes(5);

    @JsonIgnore
    private Throwable exception;

    public TaskContext() {

    }

    public void applyValuesFrom(TaskContext<D> other) {
        this.result = other.result;
        this.status = other.status;
        this.data = other.data;
        this.probeInterval = other.probeInterval;
        this.probeTimeUnit = other.probeTimeUnit;
        this.timeout = other.timeout;
        this.exception = other.exception;
    }
}
