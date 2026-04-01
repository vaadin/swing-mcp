package testapp.loginapp;

import javax.swing.*;
import java.awt.*;

public class MainWindow {
    public JFrame buildAndShow() {
        JFrame frame = new JFrame("Login App");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        frame.add(new JLabel("The app contents", SwingConstants.CENTER), BorderLayout.CENTER);

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem quitItem = new JMenuItem("Quit");
        quitItem.setName("quitItem");
        quitItem.addActionListener(e -> frame.dispose());
        fileMenu.add(quitItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        frame.setSize(400, 300);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        return frame;
    }
}
