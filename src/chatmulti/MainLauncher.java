package chatmulti;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MainLauncher extends JFrame {
    private JTextField txtIP, txtPort;
    private static final Color BG_COLOR = new Color(240, 240, 245);
    private static final Color ACCENT_COLOR = new Color(70, 80, 250);

    public MainLauncher() {
        setTitle("Chat Connect System");
        setSize(450, 350);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Header giống Source 1 
        JLabel lblHeader = new JLabel("Chat Connect Server", SwingConstants.CENTER);
        lblHeader.setFont(new Font("Arial", Font.BOLD, 24));
        lblHeader.setForeground(Color.RED);
        lblHeader.setBorder(new EmptyBorder(20, 0, 10, 0));
        add(lblHeader, BorderLayout.NORTH);

        // Panel nhập liệu [cite: 262]
        JPanel pnlInput = new JPanel(new GridBagLayout());
        pnlInput.setBorder(BorderFactory.createTitledBorder("Tùy Chọn"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        pnlInput.add(new JLabel("IP Address:"), gbc);
        gbc.gridx = 1;
        txtIP = new JTextField("192.168.1.6", 15);
        pnlInput.add(txtIP, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        pnlInput.add(new JLabel("Port:"), gbc);
        gbc.gridx = 1;
        txtPort = new JTextField("8888", 15);
        pnlInput.add(txtPort, gbc);

        add(pnlInput, BorderLayout.CENTER);

        // Nút bấm chọn giao diện [cite: 257, 272]
        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 15));
        JButton btnServer = new JButton("MỞ GIAO DIỆN SERVER");
        JButton btnClient = new JButton("MỞ GIAO DIỆN CLIENT");

        styleButton(btnServer);
        styleButton(btnClient);

        btnServer.addActionListener(e -> {
            new ChatServer().setVisible(true);
            this.dispose();
        });

        btnClient.addActionListener(e -> {
            new ChatClientUI().setVisible(true);
            this.dispose();
        });

        pnlButtons.add(btnServer);
        pnlButtons.add(btnClient);
        add(pnlButtons, BorderLayout.SOUTH);
    }

    private void styleButton(JButton btn) {
        btn.setPreferredSize(new Dimension(180, 40));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } 
        catch (Exception e) { e.printStackTrace(); }
        SwingUtilities.invokeLater(() -> new MainLauncher().setVisible(true));
    }
}