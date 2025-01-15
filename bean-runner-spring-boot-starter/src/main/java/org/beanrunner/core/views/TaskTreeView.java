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

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import org.beanrunner.core.Step;
import org.beanrunner.core.TaskManager;
import org.beanrunner.core.TaskRunIdentifier;
import org.beanrunner.core.TaskStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TaskTreeView extends VerticalLayout {

    private final Grid<Step<?>> grid = new Grid<>((Class<Step<?>>)(Class<?>) Step.class, false);
    private TreeDataProvider<Step<?>> dataProvider;

    private final TaskManager taskManager;
    private final Set<Step> addedTasks = new HashSet<>();
    private TaskRunIdentifier identifier;
    private final MainView mainView;

    private Step<?> selectedRootTask;

    public TaskTreeView(TaskManager taskManager, MainView mainView) {
        this.taskManager = taskManager;
        this.mainView = mainView;

        setPadding(false);
        setSpacing(false);
        setMargin(false);
        grid.setMinHeight("10px");

        grid.setMaxHeight("100%");
        grid.addComponentColumn(t -> {
            String name = ViewUtils.splitCamelCase(t.getClass().getSimpleName());

            HorizontalLayout layout = new HorizontalLayout();
            layout.setAlignItems(Alignment.CENTER);

            Icon categoryIcon = null;

//            if (t.getClass().isAnnotationPresent(StepCategory.class)) {
//                StepCategory category = t.getClass().getAnnotation(StepCategory.class);
//                if (category.value() != null) {
//                    categoryIcon = category.value().getIcon().create();
//                }
//            } else {
//                if (t == selectedRootTask) {
//                    categoryIcon = VaadinIcon.RHOMBUS.create();
//                } else {
//                    categoryIcon = VaadinIcon.CIRCLE_THIN.create();
//                }
//            }

//            if (categoryIcon != null) {
//                categoryIcon.setSize("20px");
//                categoryIcon.setColor("#35789c");
//                layout.add(categoryIcon);
//            }

            Span spanName = new Span(name);
            spanName.setSizeUndefined();

            layout.add(spanName);
            spanName.getStyle().set("display", "block")
                    .set("overflow", "hidden")
                    .set("text-overflow", "ellipsis");
//            layout.setFlexShrink(1, spanName);

            Span filler = new Span();
            layout.add(filler);
            layout.setFlexGrow(1, filler);

            if (identifier != null) {
                if (t.getStatus(identifier) == TaskStatus.RUNNING || t.getStatus(identifier) == TaskStatus.READY) {
                    Loader icon = new Loader("loader-running");
                    layout.add(icon);
                } else if (t.getStatus(identifier) == TaskStatus.PENDING_REWIND || t.getStatus(identifier) == TaskStatus.REWINDING) {
                    Loader icon = new Loader("loader-rewinding");
                    layout.add(icon);
                } else if (t.getStatus(identifier) == TaskStatus.SUCCESS) {
                    Icon icon = VaadinIcon.CHECK.create();
                    icon.setColor("green");
                    icon.setSize("20px");
                    layout.add(icon);
                } else if (t.getStatus(identifier) == TaskStatus.FAILED) {
                    Icon icon = VaadinIcon.CLOSE.create();
                    icon.setColor("red");
                    icon.setSize("20px");
                    layout.add(icon);
                } else if (t.getStatus(identifier) == TaskStatus.FAILED_TRANSITIVELY) {
                    Icon icon = VaadinIcon.CLOSE.create();
                    icon.setColor("#ffb8be");
                    icon.setSize("20px");
                    layout.add(icon);
                }
            }
            return layout;
        }).setHeader("Step");
        grid.addSelectionListener(e -> {
            if (e.getFirstSelectedItem().isPresent()) {
                Step<?> task = e.getFirstSelectedItem().get();
                mainView.taskTreeSelectionChanged(task, identifier);
            }
        });
        grid.removeAllHeaderRows();

        add(grid);
    }

    public Grid getGrid() {
        return grid;
    }

    public void refreshTask(Step<?> task, TaskRunIdentifier identifier) {
        if (identifier.equals(this.identifier)) {
            grid.getDataProvider().refreshItem(task);
        }
    }

    public void clear() {
        grid.setItems(List.of());
    }

    public void setRootTask(Step<?> task, TaskRunIdentifier identifier) {
        this.identifier = identifier;
        this.selectedRootTask = task;
        addedTasks.clear();
        addedTasks.add(task);
        List<Step<?>> flattentedTasks = flattenTasks(task);
        grid.setItems(flattentedTasks);
    }

    private List<Step<?>> flattenTasks(Step<?> rootTask) {
        List<Step<?>> tasks = new ArrayList<>();
        return flattenTasks(rootTask, tasks);
    }

    private List<Step<?>> flattenTasks(Step<?> rootTask, List<Step<?>> tasks) {
        if (! tasks.contains(rootTask)) {
            tasks.add(rootTask);
            List<Step<?>> dependentTasks = taskManager.getSubTasks(rootTask);
            if (dependentTasks != null) {
                dependentTasks.forEach(dt -> flattenTasks(dt, tasks));
            }
        }
        return tasks;
    }


    public void setSelectedTask(Step<?> task) {
        grid.select(task);
        grid.scrollToItem(task);
    }
}
