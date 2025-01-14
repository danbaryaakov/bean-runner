package org.vaadin.addons.visjs.network.listener;

import com.vaadin.flow.component.ComponentEventListener;
import org.vaadin.addons.visjs.network.event.AfterDrawingEvent;

/**
 * Fired after drawing on the canvas has been completed. Can be used to draw on top of the network.
 *
 * @see <a href="http://visjs.org/docs/network/#Events">http://visjs.org/docs/network/#Events</a>
 *
 * @author watho
 *
 */
public interface AfterDrawingListener extends ComponentEventListener<AfterDrawingEvent> {
}
