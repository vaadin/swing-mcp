package testapp.loginapp;

import testapp.DemoApp;

public class LoginApp implements DemoApp {
    @Override
    public String getName() {
        return "Login App";
    }

    @Override
    public void run() {
        MainWindow mainWindow = new MainWindow();
        var frame = mainWindow.buildAndShow();
        LoginDialog loginDialog = new LoginDialog(frame);
        loginDialog.setVisible(true);
        if (!loginDialog.isAuthenticated()) {
            frame.dispose();
        }
    }
}
