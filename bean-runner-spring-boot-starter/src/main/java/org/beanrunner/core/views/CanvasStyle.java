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

package org.beanrunner.core.views;

import lombok.Getter;

@Getter
public enum CanvasStyle {
    PAPER("Paper", "diagram-paper"),
    ALUMINUM("Aluminum", "diagram-aluminum"),
    NOISE("Noise", "diagram-noise"),
    PATTERN("Paper Pattern", "diagram-paper-2"),
    CLOUDS("Clouds", "diagram-clouds"),

    ;

    String name;
    String className;

    CanvasStyle(String name, String className) {
        this.name = name;
        this.className = className;
    }
}
