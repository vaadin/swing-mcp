package testapp;

import com.vaadin.swingmcp.mcp.SwingMCP;

import javax.swing.SwingUtilities;
import java.util.List;

public class Application {
    public static void main(String[] args) {
        new SwingMCP().startAndAutoStop();
        SwingUtilities.invokeLater(() ->
                new AppLauncher(List.of(new testapp.loginapp.LoginApp())).show());
    }
}
