package org.vaadin.addons.visjs.network.listener;

import com.vaadin.flow.component.ComponentEventListener;
import org.vaadin.addons.visjs.network.event.BlurEdgeEvent;

/**
 * Fired if the option interaction:{hover:true} is enabled and the mouse moved away from an edge it
 * was hovering over before.
 *
 * @see <a href="http://visjs.org/docs/network/#Events">http://visjs.org/docs/network/#Events</a>
 *
 * @author watho
 *
 */
public interface BlurEdgeListener extends ComponentEventListener<BlurEdgeEvent> {
}
