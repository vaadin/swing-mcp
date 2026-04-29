package testapp;

import com.vaadin.swingmcp.mcp.SwingMCPHandler;

import javax.swing.SwingUtilities;
import java.util.List;

public class Application {
    public static void main(String[] args) {
        new SwingMCPHandler().startAndAutoStop();
        SwingUtilities.invokeLater(() ->
                new AppLauncher(List.of(new testapp.loginapp.LoginApp())).show());
    }
}
