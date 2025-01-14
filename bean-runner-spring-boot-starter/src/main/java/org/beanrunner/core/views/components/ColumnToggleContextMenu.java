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

package org.beanrunner.core.views.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.page.WebStorage;

public class ColumnToggleContextMenu<T> extends ContextMenu {

        private String gridKey;

        public ColumnToggleContextMenu(String gridKey, Component target) {
            super(target);
            this.gridKey = gridKey;
            setOpenOnClick(true);
        }

        void addColumnToggleItem(Grid<T> grid, String label, String key, Grid.Column<T> column, boolean defaultVisible) {
            MenuItem menuItem = this.addItem(label, e -> {
                column.setVisible(e.getSource().isChecked());
                WebStorage.setItem(gridKey + "." + key, String.valueOf(column.isVisible()));
            });
            column.setVisible(defaultVisible);

            WebStorage.getItem(gridKey + "." + key).thenAccept(v -> {
                if (v != null) {
                    column.setVisible(Boolean.parseBoolean(v));
                    menuItem.setChecked(column.isVisible());
                }
            });

            menuItem.setCheckable(true);
            menuItem.setChecked(column.isVisible());
            menuItem.setKeepOpen(true);
        }

    public void showAllColumns() {
        getItems().forEach(item -> {
            item.setChecked(true);

        });
    }
}