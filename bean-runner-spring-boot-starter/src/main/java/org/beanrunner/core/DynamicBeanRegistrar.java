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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DynamicBeanRegistrar {

    private ConfigurableBeanFactory beanFactory;
    private List<StepGroupGenerator> providers;

    public DynamicBeanRegistrar(@Autowired ConfigurableBeanFactory beanFactory, @Autowired List<StepGroupGenerator> providers) {
        this.beanFactory = beanFactory;
        this.providers = providers;
        register();
    }

    private void register() {
        providers.forEach(provider -> {
            List<StepGroup> dynamicTasks = provider.generateStepGroups();

            dynamicTasks.forEach(group -> {

                int clusterId = ClusterIdGenerator.nextClusterId(group.getName(), group.getIconPath());
                for (Step<?> step : group.getSteps()) {
                    step.setClusterId(clusterId);
                    String key = group.getQualifier();
                    beanFactory.registerSingleton(step.getClass().getSimpleName() + "_" + key, step);
                }
            });
        });
    }

}