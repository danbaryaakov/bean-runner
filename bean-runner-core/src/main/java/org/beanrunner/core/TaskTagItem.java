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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.beanrunner.core.annotations.StepTag;

@Getter
@Setter
@NoArgsConstructor
public class TaskTagItem {

    private String label;
    private String className;

    public TaskTagItem(StepTag tag) {
        this.label = tag.value();
        this.className = tag.style().getClassName();
    }
}
