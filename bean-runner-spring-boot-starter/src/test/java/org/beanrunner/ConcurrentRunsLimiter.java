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

package org.beanrunner;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.FlowRunIdentifier;
import org.beanrunner.core.Step;
import org.beanrunner.core.StepStatus;
import org.beanrunner.core.annotations.OnSuccess;
import org.beanrunner.core.annotations.StepIcon;
import org.beanrunner.core.annotations.StepName;
import org.beanrunner.core.annotations.UIConfigurable;

@Slf4j
@StepIcon("images/step-bucket.svg")
@StepName("Concurrent Runs Limiter")
@RequiredArgsConstructor
public class ConcurrentRunsLimiter extends Step<Void> {

    @NonNull
    @OnSuccess
    private final Step<?> previousStep;

    @NonNull
    @JsonProperty
    @UIConfigurable("Limit")
    private Long concurrentRunsLimit;

    @Override
    public void run() {
        long currentRunningFlows = contextMap.keySet().stream()
                .filter(FlowRunIdentifier::isRunning).count();
        if (currentRunningFlows > concurrentRunsLimit) {
            throw new ConcurrentRunLimitException();
        }
    }

}
