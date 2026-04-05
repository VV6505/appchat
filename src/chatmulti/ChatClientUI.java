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
        userName = JOptionPane.showInputDialog("Nhập tên của bạn:");
        if (userName == null || userName.isEmpty()) userName = "User" + (int)(Math.random()*100);
        setupUI();
        connect();
    }

    private void setupUI() {
        setTitle("Member - " + userName);
        setSize(800, 550);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(25, 25, 35));

        // Sidebar - Danh sách Online
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(new Color(15, 15, 25));
        sidebar.setPreferredSize(new Dimension(160, 0));
        JLabel lblOnline = new JLabel(" Online", SwingConstants.LEFT);
        lblOnline.setForeground(new Color(0, 212, 170));
        lblOnline.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JList<String> userList = new JList<>(userListModel);
        userList.setBackground(new Color(15, 15, 25));
        userList.setForeground(Color.WHITE);
        sidebar.add(lblOnline, BorderLayout.NORTH);
        sidebar.add(new JScrollPane(userList), BorderLayout.CENTER);

        // Chat Area
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(new Color(25, 25, 35));
        scrollPane = new JScrollPane(chatPanel);
        scrollPane.setBorder(null);

        // Bottom Bar
        JPanel bottom = new JPanel(new BorderLayout(5, 0));
        bottom.setBackground(new Color(15, 15, 25));
        bottom.setBorder(new EmptyBorder(10, 10, 10, 10));
        inputField = new JTextField();
        JButton btnSend = new JButton("GỬI");
        JButton btnImg = new JButton("📷");
        btnSend.addActionListener(e -> sendMsg());
        btnImg.addActionListener(e -> sendImage());
        
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
        JLabel label = new JLabel("<html><p style='width: 150px; padding: 5px;'>" + text + "</p></html>");
        label.setOpaque(true);
        label.setBackground(isMe ? new Color(80, 100, 240) : new Color(45, 45, 65));
        label.setForeground(Color.WHITE);
        row.add(label);
        chatPanel.add(row);
        chatPanel.revalidate();
        scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());
    }

    private void connect() {
        try {
            Socket s = new Socket("127.0.0.1", ChatServer.TCP_PORT);
            out = new DataOutputStream(s.getOutputStream());
            out.writeUTF(userName);
            
            new Thread(this::receiveMulticast).start();
            new Thread(() -> receiveTCP(s)).start();
        } catch (Exception e) { JOptionPane.showMessageDialog(this, "Không tìm thấy Server!"); }
    }

    private void receiveMulticast() {
        try (MulticastSocket ms = new MulticastSocket(ChatServer.MCAST_PORT)) {
            ms.joinGroup(InetAddress.getByName(ChatServer.MCAST_ADDR));
            byte[] buf = new byte[4096];
            while (true) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                ms.receive(p);
                String raw = new String(p.getData(), 0, p.getLength(), "UTF-8");
                String[] parts = raw.split("\\|");
                if (parts[0].equals("UPDATE_USERS:")) {
                    userListModel.clear();
                    for (String u : parts[1].substring(13).split(",")) userListModel.addElement("● " + u);
                } else if (!parts[0].equals("HISTORY")) {
                    addBubble(parts[1], parts[1].startsWith(userName + ":"));
                }
            }
        } catch (Exception e) {}
    }

    private void receiveTCP(Socket s) {
        try (DataInputStream in = new DataInputStream(s.getInputStream())) {
            while (true) {
                String m = in.readUTF();
                if (m.startsWith("HISTORY:")) addBubble("[Lịch sử] " + m.substring(16), false);
            }
        } catch (Exception e) { addBubble("Mất kết nối server.", false); }
    }

    private void sendMsg() {
        try { out.writeUTF("MSG:" + inputField.getText()); inputField.setText(""); } catch (Exception e) {}
    }

    private void sendImage() {
        JFileChooser jfc = new JFileChooser();
        if (jfc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = jfc.getSelectedFile();
            new Thread(() -> {
                try {
                    out.writeUTF("IMG:" + f.getName());
                    out.writeLong(f.length());
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