package org.vaadin.addons.visjs.network.listener;

import com.vaadin.flow.component.ComponentEventListener;
import org.vaadin.addons.visjs.network.event.HoverNodeEvent;

/**
 * Fired if the option interaction:{hover:true} is enabled and the mouse hovers over a node.
 *
 * @see <a href="http://visjs.org/docs/network/#Events">http://visjs.org/docs/network/#Events</a>
 *
 * @author watho
 *
 */
public interface HoverNodeListener extends ComponentEventListener<HoverNodeEvent> {
}
