package testapp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import testapp.loginapp.LoginApp;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoginAppTest {

    @BeforeAll
    static void assertNotHeadless() {
        assertEquals("false", System.getProperty("java.awt.headless"), "Test requires a display");
        new JFrame(); // fails on truly headless environments
    }

    @AfterEach
    void closeAllWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Window w : Window.getWindows()) {
                w.dispose();
            }
        });
    }

    @Test
    void happyPath() throws Exception {
        // Launch picker — it shows a modal dialog, so use invokeLater
        SwingUtilities.invokeLater(() ->
                new AppLauncher(List.of(new LoginApp())).show());

        JDialog picker = waitForDialog("Select Application", 5_000);
        assertNotNull(picker, "Picker dialog should appear");

        // Select the demo app
        JComboBox<?> combo = findByName(picker, JComboBox.class, "appCombo");
        assertNotNull(combo, "appCombo should exist");
        SwingUtilities.invokeAndWait(() -> combo.setSelectedItem("Login App"));

        // Click Run — will lead to a modal login dialog, so use invokeLater
        JButton runButton = findByName(picker, JButton.class, "runButton");
        assertNotNull(runButton, "runButton should exist");
        SwingUtilities.invokeLater(() -> runButton.doClick());

        // Wait for the login dialog
        JDialog loginDialog = waitForDialog("Login", 5_000);
        assertNotNull(loginDialog, "Login dialog should appear");

        // Fill in credentials
        JTextField userField = findByName(loginDialog, JTextField.class, "username");
        JPasswordField passField = findByName(loginDialog, JPasswordField.class, "password");
        JButton loginButton = findByName(loginDialog, JButton.class, "loginButton");
        assertNotNull(userField, "username field should exist");
        assertNotNull(passField, "password field should exist");
        assertNotNull(loginButton, "loginButton should exist");

        SwingUtilities.invokeAndWait(() -> {
            userField.setText("admin");
            passField.setText("admin");
        });

        // Click Login — will dispose the modal dialog, so use invokeLater
        SwingUtilities.invokeLater(() -> loginButton.doClick());

        // Login dialog should close
        waitForWindowToClose(loginDialog, 5_000);
        assertFalse(loginDialog.isVisible(), "Login dialog should be closed after successful login");

        // Main window should now be accessible
        JFrame mainFrame = waitForFrame("Login App", 5_000);
        assertNotNull(mainFrame, "Main window should be visible after login");
        assertTrue(mainFrame.isVisible());

        // Quit via File > Quit
        JMenu fileMenu = mainFrame.getJMenuBar().getMenu(0);
        assertEquals("File", fileMenu.getText());
        JMenuItem quitItem = fileMenu.getItem(0);
        assertEquals("Quit", quitItem.getText());
        SwingUtilities.invokeAndWait(quitItem::doClick);

        assertFalse(mainFrame.isVisible(), "Main window should be closed after Quit");
    }

    // --- Helpers ---

    @SuppressWarnings("unchecked")
    private <T extends Component> T findByName(Component root, Class<T> type, String name) {
        if (type.isInstance(root) && name.equals(root.getName())) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = findByName(child, type, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private JDialog waitForDialog(String title, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && title.equals(d.getTitle()) && d.isVisible()) {
                    return d;
                }
            }
            Thread.sleep(50);
        }
        return null;
    }

    private JFrame waitForFrame(String title, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JFrame f && title.equals(f.getTitle()) && f.isVisible()) {
                    return f;
                }
            }
            Thread.sleep(50);
        }
        return null;
    }

    private void waitForWindowToClose(Window window, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!window.isVisible()) return;
            Thread.sleep(50);
        }
        fail("Window did not close within " + timeoutMs + "ms");
    }
}
