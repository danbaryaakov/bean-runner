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

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.beanrunner.core.Step;
import org.beanrunner.core.TaskRunIdentifier;
import org.beanrunner.core.logging.LogEvent;
import org.beanrunner.core.logging.LogEventAddedEvent;
import org.beanrunner.core.logging.LogListener;
import org.beanrunner.core.views.components.GridView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TimeZone;

public class LogsView extends VerticalLayout implements LogListener {

    private final GridView<LogEvent> grid = new GridView<>("gridLogs", LogEvent.class);
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final MainView mainView;

    private Step<?> selectedStep;
    private TaskRunIdentifier selectedIdentifier;

    public LogsView(MainView mainView) {
        this.mainView = mainView;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        setMargin(false);

        grid.getStyle().setFontSize("14px");
        grid.addColumn("level", "Level", LogEvent::getLevel).setHeader("Level").setWidth("100px").setResizable(true).setFlexGrow(0);

        grid.addColumn("time", "Time", e -> {
            LocalDateTime dateTime = LocalDateTime.ofInstant(e.getTimestamp(), TimeZone.getDefault().toZoneId());
            return formatter.format(dateTime);
        }).setHeader("Time").setWidth("200px").setResizable(true).setFlexGrow(0);

        grid.addColumn("location", "Location", e -> {
            return e.getLocation().substring(e.getLocation().lastIndexOf(".") + 1);
        }).setHeader("Location").setFlexGrow(0).setWidth("150px").setResizable(true);

        grid.addColumn("message", "Message", e -> {
            if (e.getMessage() == null) {
                return "";
            }
            return e.getMessage();
        }).setHeader("Message").setResizable(true);
        grid.setSizeFull();
        add(grid);
    }

    public synchronized void setLogEvents(Step<?> step, TaskRunIdentifier identifier, List<LogEvent> logEvents) {
        this.selectedStep = step;
        this.selectedIdentifier = identifier;
        if (logEvents == null) {
            grid.setItems();
            return;
        }
        grid.setItems(logEvents);
    }

    public void clear() {
        setLogEvents(null, null, null);
    }

    @Override
    public synchronized void logEventAdded(LogEventAddedEvent event) {
        if (selectedStep != null &&
            selectedIdentifier != null &&
            mainView.getQualifierInspector().getQualifierForBean(selectedStep).equals(event.getStepId()) &&
            selectedIdentifier.getId().equals(event.getRunId())) {
            if (! grid.containsItem(event.getEvent())) {
                getUI().ifPresent(ui -> ui.access(() -> {
                    grid.addItem(event.getEvent());
                }));
            }
        }
    }
}
