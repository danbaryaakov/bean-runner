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

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.dataview.GridListDataView;

import java.util.Collection;

public class FilterableGrid<T> extends Grid<T> {

    private Collection<T> allItems;

    private Filter<T> filter;

    public FilterableGrid(Class<T> beanType) {
        super(beanType, false);
    }

    public void filter(String filter) {

    }

    @Override
    public GridListDataView<T> setItems(Collection<T> items) {
        this.allItems = items;
        if (filter == null) {
            return super.setItems(items);
        } else {
            return super.setItems(items.stream().filter(filter::filter).toList());
        }
    }

    public void setFilter(Filter<T> filter) {
        this.filter = filter;
        if (allItems != null) {
            setItems(allItems);
        }
    }

    public void refreshAll() {
        getDataProvider().refreshAll();
        if (allItems != null) {
            setItems(allItems);
        }
    }

}
