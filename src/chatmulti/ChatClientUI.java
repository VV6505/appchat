package chatmulti;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.Collections;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

public class ChatClientUI extends JFrame {
    private final String serverHost;
    private final int serverPort;
    private String userName;
    private JPanel chatPanel;
    private JTextField inputField;
    private DefaultListModel<String> userListModel = new DefaultListModel<>();
    private DataOutputStream out;
    private JScrollPane scrollPane;

    public ChatClientUI(String serverHost, int serverPort) {
        this.serverHost = serverHost != null && !serverHost.isEmpty() ? serverHost.trim() : "127.0.0.1";
        this.serverPort = serverPort > 0 ? serverPort : ChatServer.TCP_PORT;
        userName = JOptionPane.showInputDialog(null, "Your Name:", "Login Frame", JOptionPane.QUESTION_MESSAGE);
        if (userName == null || userName.isEmpty()) userName = "Guest";
        setupUI();
        connect();
    }

    private void setupUI() {
        setTitle("Messenger — " + userName);
        setSize(780, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(UiTheme.WINDOW_BG);
        getRootPane().setBorder(new EmptyBorder(0, 14, 14, 14));

        JPanel sidebar = new JPanel(new BorderLayout(0, 8));
        sidebar.setOpaque(true);
        sidebar.setBackground(UiTheme.SIDEBAR_BG);
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(UiTheme.BORDER, 1),
                new EmptyBorder(12, 12, 12, 12)));
        sidebar.setPreferredSize(new Dimension(180, 0));

        JLabel lblOnline = new JLabel("Đang trực tuyến");
        lblOnline.setFont(UiTheme.uiFont(Font.BOLD, 13));
        lblOnline.setForeground(UiTheme.TEXT);
        lblOnline.setBorder(new EmptyBorder(0, 4, 0, 0));

        JList<String> userList = new JList<>(userListModel);
        userList.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        userList.setBackground(UiTheme.CARD);
        userList.setForeground(UiTheme.TEXT);
        userList.setSelectionBackground(UiTheme.LIST_SELECTION);
        userList.setSelectionForeground(UiTheme.TEXT);
        userList.setBorder(new LineBorder(UiTheme.BORDER, 1));
        JScrollPane listScroll = new JScrollPane(userList);
        listScroll.setBorder(null);

