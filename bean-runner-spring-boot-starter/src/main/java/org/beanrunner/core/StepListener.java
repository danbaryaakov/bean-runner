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

public interface StepListener {
    void stepChanged(Step<?> task, FlowRunIdentifier identifier);
    void runAdded(Step<?> task, FlowRunIdentifier identifier, boolean userInitiated);
    void runRemoved(Step<?> firstStep, FlowRunIdentifier identifier);
    void flowRunsLoaded(Step<?> firstStep);
    void runContentLoaded(Step<?> firstStep, FlowRunIdentifier identifier);
}
