package chatmulti;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.*;

public class ChatClientUI extends JFrame {
    private String userName;
    private JPanel chatPanel;
    private JTextField inputField;
    private DefaultListModel<String> userListModel = new DefaultListModel<>();
    private DataOutputStream out;
    private JScrollPane scrollPane;

    public ChatClientUI() {
        userName = JOptionPane.showInputDialog(null, "Your Name:", "Login Frame", JOptionPane.QUESTION_MESSAGE);
        if (userName == null || userName.isEmpty()) userName = "Guest";
        setupUI();
        connect();
    }

    private void setupUI() {
        setTitle("Messenger - " + userName);
        setSize(700, 550);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(30, 30, 40));

        // Sidebar - Online List
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(new Color(20, 20, 30));
        sidebar.setPreferredSize(new Dimension(150, 0));
        JLabel lblOnline = new JLabel(" ONLINE", SwingConstants.LEFT);
        lblOnline.setForeground(Color.GREEN);
        JList<String> userList = new JList<>(userListModel);
        userList.setBackground(new Color(20, 20, 30));
        userList.setForeground(Color.WHITE);
        sidebar.add(lblOnline, BorderLayout.NORTH);
        sidebar.add(new JScrollPane(userList), BorderLayout.CENTER);

        // Chat Area
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(new Color(30, 30, 40));
        scrollPane = new JScrollPane(chatPanel);
        scrollPane.setBorder(null);

        // Input Area
        JPanel bottom = new JPanel(new BorderLayout(5, 0));
        bottom.setBorder(new EmptyBorder(10, 10, 10, 10));
        bottom.setBackground(new Color(20, 20, 30));
        inputField = new JTextField();
        JButton btnSend = new JButton("Gửi");
        JButton btnImg = new JButton("📷 Ảnh");
        
        btnSend.addActionListener(e -> sendMsg());
        btnImg.addActionListener(e -> sendFile("IMG:"));
        
        JPanel btnGrp = new JPanel(new FlowLayout());
        btnGrp.setOpaque(false);
        btnGrp.add(btnImg); btnGrp.add(btnSend);
        
        bottom.add(inputField, BorderLayout.CENTER);
        bottom.add(btnGrp, BorderLayout.EAST);

        add(sidebar, BorderLayout.EAST);
        add(scrollPane, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setLocationRelativeTo(null);
    }

    private void addBubble(String text, boolean isMe) {
        JPanel row = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT));
        row.setOpaque(false);
        JLabel l = new JLabel("<html><p style='width: 150px; padding: 5px;'>" + text + "</p></html>");
        l.setOpaque(true);
        l.setBackground(isMe ? new Color(100, 120, 255) : new Color(60, 60, 70));
        l.setForeground(Color.WHITE);
        row.add(l);
        chatPanel.add(row); chatPanel.revalidate();
        scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());
    }

    private void connect() {
        try {
            Socket s = new Socket("127.0.0.1", ChatServer.TCP_PORT);
            out = new DataOutputStream(s.getOutputStream());
            out.writeUTF(userName);
            new Thread(this::receiveMulticast).start();
            new Thread(() -> receiveTCP(s)).start();
        } catch (Exception e) { JOptionPane.showMessageDialog(this, "Server Not Found!"); }
    }

    private void receiveMulticast() {
        try (MulticastSocket ms = new MulticastSocket(ChatServer.MCAST_PORT)) {
            ms.joinGroup(InetAddress.getByName(ChatServer.MCAST_ADDR));
            byte[] b = new byte[4096];
            while (true) {
                DatagramPacket p = new DatagramPacket(b, b.length);
                ms.receive(p);
                String[] parts = new String(p.getData(), 0, p.getLength(), "UTF-8").split("\\|");
                if (parts[1].startsWith("UPDATE_USERS:")) {
                    userListModel.clear();
                    for (String u : parts[1].substring(13).split(",")) userListModel.addElement("● " + u);
                } else {
                    addBubble(parts[1], parts[1].startsWith(userName + ":"));
                }
            }
        } catch (Exception e) {}
    }

    private void receiveTCP(Socket s) {
        try (DataInputStream in = new DataInputStream(s.getInputStream())) {
            while (true) {
                String m = in.readUTF();
                if (m.startsWith("HISTORY:")) addBubble("[Cũ] " + m.substring(8).split("\\|")[1], false);
            }
        } catch (Exception e) {}
    }

    private void sendMsg() {
        try { out.writeUTF("MSG:" + inputField.getText()); inputField.setText(""); } catch (Exception e) {}
    }

    private void sendFile(String prefix) {
        JFileChooser jfc = new JFileChooser();
        if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = jfc.getSelectedFile();
            new Thread(() -> {
                try {
                    out.writeUTF(prefix + f.getName()); out.writeLong(f.length());
                    try (FileInputStream fis = new FileInputStream(f)) {
                        byte[] buf = new byte[4096]; int r;
                        while ((r = fis.read(buf)) != -1) out.write(buf, 0, r);
                    }
                } catch (Exception e) {}
            }).start();
        }
    }

    public static void main(String[] args) { new ChatClientUI().setVisible(true); }
}