package chatmulti.ui;

import chatmulti.Server;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class Client extends JFrame {
    private static final int TAB_LOGIN = 0;
    private static final int TAB_WAIT = 1;
    private static final int TAB_CHAT = 2;

    private static final String PH_MSG = "Nhập tin nhắn...";

    /** Sticker gửi riêng (UDP STICKER|…) — bubble lớn. */
    private static final String[] STICKERS = {"👍", "❤️", "😂", "🔥", "🎉", "👋", "✨", "🙏", "😊", "💯"};
    /** Emoji chèn vào ô tin nhắn, gửi chung với text. */
    private static final String[] INLINE_EMOJIS = {
            "😀", "😃", "😄", "😁", "😅", "🤣", "😂", "🙂", "😉", "😊", "😍", "🥰",
            "😘", "😋", "😎", "🤩", "🥳", "😇", "🤔", "😴", "👍", "👎", "👏", "🙌",
            "🙏", "💪", "❤️", "💔", "🔥", "✨", "⭐", "💯", "🎉", "🎁", "✅", "❌"
    };

    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);

    private final JTextField tfUser = new JTextField(16);
    private final JTextField tfHost = new JTextField(18);
    private final JTextField tfTcpPort = new JTextField(6);
    private final JTextField tfUdpPort = new JTextField(6);

    private final JPanel waitPanel = new JPanel(new BorderLayout());
    private final JLabel lblWaitMain = new JLabel("Bạn hãy đợi một chút nhé.", SwingConstants.CENTER);
    private final JLabel lblWaitSub = new JLabel("Đang chờ server phân phòng...", SwingConstants.CENTER);
    private int dotPhase;
    private Timer waitTimer;

    private final JPanel chatOuter = new JPanel(new BorderLayout(0, 0));
    private final JLabel lblRoomTitle = new JLabel("Phòng: —");
    private JPanel chatFeed;
    private JScrollPane chatScroll;
    private final JTextField tfMsg = new JTextField();

    private volatile Socket tcpSocket;
    private volatile DataInputStream tcpIn;
    private volatile DataOutputStream tcpOut;
    private volatile DatagramSocket udpSocket;
    private volatile Thread tcpReaderThread;
    private volatile Thread udpReaderThread;
    private volatile String username;
    private volatile String currentRoom;
    private volatile String serverHost;
    private volatile boolean closing;

    public Client() {
        super("Chat Client");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(720, 580);
        getContentPane().setBackground(UiTheme.PANEL);
        getRootPane().setBorder(new EmptyBorder(0, 12, 12, 12));
        setLayout(new BorderLayout(0, 0));
        add(UiTheme.accentBar(), BorderLayout.NORTH);

        tfTcpPort.setText(String.valueOf(Server.TCP_PORT));
        tfUdpPort.setText(String.valueOf(Server.UDP_PORT));
        tfHost.setToolTipText("IP public VPS hoặc 127.0.0.1 khi test local");

        addPlaceholder(tfUser, "Tên của bạn");
        addPlaceholder(tfHost, "192.168.x.x");

        tabs.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        tabs.setBackground(UiTheme.SURFACE);
        tabs.addTab("Login", wrapWithDivider(buildLoginCard()));
        tabs.addTab("Chờ phòng", wrapWithDivider(buildWaitCard()));
        tabs.addTab("Chat", wrapWithDivider(buildChatCard()));

        tabs.setEnabledAt(TAB_WAIT, false);
        tabs.setEnabledAt(TAB_CHAT, false);

        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == TAB_CHAT && currentRoom == null)
                tabs.setSelectedIndex(TAB_WAIT);
        });

        add(tabs, BorderLayout.CENTER);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                closing = true;
                stopNetworking();
            }
        });
        setLocationRelativeTo(null);
    }

    private static JPanel wrapWithDivider(JPanel inner) {
        JPanel w = new JPanel(new BorderLayout(0, 0));
        w.setOpaque(false);
        JPanel line = new JPanel();
        line.setPreferredSize(new Dimension(0, 3));
        line.setBackground(UiTheme.PRIMARY);
        w.add(line, BorderLayout.NORTH);
        inner.setOpaque(true);
        w.add(inner, BorderLayout.CENTER);
        return w;
    }

    private static void addPlaceholder(JTextField tf, String ph) {
        tf.setForeground(UiTheme.MUTED);
        tf.setText(ph);
        tf.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (ph.equals(tf.getText())) {
                    tf.setText("");
                    tf.setForeground(UiTheme.TEXT);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (tf.getText().isEmpty()) {
                    tf.setText(ph);
                    tf.setForeground(UiTheme.MUTED);
                }
            }
        });
    }

    private boolean fieldIsPlaceholder(JTextField tf, String ph) {
        return ph.equals(tf.getText().trim()) || tf.getForeground().equals(UiTheme.MUTED) && tf.getText().equals(ph);
    }

    private String textOrEmpty(JTextField tf, String ph) {
        if (fieldIsPlaceholder(tf, ph)) return "";
        return tf.getText().trim();
    }

    private JPanel buildLoginCard() {
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setBackground(UiTheme.WINDOW);
        wrap.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(UiTheme.BORDER_BLUE, 1),
                new EmptyBorder(28, 32, 28, 32)));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 10, 8, 10);
        Font lf = UiTheme.uiFont(Font.PLAIN, 13);

        int y = 0;
        g.gridx = 0;
        g.gridy = y;
        g.gridwidth = 1;
        g.anchor = GridBagConstraints.LINE_END;
        JLabel a = new JLabel("Username:");
        a.setFont(lf);
        a.setForeground(UiTheme.TEXT);
        wrap.add(a, g);
        g.gridx = 1;
        g.anchor = GridBagConstraints.LINE_START;
        tfUser.setFont(lf);
        tfUser.setBorder(UiTheme.fieldBorder());
        wrap.add(tfUser, g);

        y++;
        g.gridx = 0;
        g.gridy = y;
        g.anchor = GridBagConstraints.LINE_END;
        JLabel b = new JLabel("IP server:");
        b.setFont(lf);
        b.setForeground(UiTheme.TEXT);
        wrap.add(b, g);
        g.gridx = 1;
        g.anchor = GridBagConstraints.LINE_START;
        tfHost.setFont(lf);
        tfHost.setBorder(UiTheme.fieldBorder());
        wrap.add(tfHost, g);

        y++;
        g.gridx = 0;
        g.gridy = y;
        g.anchor = GridBagConstraints.LINE_END;
        JLabel c = new JLabel("Cổng TCP:");
        c.setFont(lf);
        c.setForeground(UiTheme.TEXT);
        wrap.add(c, g);
        g.gridx = 1;
        tfTcpPort.setFont(lf);
        tfTcpPort.setBorder(UiTheme.fieldBorder());
        wrap.add(tfTcpPort, g);

        y++;
        g.gridx = 0;
        g.gridy = y;
        g.anchor = GridBagConstraints.LINE_END;
        JLabel d = new JLabel("Cổng UDP:");
        d.setFont(lf);
        d.setForeground(UiTheme.TEXT);
        wrap.add(d, g);
        g.gridx = 1;
        tfUdpPort.setFont(lf);
        tfUdpPort.setBorder(UiTheme.fieldBorder());
        wrap.add(tfUdpPort, g);

        y++;
        g.gridx = 0;
        g.gridy = y;
        g.gridwidth = 2;
        g.anchor = GridBagConstraints.CENTER;
        JButton btn = new JButton("Connect");
        UiTheme.stylePrimaryButton(btn);
        btn.addActionListener(e -> doConnect());
        wrap.add(btn, g);

        JPanel holder = new JPanel(new GridBagLayout());
        holder.setBackground(UiTheme.PANEL);
        holder.add(wrap, new GridBagConstraints());
        return holder;
    }

    private JPanel buildWaitCard() {
        waitPanel.setBackground(UiTheme.WAIT_BG);
        waitPanel.setBorder(new EmptyBorder(32, 24, 32, 24));

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel icon = new JLabel("◎", SwingConstants.CENTER);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        icon.setFont(UiTheme.uiFont(Font.PLAIN, 52));
        icon.setForeground(UiTheme.WAIT_ACCENT);

        lblWaitMain.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblWaitMain.setFont(UiTheme.uiFont(Font.BOLD, 17));
        lblWaitMain.setForeground(UiTheme.WAIT_ACCENT);
        lblWaitSub.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblWaitSub.setFont(UiTheme.uiFont(Font.PLAIN, 14));
        lblWaitSub.setForeground(UiTheme.WAIT_ACCENT);
        lblWaitSub.setBorder(new EmptyBorder(8, 0, 0, 0));

        center.add(Box.createVerticalGlue());
        center.add(icon);
        center.add(Box.createRigidArea(new Dimension(0, 16)));
        center.add(lblWaitMain);
        center.add(lblWaitSub);
        center.add(Box.createVerticalGlue());

        waitPanel.add(center, BorderLayout.CENTER);

        JButton btnDisc = new JButton("Rời khỏi phòng");
        UiTheme.styleSecondaryButton(btnDisc);
        btnDisc.addActionListener(e -> disconnectToLogin());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.setOpaque(false);
        south.add(btnDisc);
        waitPanel.add(south, BorderLayout.SOUTH);

        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(UiTheme.PANEL);
        holder.add(waitPanel, BorderLayout.CENTER);
        return holder;
    }

    private JPanel buildChatCard() {
        chatOuter.setBackground(UiTheme.PANEL);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(UiTheme.SURFACE);
        header.setBorder(new EmptyBorder(10, 14, 10, 14));
        lblRoomTitle.setFont(UiTheme.uiFont(Font.BOLD, 15));
        lblRoomTitle.setForeground(UiTheme.TEXT);
        header.add(lblRoomTitle, BorderLayout.WEST);
        JButton btnDisc = new JButton("Ngắt kết nối");
        UiTheme.styleSecondaryButton(btnDisc);
        btnDisc.addActionListener(e -> disconnectToLogin());
        header.add(btnDisc, BorderLayout.EAST);

        chatFeed = new JPanel();
        chatFeed.setLayout(new BoxLayout(chatFeed, BoxLayout.Y_AXIS));
        chatFeed.setBackground(UiTheme.WINDOW);
        chatScroll = new JScrollPane(chatFeed);
        chatScroll.setBorder(new LineBorder(UiTheme.BORDER_BLUE, 1));
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
        inputBar.setOpaque(true);
        inputBar.setBackground(UiTheme.PANEL);
        inputBar.setBorder(new EmptyBorder(10, 0, 0, 0));

        tfMsg.setFont(UiTheme.uiFont(Font.PLAIN, 14));
        tfMsg.setOpaque(true);
        tfMsg.setBackground(UiTheme.WINDOW);
        tfMsg.setBorder(new EmptyBorder(6, 10, 6, 10));
        addPlaceholder(tfMsg, PH_MSG);

        JButton btnEmoji = new JButton("😀");
        UiTheme.styleChatMiniButton(btnEmoji);
        btnEmoji.setToolTipText("Chèn emoji vào tin nhắn");
        btnEmoji.addActionListener(e -> showInlineEmojiPopup(btnEmoji));

        JButton btnMore = new JButton("⋯");
        UiTheme.styleChatMiniButton(btnMore);
        btnMore.setFont(UiTheme.uiFont(Font.BOLD, 16));
        btnMore.setToolTipText("Đính kèm");
        JPopupMenu attachMenu = new JPopupMenu();
        JMenuItem miFile = new JMenuItem("📎  Tệp");
        miFile.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        miFile.addActionListener(ev -> pickAndSendFile());
        JMenuItem miSticker = new JMenuItem("🏷  Nhãn dán");
        miSticker.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        miSticker.addActionListener(ev -> showStickerPicker());
        attachMenu.add(miFile);
        attachMenu.add(miSticker);
        btnMore.addActionListener(e -> attachMenu.show(btnMore, 0, btnMore.getHeight()));

        JButton send = new JButton("Gửi");
        UiTheme.stylePrimaryButton(send);
        send.addActionListener(e -> sendTextUdp());

        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        east.setOpaque(false);
        east.add(btnEmoji);
        east.add(btnMore);
        east.add(send);

        JPanel composeBar = new JPanel(new BorderLayout(8, 0));
        composeBar.setOpaque(true);
        composeBar.setBackground(UiTheme.WINDOW);
        composeBar.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(UiTheme.BORDER_BLUE, 1, true),
                new EmptyBorder(6, 8, 6, 8)));
        composeBar.add(tfMsg, BorderLayout.CENTER);
        composeBar.add(east, BorderLayout.EAST);

        inputBar.add(composeBar, BorderLayout.CENTER);

        chatOuter.add(header, BorderLayout.NORTH);
        chatOuter.add(chatScroll, BorderLayout.CENTER);
        chatOuter.add(inputBar, BorderLayout.SOUTH);

        tfMsg.addActionListener(e -> sendTextUdp());

        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(UiTheme.PANEL);
        holder.add(chatOuter, BorderLayout.CENTER);
        return holder;
    }

    private void doConnect() {
        String u = textOrEmpty(tfUser, "Tên của bạn");
        String hostRaw = textOrEmpty(tfHost, "192.168.x.x");
        if (u.isEmpty() || u.contains("|")) {
            JOptionPane.showMessageDialog(this, "Username không hợp lệ (không được rỗng hoặc chứa |).",
                    "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final String host = hostRaw.isEmpty() ? "127.0.0.1" : hostRaw;
        final int tcpPort;
        final int udpPort;
        try {
            tcpPort = Integer.parseInt(tfTcpPort.getText().trim());
            udpPort = Integer.parseInt(tfUdpPort.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Port không hợp lệ.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        stopNetworking();
        username = u;
        serverHost = host;
        closing = false;

        new Thread(() -> {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, tcpPort), 15000);
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                DataInputStream in = new DataInputStream(s.getInputStream());
                out.writeUTF("CONNECT|" + u);
                out.flush();
                String line = in.readUTF().trim();
                if (line.startsWith("ERROR|")) {
                    String err = line.length() > 6 ? line.substring(6) : "Lỗi";
                    s.close();
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(Client.this, err, "Không kết nối được", JOptionPane.ERROR_MESSAGE);
                        stopNetworking();
                    });
                    return;
                }
                if (!"OK_WAITING".equals(line)) {
                    s.close();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(Client.this,
                            "Phản hồi không hợp lệ: " + line, "Lỗi", JOptionPane.ERROR_MESSAGE));
                    return;
                }

                DatagramSocket ds = new DatagramSocket();
                byte[] reg = ("REGISTER|" + u).getBytes(StandardCharsets.UTF_8);
                ds.send(new DatagramPacket(reg, reg.length, InetAddress.getByName(host), udpPort));

                tcpSocket = s;
                tcpIn = in;
                tcpOut = out;
                udpSocket = ds;

                SwingUtilities.invokeLater(() -> {
                    setTitle("Chat Client — " + u);
                    tabs.setEnabledAt(TAB_LOGIN, false);
                    tabs.setEnabledAt(TAB_WAIT, true);
                    tabs.setEnabledAt(TAB_CHAT, false);
                    tabs.setSelectedIndex(TAB_WAIT);
                    showWaitUi();
                });

                tcpReaderThread = new Thread(this::tcpReadLoop, "chatmulti-client-tcp");
                tcpReaderThread.setDaemon(true);
                tcpReaderThread.start();
            } catch (Exception ex) {
                String msg;
                if (ex instanceof ConnectException || ex instanceof SocketTimeoutException)
                    msg = "Kết nối thất bại: Server chưa cập nhật";
                else
                    msg = "Kết nối thất bại: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(Client.this, msg, "Lỗi", JOptionPane.ERROR_MESSAGE));
                stopNetworking();
            }
        }, "chatmulti-connect").start();
    }

    private void showWaitUi() {
        dotPhase = 0;
        if (waitTimer != null) waitTimer.stop();
        lblWaitSub.setText("Đang chờ server phân phòng...");
        waitTimer = new Timer(450, e -> {
            dotPhase = (dotPhase + 1) % 4;
            String dots = dotPhase == 0 ? "" : dotPhase == 1 ? "." : dotPhase == 2 ? ".." : "...";
            lblWaitMain.setText("Bạn hãy đợi một chút nhé" + dots);
        });
        waitTimer.start();
    }

    private void tcpReadLoop() {
        try {
            DataInputStream in = tcpIn;
            if (in == null) return;
            while (!closing) {
                String line = in.readUTF().trim();
                if (line.startsWith("JOINED_ROOM|") && line.length() > "JOINED_ROOM|".length()) {
                    String room = line.substring("JOINED_ROOM|".length()).trim();
                    currentRoom = room;
                    SwingUtilities.invokeLater(() -> switchToChat(room));
                } else if ("BACK_TO_WAITING".equals(line)) {
                    currentRoom = null;
                    SwingUtilities.invokeLater(this::backToWaitingUi);
                } else if (line.startsWith("SYSTEM|") && line.length() > "SYSTEM|".length()) {
                    String sys = line.substring("SYSTEM|".length());
                    SwingUtilities.invokeLater(() -> {
                        if (currentRoom != null) addSystemBubble(sys);
                    });
                } else if ("FILE_RECV".equals(line)) {
                    String from = in.readUTF();
                    String fn = in.readUTF();
                    int len = in.readInt();
                    if (len < 0 || len > Server.MAX_FILE_BYTES) break;
                    byte[] data = new byte[len];
                    in.readFully(data);
                    SwingUtilities.invokeLater(() -> addFileBubble(from, fn, data, false));
                }
            }
        } catch (EOFException ignored) {
        } catch (IOException ignored) {
        } finally {
            if (!closing) SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(Client.this, "Mất kết nối server.", "Mất kết nối", JOptionPane.WARNING_MESSAGE);
                disconnectToLogin();
            });
        }
    }

    private void switchToChat(String room) {
        if (waitTimer != null) {
            waitTimer.stop();
            waitTimer = null;
        }
        lblRoomTitle.setText("Phòng: " + room);
        clearChatFeed();
        tabs.setEnabledAt(TAB_CHAT, true);
        tabs.setSelectedIndex(TAB_CHAT);
        if (udpReaderThread == null || !udpReaderThread.isAlive()) {
            udpReaderThread = new Thread(this::udpReadLoop, "chatmulti-client-udp");
            udpReaderThread.setDaemon(true);
            udpReaderThread.start();
        }
    }

    private void backToWaitingUi() {
        if (waitTimer != null) waitTimer.stop();
        tabs.setEnabledAt(TAB_CHAT, false);
        tabs.setSelectedIndex(TAB_WAIT);
        clearChatFeed();
        showWaitUi();
    }

    private void udpReadLoop() {
        byte[] buf = new byte[65507];
        while (!closing) {
            DatagramSocket ds = udpSocket;
            if (ds == null || ds.isClosed()) break;
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                ds.receive(p);
            } catch (IOException e) {
                break;
            }
            String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).trim();
            if (!msg.startsWith("ROOM_MSG|")) continue;
            String rest = msg.substring("ROOM_MSG|".length());
            String[] parts = rest.split("\\|", 3);
            if (parts.length < 3) continue;
            String room = parts[0];
            String user = parts[1];
            String payload = parts[2];
            String cr = currentRoom;
            if (cr == null || !cr.equals(room)) continue;
            String uLocal = username;
            boolean isMe = user.equals(uLocal);
            if (payload.startsWith("STICKER|")) {
                String em = payload.substring("STICKER|".length());
                SwingUtilities.invokeLater(() -> addStickerBubble(user, em, isMe));
            } else {
                SwingUtilities.invokeLater(() -> addTextBubble(user, payload, isMe));
            }
        }
    }

    private void sendTextUdp() {
        String text = textOrEmpty(tfMsg, PH_MSG);
        if (text.isEmpty()) return;
        sendRoomUdpPayload(text);
        addTextBubble(username, text, true);
        tfMsg.setText("");
        tfMsg.setForeground(UiTheme.TEXT);
    }

    private void sendStickerUdp(String emoji) {
        sendRoomUdpPayload("STICKER|" + emoji);
        addStickerBubble(username, emoji, true);
    }

    private void sendRoomUdpPayload(String payload) {
        String room = currentRoom;
        String u = username;
        String host = serverHost;
        if (room == null || u == null || host == null) return;
        DatagramSocket ds = udpSocket;
        if (ds == null) return;
        int udpPort;
        try {
            udpPort = Integer.parseInt(tfUdpPort.getText().trim());
        } catch (NumberFormatException e) {
            return;
        }
        String packet = "ROOM_MSG|" + room + "|" + u + "|" + payload;
        byte[] data = packet.getBytes(StandardCharsets.UTF_8);
        if (data.length > 65507) {
            JOptionPane.showMessageDialog(this, "Nội dung quá dài cho UDP.", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            ds.send(new DatagramPacket(data, data.length, InetAddress.getByName(host), udpPort));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Gửi UDP thất bại: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void pickAndSendFile() {
        if (currentRoom == null || tcpOut == null) return;
        JFileChooser jfc = new JFileChooser();
        if (jfc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = jfc.getSelectedFile();
        new Thread(() -> {
            try {
                byte[] buf = Files.readAllBytes(f.toPath());
                if (buf.length > Server.MAX_FILE_BYTES) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(Client.this,
                            "File tối đa 5 MB.", "Lỗi", JOptionPane.WARNING_MESSAGE));
                    return;
                }
                DataOutputStream out = tcpOut;
                synchronized (out) {
                    out.writeUTF("FILE");
                    out.writeUTF(f.getName());
                    out.writeLong(buf.length);
                    out.write(buf);
                    out.flush();
                }
                SwingUtilities.invokeLater(() -> addFileBubble(username, f.getName(), buf, true));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(Client.this,
                        "Gửi file lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }

    private void insertInlineEmoji(String em) {
        if (em == null || em.isEmpty()) return;
        if (fieldIsPlaceholder(tfMsg, PH_MSG)) {
            tfMsg.setText("");
            tfMsg.setForeground(UiTheme.TEXT);
        }
        int pos = Math.min(tfMsg.getCaretPosition(), tfMsg.getText().length());
        try {
            tfMsg.getDocument().insertString(pos, em, null);
            tfMsg.setCaretPosition(pos + em.length());
        } catch (BadLocationException ex) {
            tfMsg.setText(tfMsg.getText() + em);
        }
        tfMsg.requestFocusInWindow();
    }

    private void showInlineEmojiPopup(JButton anchor) {
        JPopupMenu pop = new JPopupMenu();
        JPanel grid = new JPanel(new GridLayout(0, 6, 4, 4));
        grid.setOpaque(true);
        grid.setBackground(UiTheme.WINDOW);
        grid.setBorder(new EmptyBorder(6, 8, 6, 8));
        for (String em : INLINE_EMOJIS) {
            JButton b = new JButton(em);
            b.setFont(b.getFont().deriveFont(18f));
            b.setFocusPainted(false);
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.addActionListener(ev -> {
                insertInlineEmoji(em);
                pop.setVisible(false);
            });
            grid.add(b);
        }
        pop.add(grid);
        pop.show(anchor, 0, anchor.getHeight());
    }

    /** Sticker riêng (bubble lớn), không gộp vào ô gõ. */
    private void showStickerPicker() {
        if (currentRoom == null) return;
        int opt = JOptionPane.showOptionDialog(this, "Chọn sticker gửi vào phòng:", "Nhãn dán",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, STICKERS, STICKERS[0]);
        if (opt >= 0) sendStickerUdp(STICKERS[opt]);
    }

    private void clearChatFeed() {
        chatFeed.removeAll();
        chatFeed.revalidate();
        chatFeed.repaint();
    }

    private void scrollChatBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = chatScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private void addTextBubble(String user, String text, boolean isMe) {
        JLabel l = bubbleLabel(escapeHtml(text), isMe);
        addBubbleRow(l, isMe);
        scrollChatBottom();
    }

    private void addStickerBubble(String user, String emoji, boolean isMe) {
        JLabel l = new JLabel(emoji, SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(36f));
        l.setOpaque(true);
        l.setBackground(isMe ? UiTheme.BUBBLE_ME : UiTheme.BUBBLE_OTHER);
        l.setForeground(isMe ? UiTheme.BUBBLE_ME_TEXT : UiTheme.BUBBLE_OTHER_TEXT);
        l.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(isMe ? UiTheme.PRIMARY_DARK : UiTheme.BORDER_BLUE, 1, true),
                new EmptyBorder(12, 18, 12, 18)));
        addBubbleRow(l, isMe);
        scrollChatBottom();
    }

    private void addSystemBubble(String text) {
        JLabel l = new JLabel("<html><div style='text-align:center'>" + escapeHtml(text) + "</div></html>");
        l.setOpaque(true);
        l.setBackground(UiTheme.BUBBLE_SYSTEM_BG);
        l.setForeground(UiTheme.BUBBLE_SYSTEM_TEXT);
        l.setFont(UiTheme.uiFont(Font.PLAIN, 12));
        l.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(UiTheme.DIVIDER, 1, true),
                new EmptyBorder(6, 14, 6, 14)));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
        row.setOpaque(false);
        row.setBackground(UiTheme.WINDOW);
        row.add(l);
        chatFeed.add(row);
        chatFeed.add(Box.createVerticalStrut(4));
        chatFeed.revalidate();
        scrollChatBottom();
    }

    private void addFileBubble(String user, String fileName, byte[] data, boolean isMe) {
        JPanel box = new JPanel(new BorderLayout(6, 6));
        box.setOpaque(true);
        box.setBackground(isMe ? UiTheme.BUBBLE_ME : UiTheme.BUBBLE_OTHER);
        box.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(isMe ? UiTheme.PRIMARY_DARK : UiTheme.BORDER_BLUE, 1, true),
                new EmptyBorder(10, 12, 10, 12)));

        JLabel cap = new JLabel(escapeHtml(user + " · " + fileName));
        cap.setFont(UiTheme.uiFont(Font.PLAIN, 12));
        cap.setForeground(isMe ? UiTheme.BUBBLE_ME_TEXT : UiTheme.BUBBLE_OTHER_TEXT);
        box.add(cap, BorderLayout.NORTH);

        try {
            java.awt.image.BufferedImage bi = ImageIO.read(new ByteArrayInputStream(data));
            if (bi != null) {
                int w = bi.getWidth(), h = bi.getHeight();
                double sc = Math.min(1.0, Math.min(
                        (double) UiTheme.IMAGE_PREVIEW_MAX_W / w,
                        (double) UiTheme.IMAGE_PREVIEW_MAX_H / h));
                int nw = Math.max(1, (int) (w * sc));
                int nh = Math.max(1, (int) (h * sc));
                Image scaled = bi.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
                JLabel pic = new JLabel(new ImageIcon(scaled));
                pic.setOpaque(false);
                box.add(pic, BorderLayout.CENTER);
            } else {
                JLabel info = new JLabel("📎 Tệp đính kèm");
                info.setForeground(isMe ? UiTheme.BUBBLE_ME_TEXT : UiTheme.BUBBLE_OTHER_TEXT);
                box.add(info, BorderLayout.CENTER);
            }
        } catch (IOException e) {
            JLabel info = new JLabel("📎 Tệp đính kèm");
            info.setForeground(isMe ? UiTheme.BUBBLE_ME_TEXT : UiTheme.BUBBLE_OTHER_TEXT);
            box.add(info, BorderLayout.CENTER);
        }

        addBubbleRow(box, isMe);
        scrollChatBottom();
    }

    private JLabel bubbleLabel(String htmlBody, boolean isMe) {
        JLabel l = new JLabel("<html><body style='width:240px;margin:0'>" + htmlBody + "</body></html>");
        l.setOpaque(true);
        l.setFont(UiTheme.uiFont(Font.PLAIN, 14));
        l.setBackground(isMe ? UiTheme.BUBBLE_ME : UiTheme.BUBBLE_OTHER);
        l.setForeground(isMe ? UiTheme.BUBBLE_ME_TEXT : UiTheme.BUBBLE_OTHER_TEXT);
        l.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(isMe ? UiTheme.PRIMARY_DARK : UiTheme.BORDER_BLUE, 1, true),
                new EmptyBorder(10, 14, 10, 14)));
        return l;
    }

    private void addBubbleRow(JComponent inner, boolean isMe) {
        JPanel row = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 12, 6));
        row.setOpaque(false);
        row.setBackground(UiTheme.WINDOW);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(inner);
        chatFeed.add(row);
        chatFeed.add(Box.createVerticalStrut(2));
        chatFeed.revalidate();
        chatFeed.repaint();
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>");
    }

    private void stopNetworking() {
        closing = true;
        if (waitTimer != null) {
            waitTimer.stop();
            waitTimer = null;
        }
        Socket s = tcpSocket;
        tcpSocket = null;
        tcpIn = null;
        tcpOut = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        DatagramSocket ds = udpSocket;
        udpSocket = null;
        if (ds != null && !ds.isClosed()) ds.close();
        currentRoom = null;
        username = null;
        serverHost = null;
    }

    private void disconnectToLogin() {
        stopNetworking();
        closing = false;
        setTitle("Chat Client");
        tabs.setEnabledAt(TAB_LOGIN, true);
        tabs.setEnabledAt(TAB_WAIT, false);
        tabs.setEnabledAt(TAB_CHAT, false);
        tabs.setSelectedIndex(TAB_LOGIN);
    }

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new Client().setVisible(true));
    }
}
