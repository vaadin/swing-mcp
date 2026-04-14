package com.vaadin.swingmcp.mcp;

import javax.swing.JPanel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;

/**
 * A reusable test component: a JPanel that registers both a MouseAdapter and
 * a MouseMotionAdapter on itself to record drag event sequences. Verifies
 * internally that events arrive in the correct order: MOUSE_PRESSED followed
 * by one or more MOUSE_DRAGGED followed by MOUSE_RELEASED.
 * <p>
 * Tests assert via {@link #wasDragged()} — returns {@code true} only if a
 * valid drag sequence was received.
 *
 * @see <a href="use-case-022-swing-drag.md">UC-022 — DragRecordingPanel</a>
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
        // Valid sequence: PRESSED, then at least one DRAGGED, then RELEASED
        if (eventIds.size() < 3) return;
        if (eventIds.get(0) != MouseEvent.MOUSE_PRESSED) return;
        if (eventIds.get(eventIds.size() - 1) != MouseEvent.MOUSE_RELEASED) return;
        for (int i = 1; i < eventIds.size() - 1; i++) {
            if (eventIds.get(i) != MouseEvent.MOUSE_DRAGGED) return;
        }
        sequenceValid = true;
    }

    /**
     * Returns {@code true} if a valid drag sequence was received:
     * MOUSE_PRESSED → (one or more MOUSE_DRAGGED) → MOUSE_RELEASED.
     */
    public boolean wasDragged() {
        return sequenceValid;
    }

    /** Returns the number of MOUSE_DRAGGED events received. */
    public int getDragCount() {
        return dragEvents.size();
    }

    /** Returns all recorded MOUSE_DRAGGED events. */
    public List<MouseEvent> getDragEvents() {
        return new ArrayList<>(dragEvents);
    }

    /** Returns the MOUSE_PRESSED event, or null if none was received. */
    public MouseEvent getPressEvent() {
        return pressEvent;
    }

    /** Returns the MOUSE_RELEASED event, or null if none was received. */
    public MouseEvent getReleaseEvent() {
        return releaseEvent;
    }

    /** Returns the recorded event IDs for detailed assertions. */
    public List<Integer> getEventIds() {
        return new ArrayList<>(eventIds);
    }

    /** Resets the recording state. */
    public void reset() {
        eventIds.clear();
        dragEvents.clear();
        pressEvent = null;
        releaseEvent = null;
        sequenceValid = false;
    }
}
