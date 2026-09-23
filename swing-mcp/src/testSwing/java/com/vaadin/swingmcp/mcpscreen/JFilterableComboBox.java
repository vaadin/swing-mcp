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
package com.vaadin.swingmcp.mcpscreen;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.ArrayList;
import java.util.List;

/**
 * An editable JComboBox that narrows its items to a case-insensitive prefix match of the
 * editor text, one EDT turn after the text changes. An agent types into the editor child
 * with {@code set_text}, then reads the combo with {@code get_items}.
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
