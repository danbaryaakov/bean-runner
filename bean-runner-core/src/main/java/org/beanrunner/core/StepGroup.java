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

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StepGroup {
    private String qualifier;
    private String iconPath;
    private String name;
    private List<Step<?>> steps;

    public <T extends Step<?>> T get(Class<T> clazz) {
        for (Step<?> step : steps) {
            if (clazz.isInstance(step)) {
                return clazz.cast(step);
            }
        }
        throw new RuntimeException("Step " + clazz + " not found");
    }

    @SafeVarargs
    public static List<StepGroup> listOf(List<StepGroup>... lists) {
        List<StepGroup> result = new ArrayList<>();
        for (List<StepGroup> list : lists) {
            result.addAll(list);
        }
        return result;
    }

}
