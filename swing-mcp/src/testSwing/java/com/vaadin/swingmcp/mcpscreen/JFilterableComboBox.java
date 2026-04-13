package com.vaadin.swingmcp.mcpscreen;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.ArrayList;
import java.util.List;

/**
 * A test helper: editable JComboBox that filters its items by a case-insensitive
 * starts-with match on the editor text. Used to verify that the AI client can
 * interact with filterable combo boxes via {@code set_text} on the editor child
 * followed by {@code get_items} / {@code get_item_count}
 * on the combo ref.
 */
public class JFilterableComboBox extends JComboBox<String> {

    private final List<String> allItems;
    private boolean filtering;

    public JFilterableComboBox(String... items) {
        allItems = List.of(items);
        for (String item : allItems) {
            addItem(item);
        }
        setEditable(true);

        JTextField editor = (JTextField) getEditor().getEditorComponent();
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
    }

    /** Returns the current text in the editor field. */
    public String getFilterText() {
        return ((JTextField) getEditor().getEditorComponent()).getText();
    }

    private void applyFilter() {
        if (filtering) return;
        // Capture the prefix now; defer the model change so it does not
        // re-enter the DocumentListener while the editor text is still
        // being modified (removeAllItems() resets the editor text).
        String filterText = getFilterText();
        String prefix = filterText.toLowerCase();
        SwingUtilities.invokeLater(() -> {
            if (filtering) return;
            filtering = true;
            try {
                List<String> matched = new ArrayList<>();
                for (String item : allItems) {
                    if (item.toLowerCase().startsWith(prefix)) {
                        matched.add(item);
                    }
                }
                removeAllItems();
                for (String item : matched) {
                    addItem(item);
                }
                // Restore the editor text — removeAllItems() clears it.
                getEditor().setItem(filterText);
            } finally {
                filtering = false;
            }
        });
    }
}