        sidebar.add(lblOnline, BorderLayout.NORTH);
        sidebar.add(listScroll, BorderLayout.CENTER);

        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(UiTheme.CHAT_BG);
        scrollPane = new JScrollPane(chatPanel);
        scrollPane.setBorder(new LineBorder(UiTheme.BORDER, 1));
        scrollPane.getViewport().setBackground(UiTheme.CHAT_BG);

        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.setOpaque(false);
        centerWrap.setBorder(new EmptyBorder(0, 0, 0, 10));
        centerWrap.add(scrollPane, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.setOpaque(true);
        bottom.setBackground(UiTheme.INPUT_BAR);
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER),
                new EmptyBorder(12, 0, 0, 0)));

        inputField = new JTextField();
        inputField.setFont(UiTheme.uiFont(Font.PLAIN, 14));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(UiTheme.BORDER, 1),
                new EmptyBorder(8, 12, 8, 12)));

        JButton btnSend = new JButton("Gửi");
        JButton btnImg = new JButton("Ảnh");
        UiTheme.stylePrimaryButton(btnSend);
        UiTheme.styleSecondaryButton(btnImg);
        btnSend.addActionListener(e -> sendMsg());
        btnImg.addActionListener(e -> sendFile("IMG:"));

        JPanel btnGrp = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnGrp.setOpaque(false);
        btnGrp.add(btnImg);
        btnGrp.add(btnSend);

        bottom.add(inputField, BorderLayout.CENTER);
        bottom.add(btnGrp, BorderLayout.EAST);

        JPanel body = new JPanel(new BorderLayout(0, 0));
        body.setOpaque(false);
        body.add(sidebar, BorderLayout.EAST);
        body.add(centerWrap, BorderLayout.CENTER);
        body.add(bottom, BorderLayout.SOUTH);

        setLayout(new BorderLayout(0, 0));
        add(UiTheme.accentBar(), BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        setLocationRelativeTo(null);
    }

    private void addBubble(String text, boolean isMe) {
        Runnable r = () -> {
            JPanel row = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 12, 6));
            row.setOpaque(false);
            row.setBackground(UiTheme.CHAT_BG);
            String esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>");
            JLabel l = new JLabel("<html><body style='width:220px;margin:0'>" + esc + "</body></html>");
            l.setOpaque(true);
            l.setFont(UiTheme.uiFont(Font.PLAIN, 14));
            l.setBackground(isMe ? UiTheme.BUBBLE_ME : UiTheme.BUBBLE_OTHER);
            l.setForeground(isMe ? UiTheme.BUBBLE_ME_TEXT : UiTheme.BUBBLE_OTHER_TEXT);
            l.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(isMe ? UiTheme.ACCENT_DARK : UiTheme.BORDER, 1, true),
                    new EmptyBorder(10, 14, 10, 14)));
            row.add(l);
            chatPanel.add(row);
            chatPanel.revalidate();
            scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    private void addImageBubble(String sender, String fileName, byte[] raw) {
        boolean isMe = sender.equals(userName);
        Runnable r = () -> {
            JPanel row = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 12, 8));
            row.setOpaque(false);
            row.setBackground(UiTheme.CHAT_BG);

            ImageIcon orig = new ImageIcon(raw);
            int w = orig.getIconWidth(), h = orig.getIconHeight();
            JLabel pic;
            if (w <= 0 || h <= 0) {
                pic = new JLabel("(Không hiển thị được ảnh)");
                pic.setFont(UiTheme.uiFont(Font.ITALIC, 13));
                pic.setForeground(UiTheme.TEXT_MUTED);
            } else {
                double sc = Math.min(1.0, Math.min(
                        (double) UiTheme.IMAGE_PREVIEW_MAX_W / w,
                        (double) UiTheme.IMAGE_PREVIEW_MAX_H / h));
                int nw = Math.max(1, (int) (w * sc));
                int nh = Math.max(1, (int) (h * sc));
                Image scaled = orig.getImage().getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
                pic = new JLabel(new ImageIcon(scaled));
            }
            pic.setBorder(new LineBorder(UiTheme.BORDER, 1));
            pic.setOpaque(true);
            pic.setBackground(UiTheme.CARD);

            JLabel cap = new JLabel(sender + "  ·  " + fileName);
            cap.setFont(UiTheme.uiFont(Font.PLAIN, 12));
            cap.setForeground(UiTheme.TEXT_MUTED);

            JPanel inner = new JPanel(new BorderLayout(6, 6));
            inner.setOpaque(true);
            inner.setBackground(isMe ? UiTheme.IMAGE_PANEL_ME : UiTheme.IMAGE_PANEL_OTHER);
            inner.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(isMe ? UiTheme.ACCENT : UiTheme.BORDER, 1, true),
                    new EmptyBorder(10, 12, 10, 12)));
            inner.add(cap, BorderLayout.NORTH);
            inner.add(pic, BorderLayout.CENTER);

            row.add(inner);
            chatPanel.add(row);
            chatPanel.revalidate();
            scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());
        };
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeLater(r);
    }

    private static String tcpHostForConnect(String host) {
        try {
            String h = host.trim();
            InetAddress target = InetAddress.getByName(h);
            if (target.isLoopbackAddress()) return h;
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
                    InetAddress a = ia.getAddress();
                    if (a != null && target.equals(a)) return "127.0.0.1";
                }
            }
            return h;
        } catch (Exception e) {
            return host.trim();
        }
    }

    private void connect() {
        try {
            String tcpHost = tcpHostForConnect(serverHost);
            Socket s = new Socket(tcpHost, serverPort);
            out = new DataOutputStream(s.getOutputStream());
            out.writeUTF(userName);
            new Thread(() -> receiveTCP(s)).start();
        } catch (Exception e) { JOptionPane.showMessageDialog(this, "Server Not Found!"); }
    }

    /** Parse payload giống multicast: {@code type|body} — nhận qua TCP LIVE (server vẫn gửi UDP song song). */
    private void handleBroadcastPayload(String payload) {
        int sep = payload.indexOf('|');
        if (sep < 0 || sep >= payload.length() - 1) return;
        String body = payload.substring(sep + 1);
        if (body.startsWith("UPDATE_USERS:")) {
            String list = body.substring("UPDATE_USERS:".length());
            SwingUtilities.invokeLater(() -> {
                userListModel.clear();
                for (String u : list.split(",")) {
                    if (!u.isEmpty()) userListModel.addElement("● " + u);
                }
            });
        } else {
            addBubble(body, body.startsWith(userName + ":"));
        }
    }

    private void receiveTCP(Socket s) {
        try (DataInputStream in = new DataInputStream(s.getInputStream())) {
            while (true) {
                String m = in.readUTF();
                if ("IMGCHUNK".equals(m)) {
                    String sender = in.readUTF();
                    String fn = in.readUTF();
                    int len = in.readInt();
                    if (len < 0 || len > 20 * 1024 * 1024) continue;
                    byte[] buf = new byte[len];
                    in.readFully(buf);
                    addImageBubble(sender, fn, buf);
                } else if (m.startsWith("HISTORY:")) {
                    String rest = m.substring(8);
                    int h = rest.indexOf('|');
                    if (h >= 0 && h < rest.length() - 1)
                        addBubble("" + rest.substring(h + 1), false);
                } else if (m.startsWith("LIVE:")) {
                    handleBroadcastPayload(m.substring(5));
                }
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

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> {
            ConnectDialog.Result r = ConnectDialog.show(null);
            if (r != null) new ChatClientUI(r.host, r.port).setVisible(true);
        });
    }
}