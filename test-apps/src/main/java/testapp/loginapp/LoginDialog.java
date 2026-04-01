package testapp.loginapp;

import javax.swing.*;
import java.awt.*;

public class LoginDialog extends JDialog {
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private boolean authenticated = false;

    public LoginDialog(Frame owner) {
        super(owner, "Login", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        usernameField = new JTextField(15);
        usernameField.setName("username");
        passwordField = new JPasswordField(15);
        passwordField.setName("password");

        JButton loginButton = new JButton("Login");
        loginButton.setName("loginButton");
        loginButton.addActionListener(e -> attemptLogin());

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        formPanel.add(usernameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        formPanel.add(passwordField, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(loginButton);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(formPanel, BorderLayout.CENTER);
        content.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    private void attemptLogin() {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());
        if ("admin".equals(user) && "admin".equals(pass)) {
            authenticated = true;
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, "Invalid username or password.", "Login Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
}
