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
import java.util.ArrayList;
import java.util.List;

/**
 * A {@code JPanel} whose own application {@code MouseListener} makes it a Tier 2 click target;
 * {@link #wasClicked()} reports whether it received exactly
 * MOUSE_PRESSED → MOUSE_RELEASED → MOUSE_CLICKED.
 */
public class ClickRecordingPanel extends JPanel {

    private final List<Integer> eventIds = new ArrayList<>();
    private boolean sequenceValid = false;

    public ClickRecordingPanel() {
        // Give the panel a non-zero size so synthetic clicks have valid center coordinates
        setSize(100, 50);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                eventIds.add(e.getID());
                validateEvent(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                eventIds.add(e.getID());
                validateEvent(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                eventIds.add(e.getID());
                validateEvent(e);
                if (eventIds.size() == 3
                        && eventIds.get(0) == MouseEvent.MOUSE_PRESSED
                        && eventIds.get(1) == MouseEvent.MOUSE_RELEASED
                        && eventIds.get(2) == MouseEvent.MOUSE_CLICKED) {
                    sequenceValid = true;
                }
            }

            private void validateEvent(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    sequenceValid = false;
                }
                if (e.getClickCount() != 1) {
                    sequenceValid = false;
                }
            }
        });
    }

    public boolean wasClicked() {
        return sequenceValid;
    }

    public List<Integer> getEventIds() {
        return new ArrayList<>(eventIds);
    }

    public void reset() {
        eventIds.clear();
        sequenceValid = false;
    }
}
