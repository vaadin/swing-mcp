package com.vaadin.swingmcp.mcp;

import javax.swing.JPanel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A reusable test component: a JPanel that registers a MouseAdapter on itself
 * and records the mouse event sequence. Verifies internally that events arrive
 * in the correct order (MOUSE_PRESSED → MOUSE_RELEASED → MOUSE_CLICKED), all
 * with BUTTON1 and click count 1.
 * <p>
 * Tests assert via {@link #wasClicked()} — returns {@code true} only if the
 * full sequence was received correctly.
 *
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
                // Check the full sequence after the final event
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

    /**
     * Returns {@code true} if the full mouse click event sequence was received
     * correctly: MOUSE_PRESSED → MOUSE_RELEASED → MOUSE_CLICKED, all with
     * BUTTON1 and click count 1.
     */
    public boolean wasClicked() {
        return sequenceValid;
    }

    /**
     * Returns the recorded event IDs for detailed assertions.
     */
    public List<Integer> getEventIds() {
        return new ArrayList<>(eventIds);
    }

    /**
     * Resets the recording state.
     */
    public void reset() {
        eventIds.clear();
        sequenceValid = false;
    }
}
