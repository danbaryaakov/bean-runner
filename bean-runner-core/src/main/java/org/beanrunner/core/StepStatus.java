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

public enum StepStatus {
    NOT_STARTED,
    READY,
    RUNNING,
    SUCCESS,
    FAILED_TRANSITIVELY,
    FAILED,
    PENDING_REWIND,
    REWINDING,
    REWIND_SUCCESS,
    REWIND_FAILED,
    PAUSED_FAILURE,
    PAUSED_SUCCESS,
    PAUSED_PROBING,
    PAUSED_REWIND_SUCCESS,
    PAUSED_REWIND_FAILURE,

    ;

    public boolean isPaused() {
        return this == PAUSED_FAILURE || this == PAUSED_SUCCESS || this == PAUSED_PROBING || this == PAUSED_REWIND_SUCCESS || this == PAUSED_REWIND_FAILURE;
    }

    public boolean isFailed() {
        return this == FAILED || this == PAUSED_FAILURE;
    }

    public boolean isRewinding() {
        return this == PENDING_REWIND || this == REWINDING || this == PAUSED_REWIND_SUCCESS || this == PAUSED_REWIND_FAILURE;
    }

}
