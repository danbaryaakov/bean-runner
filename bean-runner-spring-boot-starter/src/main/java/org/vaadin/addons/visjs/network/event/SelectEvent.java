package org.vaadin.addons.visjs.network.event;

import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import elemental.json.JsonException;
import elemental.json.JsonObject;
import org.vaadin.addons.visjs.network.api.Event;
import org.vaadin.addons.visjs.network.main.NetworkDiagram;

@SuppressWarnings("serial")
@DomEvent("vaadin-select")
public class SelectEvent extends Event {
  public SelectEvent(final NetworkDiagram source, boolean fromClient,
      @EventData("event.detail") final JsonObject params) throws JsonException {
    super(source, fromClient, params);
  }
}
