package org.vaadin.addons.visjs.network.listener;

import com.vaadin.flow.component.ComponentEventListener;
import org.vaadin.addons.visjs.network.event.DeselectNodeEvent;

/**
 * Fired when a node (or nodes) has (or have) been deselected by the user.
 *
 * @see <a href="http://visjs.org/docs/network/#Events">http://visjs.org/docs/network/#Events</a>
 *
 * @author watho
 *
 */
public interface DeselectNodeListener extends ComponentEventListener<DeselectNodeEvent> {
}
