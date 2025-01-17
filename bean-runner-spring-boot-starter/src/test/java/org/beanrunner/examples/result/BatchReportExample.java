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

import org.beanrunner.BatchReporterStep;
import org.beanrunner.core.Step;
import org.beanrunner.examples.rewind.Person;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class BatchReportExample extends BatchReporterStep<Person> {

    public BatchReportExample(@Autowired WR2 inputStep) {
        super(inputStep, 10, 30, TimeUnit.SECONDS);
    }

    @Override
    protected void handleBatch(List<Person> data) {
        System.out.println("Reporting batch of " + data.size() + " persons: ");
        data.forEach(System.out::println);
        System.out.println("-----------------------------------------------------");
    }
}
