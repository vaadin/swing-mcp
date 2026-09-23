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

import javax.swing.JPanel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@code JPanel} that records the mouse events it receives; {@link #wasDragged()} reports
 * whether they formed MOUSE_PRESSED → one or more MOUSE_DRAGGED → MOUSE_RELEASED.
 */
public class DragRecordingPanel extends JPanel {

    private final List<Integer> eventIds = new ArrayList<>();
    private final List<MouseEvent> dragEvents = new ArrayList<>();
    private MouseEvent pressEvent;
    private MouseEvent releaseEvent;
    private boolean sequenceValid = false;

    public DragRecordingPanel() {
        // Give the panel a non-zero size so synthetic events have valid coordinates
        setSize(100, 50);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                eventIds.add(e.getID());
                pressEvent = e;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                eventIds.add(e.getID());
                releaseEvent = e;
                validateSequence();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                eventIds.add(e.getID());
                dragEvents.add(e);
            }
        });
    }

    private void validateSequence() {
        if (eventIds.size() < 3) return;
        if (eventIds.get(0) != MouseEvent.MOUSE_PRESSED) return;
        if (eventIds.get(eventIds.size() - 1) != MouseEvent.MOUSE_RELEASED) return;
        for (int i = 1; i < eventIds.size() - 1; i++) {
            if (eventIds.get(i) != MouseEvent.MOUSE_DRAGGED) return;
        }
        sequenceValid = true;
    }

    public boolean wasDragged() {
        return sequenceValid;
    }

    public int getDragCount() {
        return dragEvents.size();
    }

    public List<MouseEvent> getDragEvents() {
        return new ArrayList<>(dragEvents);
    }

    /** @return the MOUSE_PRESSED event, or {@code null} if none arrived */
    public MouseEvent getPressEvent() {
        return pressEvent;
    }

    /** @return the MOUSE_RELEASED event, or {@code null} if none arrived */
    public MouseEvent getReleaseEvent() {
        return releaseEvent;
    }

    public List<Integer> getEventIds() {
        return new ArrayList<>(eventIds);
    }

    public void reset() {
        eventIds.clear();
        dragEvents.clear();
        pressEvent = null;
        releaseEvent = null;
        sequenceValid = false;
    }
}
