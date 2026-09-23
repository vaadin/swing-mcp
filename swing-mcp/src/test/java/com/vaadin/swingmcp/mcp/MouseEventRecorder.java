/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.swingmcp.mcp;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Records the presses, drags and releases a component receives, in order — for a component
 * that cannot be a {@link DragRecordingPanel}:
 *
 * <pre>{@code
 * MouseEventRecorder recorder = MouseEventRecorder.attachTo(list);
 * // ... drag ...
 * assertEquals(MouseEventRecorder.SYNTHETIC_DRAG, recorder.getEventIds());
 * }</pre>
 *
 * A listener that runs after one that throws never sees the event, so an empty recording can
 * mean the component's own UI delegate failed, not that nothing was dispatched.
 */
public final class MouseEventRecorder extends MouseAdapter {

    /** What the synthetic drag sends with no waypoints: a press, five drags, a release. */
    public static final List<Integer> SYNTHETIC_DRAG = List.of(
            MouseEvent.MOUSE_PRESSED,
            MouseEvent.MOUSE_DRAGGED, MouseEvent.MOUSE_DRAGGED, MouseEvent.MOUSE_DRAGGED,
            MouseEvent.MOUSE_DRAGGED, MouseEvent.MOUSE_DRAGGED,
            MouseEvent.MOUSE_RELEASED);

    private final List<MouseEvent> events = new ArrayList<>();

    private MouseEventRecorder() {
    }

    public static MouseEventRecorder attachTo(Component component) {
        MouseEventRecorder recorder = new MouseEventRecorder();
        component.addMouseListener(recorder);
        component.addMouseMotionListener(recorder);
        return recorder;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        events.add(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        events.add(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        events.add(e);
    }

    public List<MouseEvent> getEvents() {
        return new ArrayList<>(events);
    }

    public List<Integer> getEventIds() {
        List<Integer> ids = new ArrayList<>();
        for (MouseEvent e : events) {
            ids.add(e.getID());
        }
        return ids;
    }
}
