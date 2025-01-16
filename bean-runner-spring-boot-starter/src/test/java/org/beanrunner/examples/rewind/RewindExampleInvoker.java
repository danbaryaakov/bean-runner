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

package org.beanrunner.examples.rewind;

import org.beanrunner.core.FlowInvoker;
import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.HttpInvokable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@HttpInvokable(flowId = "rewind-example")
public class RewindExampleInvoker extends FlowInvoker<Void, Void> {
    public RewindExampleInvoker(@Autowired R1 firstStep, @Autowired R4 lastStep) {
        super(firstStep, lastStep);
    }
}
