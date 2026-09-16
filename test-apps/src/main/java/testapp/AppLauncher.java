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
package testapp;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class AppLauncher {
    private final List<DemoApp> apps;

    public AppLauncher(List<DemoApp> apps) {
        this.apps = apps;
    }

    public void show() {
        JDialog dialog = new JDialog((Frame) null, "Select Application", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JComboBox<String> combo = new JComboBox<>();
        combo.setName("appCombo");
        for (DemoApp app : apps) {
            combo.addItem(app.getName());
        }

        JButton runButton = new JButton("Run");
        runButton.setName("runButton");
        runButton.addActionListener(e -> {
            int idx = combo.getSelectedIndex();
            if (idx >= 0) {
                dialog.dispose();
                apps.get(idx).run();
            }
        });

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel("Select application:"), BorderLayout.NORTH);
        panel.add(combo, BorderLayout.CENTER);
        panel.add(runButton, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }
}
