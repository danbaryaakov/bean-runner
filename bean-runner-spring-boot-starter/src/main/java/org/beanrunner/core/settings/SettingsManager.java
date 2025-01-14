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

package org.beanrunner.core.settings;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.beanrunner.core.QualifierInspector;
import org.beanrunner.core.storage.StorageService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class SettingsManager implements InitializingBean {

    @Getter
    private final List<ConfigurationSettings> settings;

    private final StorageService storageService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QualifierInspector qualifierInspector;

    public SettingsManager(@Autowired List<ConfigurationSettings> settings, @Autowired StorageService storageService, @Autowired QualifierInspector qualifierInspector) {
        this.settings = settings;
        this.storageService = storageService;
        this.qualifierInspector = qualifierInspector;;

        objectMapper.setVisibility(
                objectMapper.getSerializationConfig()
                        .getDefaultVisibilityChecker()
                        .withFieldVisibility(JsonAutoDetect.Visibility.NONE)
                        .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withCreatorVisibility(JsonAutoDetect.Visibility.NONE)
        );
    }

    public void settingUpdated(ConfigurationSettings setting) {
        try {
            String json = objectMapper.writeValueAsString(setting);
            storageService.store("configuration/" + qualifierInspector.getQualifierForBean(setting) + ".json", json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        settings.forEach(setting -> {
           Optional<String> json = storageService.read("configuration/" + qualifierInspector.getQualifierForBean(setting) + ".json");
            if (json.isPresent()) {
                 try {
                      objectMapper.readerForUpdating(setting).readValue(json.get());
                 } catch (JsonProcessingException e) {
                     log.error("Failed to read configuration for {}", setting.getClass().getName(), e);
                 }
            }
        });
    }


}
