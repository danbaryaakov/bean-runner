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

package org.beanrunner.examples.result;

import org.beanrunner.core.Step;
import org.beanrunner.core.annotations.OnComplete;
import org.beanrunner.core.annotations.StepDescription;
import org.beanrunner.core.annotations.StepIcon;
import org.beanrunner.core.annotations.StepName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@StepName("Error Monitor")
@StepIcon("images/step-monitor.svg")
@StepDescription("""
        This step demonstrates monitoring another step and reporting when the service is up or down.
        It currently outputs to the console, but in a real world application it can inform a monitoring system such as OpsGenie, Slack etc.
        """)
public class PersonGeneratorErrorMonitor extends Step<Void> {

    @Autowired
    @OnComplete
    private GeneratePerson generatePerson;

    private AtomicBoolean isError = new AtomicBoolean(false);

    @Override
    protected void run() {
        boolean currentValue = isError.get();
        if (generatePerson.getException() != null) {
            isError.set(true);
            if (!currentValue) {
                System.out.println("Service is down. Error is " + generatePerson.getException().getMessage());
            }
        } else {
            isError.set(false);
            if (currentValue) {
                System.out.println("Service is up.");
            }
        }
    }

}
