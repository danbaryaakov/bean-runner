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

package org.beanrunner.core.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;

import java.util.HashMap;
import java.util.Map;

public class VerticalTabSheet extends HorizontalLayout {

    private Map<Tab, Component> tabComponents = new HashMap<>();
    Tabs tabs = new Tabs();
    private VerticalLayout content = new VerticalLayout();

    public VerticalTabSheet() {
        setSizeFull();
        setPadding(false);
        setMargin(false);
        setSpacing(false);
        setAlignItems(Alignment.CENTER);

        content.setSizeFull();
//        content.getStyle().setBackground("red");
        content.setPadding(false);
        content.setMargin(false);
        content.setSpacing(false);
        content.setSizeFull();
        add(content);
        tabs.setClassName("vertical-tabs");
        tabs.setOrientation(Tabs.Orientation.VERTICAL);
        tabs.setFlexGrowForEnclosedTabs(0);
//        tabs.setMinHeight("30px");
//        tabs.setMaxHeight("100%");
        tabs.setHeightFull();
//        add(tabs);


        tabs.addSelectedChangeListener(e -> {
            content.removeAll();
            content.add(tabComponents.get(e.getSelectedTab()));
        });
    }

    public void add(Icon icon, Component component) {
        Tab tab = new Tab(icon);
        Scroller scroller = new Scroller(component);
        scroller.setSizeFull();
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        tabComponents.put(tab, scroller);
        tabs.add(tab);
        if (tabComponents.size() == 1) {
            tabs.setSelectedTab(tab);
        }
    }

}
