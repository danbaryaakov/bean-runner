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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextArea;
import org.beanrunner.core.settings.ConfigurationSettings;
import org.beanrunner.core.settings.SettingsManager;

public class ConfigurationView extends VerticalLayout {

    private final SplitLayout splitMain = new SplitLayout();

    private final VerticalLayout pnlLeft = new VerticalLayout();
    private final Grid<ConfigurationSettings> gridSettings = new Grid<>(ConfigurationSettings.class, false);

    private final VerticalLayout pnlDetails = new VerticalLayout();

    private final SettingsManager settingsManager;
    private final TextArea txtConfiguration = new TextArea();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final YAMLMapper yamlMapper = new YAMLMapper();

    public ConfigurationView(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setVisibility(
                objectMapper.getSerializationConfig()
                        .getDefaultVisibilityChecker()
                        .withFieldVisibility(JsonAutoDetect.Visibility.NONE)
                        .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withCreatorVisibility(JsonAutoDetect.Visibility.NONE)
        );
        setSizeFull();
        setPadding(false);
        setMargin(false);

        splitMain.setSizeFull();
        splitMain.setOrientation(SplitLayout.Orientation.HORIZONTAL);
        splitMain.setSplitterPosition(20);
        add(splitMain);

        pnlLeft.setSizeFull();
        pnlLeft.setPadding(false);
        pnlLeft.setMargin(false);

        txtConfiguration.setClassName("configuration-text-editor");

        gridSettings.setSizeFull();
        gridSettings.addComponentColumn(c -> {
            String value = c.getClass().getSimpleName();
            if (value.contains("$$")) {
                value = value.substring(0, value.indexOf("$"));
            }
            HorizontalLayout layout = new HorizontalLayout();
            layout.setHeight("50px");
            layout.setAlignItems(Alignment.CENTER);
            layout.add(VaadinIcon.COG.create());
            layout.add(value);
            return layout;

        });

        HorizontalLayout pnlHeader = new HorizontalLayout();
        pnlHeader.setWidthFull();
        pnlHeader.setAlignItems(Alignment.CENTER);
        pnlLeft.setSpacing(false);
        pnlHeader.setClassName("configuration-actions");
        Span spanConfigurations = new Span("Configurations");
        spanConfigurations.getStyle().setFontSize("20px");
//        pnlHeader.add(spanConfigurations);
        pnlLeft.add(pnlHeader);
        pnlLeft.add(gridSettings);

        splitMain.addToPrimary(pnlLeft);

        pnlDetails.setSizeFull();
        pnlDetails.setPadding(false);
        pnlDetails.setMargin(false);

        txtConfiguration.setSizeFull();


        HorizontalLayout pnlActions = new HorizontalLayout();
        pnlActions.setAlignItems(Alignment.CENTER);
        pnlActions.setWidthFull();
        Button btnSave = new Button("Save", VaadinIcon.CHECK.create());
        btnSave.addClickListener(e -> {
            try {
                ConfigurationSettings selected = gridSettings.asSingleSelect().getValue();
                objectMapper.readerForUpdating(selected).readValue(txtConfiguration.getValue());
                settingsManager.settingUpdated(selected);
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
        });
        pnlActions.setClassName("configuration-actions");
        pnlActions.add(btnSave);
        pnlDetails.setSpacing(false);
        pnlDetails.add(pnlActions);
        pnlDetails.add(txtConfiguration);

        splitMain.addToSecondary(pnlDetails);

        gridSettings.setItems(settingsManager.getSettings());

        gridSettings.addSelectionListener(e -> {
            if (e.getFirstSelectedItem().isPresent()) {
                ConfigurationSettings selected = e.getFirstSelectedItem().orElse(null);
                try {
                    String jsonValue = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(selected);
                    txtConfiguration.setValue(jsonValue);
                } catch (JsonProcessingException ex) {
                    throw new RuntimeException(ex);
                }
            } else {
                txtConfiguration.setValue("");
            }
        });
    }

}
