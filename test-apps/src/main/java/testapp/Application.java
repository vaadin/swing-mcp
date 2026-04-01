package testapp;

import javax.swing.SwingUtilities;
import java.util.List;

public class Application {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() ->
                new AppLauncher(List.of(new testapp.loginapp.LoginApp())).show());
    }
}
