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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.Icon;

public class ToggleButton extends Button {

    private boolean toggled = false;

    public ToggleButton(String text, Image icon, String classPrefix) {
        super(text, icon);
        addClickListener(e -> {
            toggled = !toggled;
            if (toggled) {
                addClassName("toggled");
            } else {
                removeClassName("toggled");
            }
        });
    }

    public ToggleButton(String text, Icon icon) {
        super(text, icon);
        addClickListener(e -> {
            toggled = !toggled;
            if (toggled) {
                addClassName("toggled");
            } else {
                removeClassName("toggled");
            }
        });
    }

    public boolean isToggled() {
        return toggled;
    }

    public void setToggled(boolean toggled) {
        this.toggled = toggled;
        if (toggled) {
            addClassName("toggled");
        } else {
            removeClassName("toggled");
        }
    }

}
