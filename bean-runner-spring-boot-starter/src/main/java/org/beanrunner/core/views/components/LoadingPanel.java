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

package org.beanrunner.core.views.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.beanrunner.core.views.Loader;

public class LoadingPanel extends VerticalLayout {

    private final VerticalLayout pnlLoading = new VerticalLayout();
    private Component content;

    public LoadingPanel(Component content, String color) {
        this.content = content;
        setSizeFull();
        setPadding(false);
        setMargin(false);
        setSpacing(false);
        add(content);
        pnlLoading.setSizeFull();
        pnlLoading.setAlignItems(Alignment.CENTER);
        pnlLoading.setJustifyContentMode(JustifyContentMode.CENTER);
        Loader loader = new Loader("loader-running");
        pnlLoading.add(loader);
//        pnlLoading.setClassName("overlay-layout");
        pnlLoading.getStyle().setBackground(color);
        add(pnlLoading);
        pnlLoading.setVisible(false);
        content.setVisible(true);
    }

    public void setLoading(boolean loading) {
        if (loading) {
            content.setVisible(false);
            pnlLoading.setVisible(true);
        } else {
            pnlLoading.setVisible(false);
            content.setVisible(true);
        }
    }

}
