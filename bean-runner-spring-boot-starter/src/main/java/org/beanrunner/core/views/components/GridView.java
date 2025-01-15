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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.WebStorage;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.selection.SelectionListener;
import com.vaadin.flow.function.ValueProvider;
import lombok.Getter;

import java.util.*;

public class GridView<T> extends VerticalLayout {

    @Getter
    private final Grid<T> grid;

    private final HorizontalLayout pnlToolbar;

    private final Button btnShowHideColumns;
    private final Button btnCopyToCsv;

    private final TextField txtFilter;
    private final Span lblTitle;

    private final Map<String, ValueProvider<T, ?>> providerMap = new LinkedHashMap<>();

    private List<T> allItems;
    private final String localStorageKey;
    private final ColumnToggleContextMenu<T> columnToggleContextMenu;

    public GridView(String localStorageKey, Class<T> type) {
        setSizeFull();
        setPadding(false);
        setMargin(false);
        setSpacing(false);
        this.localStorageKey = localStorageKey;
        grid = new Grid<>(type, false);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.getStyle().setFontSize("14px");

        pnlToolbar = new HorizontalLayout();
        pnlToolbar.setWidthFull();
        pnlToolbar.setClassName("grid-toolbar");
        pnlToolbar.setPadding(false);
        pnlToolbar.setMargin(false);
        pnlToolbar.setSpacing(false);
        pnlToolbar.setAlignItems(Alignment.CENTER);

        lblTitle = new Span();
        lblTitle.setClassName("grid-title");
        lblTitle.getStyle().setFontSize("12px");
        pnlToolbar.add(lblTitle);

        Span filler = new Span();
        pnlToolbar.add(filler);
        pnlToolbar.setFlexGrow(1, filler);
        pnlToolbar.setHeight("32px");
        txtFilter = new TextField();
        txtFilter.setPlaceholder("Filter");
        txtFilter.setClassName("txt-filter");
        txtFilter.addValueChangeListener(e -> {
            grid.setItems(allItems.stream().filter(this::filter).toList());
        });
        txtFilter.getStyle().setPadding("0");
        txtFilter.setMaxHeight("23px");
        txtFilter.setHeight("23px");
        txtFilter.setClearButtonVisible(true);
        txtFilter.getStyle().setBackground("transparent");

        pnlToolbar.add(txtFilter);

        btnCopyToCsv = new Button(VaadinIcon.FILE_TABLE.create());
        btnCopyToCsv.setClassName("btn-copy-to-csv");
        btnCopyToCsv.getStyle().setBackground("transparent");
        btnCopyToCsv.setHeight("24px");
        btnCopyToCsv.addClickListener(e -> {
            StringBuilder sb = new StringBuilder();
            Collection<ValueProvider<T, ?>> providers = providerMap.values();
            for (T item : allItems.stream().filter(this::filter).toList()) {
                for (ValueProvider<T, ?> provider : providers) {
                    sb.append(provider.apply(item)).append(",");
                }
                sb.append("\n");
            }
            UI.getCurrent().getPage().executeJs("window.copyToClipboard($0)",sb.toString());
            Notification.show("CSV copied to clipboard");
        });
        btnCopyToCsv.setTooltipText("Copy to CSV");

        pnlToolbar.add(btnCopyToCsv);



        btnShowHideColumns = new Button(VaadinIcon.GRID.create());
        btnShowHideColumns.setClassName("btn-show-hide-columns");
        btnShowHideColumns.setTooltipText("Show/Hide Columns");

        columnToggleContextMenu = new ColumnToggleContextMenu<>(localStorageKey, btnShowHideColumns);

        pnlToolbar.add(btnShowHideColumns);


        add(pnlToolbar, grid);
    }

    public Grid.Column<T> addColumn(String key, String label, ValueProvider<T, ?> valueProfider) {
        Grid.Column<T> column = grid.addColumn(valueProfider)
                .setHeader(label)
                .setSortable(true)
                .setResizable(true)
                .setKey(key);
        providerMap.put(key, valueProfider);
        columnToggleContextMenu.addColumnToggleItem(grid, label, key, column, true);
        return column;
    }

    public void setTitle(String title) {
        lblTitle.setText(title);
    }

    public void addSelectionListener(SelectionListener<Grid<T>, T> listener) {
        grid.addSelectionListener(listener);
    }

    public void setItems(List<T> items) {
        allItems = items;
        grid.setItems(items);
    }

    public void setItems() {
        allItems = new ArrayList<>();
        grid.setItems(List.of());
    }

    private boolean filter(T item) {
        String filter = txtFilter.getValue();
        if (filter == null || filter.isEmpty()) {
            return true;
        }

        for (ValueProvider<T, ?> provider : providerMap.values()) {
            Object value = provider.apply(item);
            if (value != null && value.toString().toLowerCase().contains(filter.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    public void clearFilter() {
        txtFilter.clear();
    }

    public void setFilter(String filter) {
        txtFilter.setValue(filter);
    }

    public void showAllColumns() {
        columnToggleContextMenu.showAllColumns();
        grid.getColumns().forEach(c -> {
            c.setVisible(true);
            String key = c.getKey();
            WebStorage.setItem(localStorageKey + "." + key, "true");
        });
    }

    public boolean containsItem(T item) {
        return allItems.contains(item);
    }

    public void addItem(T item) {
        allItems.add(item);
        grid.setItems(allItems);
        grid.getDataProvider().refreshAll();
    }
}
