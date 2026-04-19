package chatmulti.ui;

import chatmulti.Server;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class Client extends JFrame {

    private static final String PH_MSG = "Nhập tin nhắn...";
    private static final String[] STICKERS = { "👍", "❤️", "😂", "🔥", "🎉", "👋", "✨", "🙏", "😊", "💯" };
    private static final String[] INLINE_EMOJIS = {
            "😀", "😃", "😄", "😁", "😅", "🤣", "😂", "🙂", "😉", "😊", "😍", "🥰",
            "😘", "😋", "😎", "🤩", "🥳", "😇", "🤔", "😴", "👍", "👎", "👏", "🙌",
            "🙏", "💪", "❤️", "💔", "🔥", "✨", "⭐", "💯", "🎉", "🎁", "✅", "❌"
    };

    // ── Screens (CardLayout) ───────────────────────────────────────────────────
    private static final String SCREEN_LOGIN = "LOGIN";
    private static final String SCREEN_WAIT = "WAIT";
    private static final String SCREEN_CHAT = "CHAT";
    private static final String SCREEN_ADMIN = "ADMIN";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentArea = new JPanel(cardLayout);

    // ── Login fields ───────────────────────────────────────────────────────────
    private final JTextField tfUser = new JTextField();
    private final JTextField tfHost = new JTextField();
    private final JTextField tfTcpPort = new JTextField();
    private final JTextField tfUdpPort = new JTextField();
    private final JPasswordField tfAdminPass = new JPasswordField();

    // ── Wait screen ────────────────────────────────────────────────────────────
    private final JLabel lblWaitDot = new JLabel("◌", SwingConstants.CENTER);
    private final JLabel lblWaitMsg = new JLabel("Đang chờ phân phòng...", SwingConstants.CENTER);
    private int dotPhase;
    private Timer waitTimer;

    // ── Chat screen ────────────────────────────────────────────────────────────
    private final java.util.Map<String, JPanel> roomFeeds = new java.util.HashMap<>();
    private JPanel chatFeed;
    private JScrollPane chatScroll;
    private final JTextField tfMsg = new JTextField();
    private final JLabel lblRoomHeader = new JLabel("Phòng: —");
    private final JLabel lblOnlineCount = new JLabel("Đang online");
    private JLabel lblChatAvatar;
    private JPanel roomListPanel;

    // ── Admin screen ───────────────────────────────────────────────────────────
    private final DefaultListModel<String> adminOnlineModel = new DefaultListModel<>();
    private final DefaultListModel<String> adminWaitingModel = new DefaultListModel<>();
    private final DefaultListModel<String> adminRoomsModel = new DefaultListModel<>();
    private final JTextArea adminLogArea = new JTextArea();
    private final JTextField tfAdminNewRoom = new JTextField();
    private final JComboBox<String> cbAdminWaiting = new JComboBox<>();
    private final JComboBox<String> cbAdminRooms = new JComboBox<>();
    private final JComboBox<String> cbAdminInRoom = new JComboBox<>();

    // ── Network ────────────────────────────────────────────────────────────────
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
    private volatile boolean isAdmin;

    private volatile Socket adminTcpSocket;
    private volatile DataInputStream adminTcpIn;
    private volatile DataOutputStream adminTcpOut;
    private volatile Thread adminReaderThread;

    // ─────────────────────────────────────────────────────────────────────────
    public Client() {
        super("ChatApp");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1020, 680);
        setMinimumSize(new Dimension(820, 560));
        getContentPane().setBackground(UiTheme.BG);
        setUndecorated(false);

        // sidebar trái + content phải
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiTheme.SIDEBAR_BG);

        JPanel sidebar = buildSidebar();
        root.add(sidebar, BorderLayout.WEST);

        contentArea.setBackground(UiTheme.BG);
        contentArea.add(buildLoginScreen(), SCREEN_LOGIN);
        contentArea.add(buildWaitScreen(), SCREEN_WAIT);
        contentArea.add(buildChatScreen(), SCREEN_CHAT);
        contentArea.add(buildAdminScreen(), SCREEN_ADMIN);
        root.add(contentArea, BorderLayout.CENTER);

        setContentPane(root);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                closing = true;
                stopNetworking();
            }
        });
        setLocationRelativeTo(null);
        cardLayout.show(contentArea, SCREEN_LOGIN);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SIDEBAR
    // ═══════════════════════════════════════════════════════════════════════════
    private JPanel buildSidebar() {
        JPanel sb = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(UiTheme.SIDEBAR_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // subtle right edge
                g2.setColor(new Color(0x1E1B5E));
                g2.fillRect(getWidth() - 1, 0, 1, getHeight());
                g2.dispose();
            }
        };
        sb.setLayout(new BoxLayout(sb, BoxLayout.Y_AXIS));
        sb.setPreferredSize(new Dimension(64, 0));
        sb.setOpaque(false);

        // Không có nav buttons (icon không hiển thị được)
        sb.add(Box.createVerticalGlue());

        // Nút Admin (chỉ hiện khi là admin) — dùng text thuần
        JButton gearBtn = new JButton("ADM") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2.setColor(new Color(0x1E1B5E));
                    g2.fill(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        gearBtn.setFont(UiTheme.uiFont(Font.BOLD, 10));
        gearBtn.setForeground(new Color(0xAAACCC));
        gearBtn.setFocusPainted(false);
        gearBtn.setOpaque(false);
        gearBtn.setContentAreaFilled(false);
        gearBtn.setBorderPainted(false);
        gearBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        gearBtn.setPreferredSize(new Dimension(44, 32));
        gearBtn.setMaximumSize(new Dimension(44, 32));
        gearBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        gearBtn.addActionListener(e -> {
            if (isAdmin)
                cardLayout.show(contentArea, SCREEN_ADMIN);
        });
        sb.add(gearBtn);
        sb.add(Box.createRigidArea(new Dimension(0, 8)));

        JLabel avatarMe = new JLabel("?", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0xEF4444));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        avatarMe.setPreferredSize(new Dimension(36, 36));
        avatarMe.setMaximumSize(new Dimension(36, 36));
        avatarMe.setFont(UiTheme.uiFont(Font.BOLD, 14));
        avatarMe.setForeground(Color.WHITE);
        avatarMe.setAlignmentX(Component.CENTER_ALIGNMENT);
        avatarMe.setOpaque(false);
        sb.add(avatarMe);
        sb.add(Box.createRigidArea(new Dimension(0, 14)));

        return sb;
    }

    private JButton makeSidebarBtn(String icon, boolean active, Runnable onClick) {
        JButton b = new JButton(icon);
        UiTheme.styleSidebarIcon(b, active);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        if (onClick != null)
            b.addActionListener(e -> onClick.run());
        return b;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOGIN SCREEN
    // ═══════════════════════════════════════════════════════════════════════════
    private JPanel buildLoginScreen() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(UiTheme.BG);

        // Card trắng bo tròn
        UiTheme.RoundedPanel card = new UiTheme.RoundedPanel(new GridBagLayout(), 16, UiTheme.SURFACE);
        card.setShadow(true);
        card.setBorder(new EmptyBorder(36, 40, 36, 40));
        card.setPreferredSize(new Dimension(400, 460));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 0, 6, 0);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;

        // Logo + title
        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 1;
        JLabel logo = new JLabel("◉", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics gr) {
                Graphics2D g2 = (Graphics2D) gr.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, UiTheme.PRIMARY, getWidth(), getHeight(),
                        new Color(0x9333EA));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(gr);
            }
        };
        logo.setOpaque(false);
        logo.setPreferredSize(new Dimension(54, 54));
        logo.setFont(UiTheme.uiFont(Font.PLAIN, 28));
        JPanel logoWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        logoWrap.setOpaque(false);
        logoWrap.add(logo);
        card.add(logoWrap, g);

        g.gridy = 1;
        JLabel title = new JLabel("ChatApp", SwingConstants.CENTER);
        title.setFont(UiTheme.uiFont(Font.BOLD, 22));
        title.setForeground(UiTheme.TEXT);
        card.add(title, g);

        g.gridy = 2;
        JLabel sub = new JLabel("Kết nối và trò chuyện cùng mọi người", SwingConstants.CENTER);
        sub.setFont(UiTheme.uiFont(Font.PLAIN, 12));
        sub.setForeground(UiTheme.MUTED);
        card.add(sub, g);

        g.gridy = 3;
        g.insets = new Insets(14, 0, 3, 0);
        card.add(makeFieldLabel("Tên của bạn"), g);
        g.gridy = 4;
        g.insets = new Insets(0, 0, 6, 0);
        styleLoginField(tfUser, "Nhập username...");
        card.add(tfUser, g);

        g.gridy = 5;
        g.insets = new Insets(4, 0, 3, 0);
        card.add(makeFieldLabel("IP Server"), g);
        g.gridy = 6;
        g.insets = new Insets(0, 0, 6, 0);
        styleLoginField(tfHost, "172.188.65.249");
        tfHost.setText("172.188.65.249");
        card.add(tfHost, g);

        // TCP / UDP row
        g.gridy = 7;
        g.insets = new Insets(4, 0, 3, 0);
        JPanel portLabelRow = new JPanel(new GridLayout(1, 2, 12, 0));
        portLabelRow.setOpaque(false);
        portLabelRow.add(makeFieldLabel("Cổng TCP"));
        portLabelRow.add(makeFieldLabel("Cổng UDP"));
        card.add(portLabelRow, g);

        g.gridy = 8;
        g.insets = new Insets(0, 0, 6, 0);
        JPanel portRow = new JPanel(new GridLayout(1, 2, 12, 0));
        portRow.setOpaque(false);
        tfTcpPort.setText(String.valueOf(Server.TCP_PORT));
        tfUdpPort.setText(String.valueOf(Server.UDP_PORT));
        styleLoginField(tfTcpPort, "5000");
        styleLoginField(tfUdpPort, "6000");
        portRow.add(tfTcpPort);
        portRow.add(tfUdpPort);
        card.add(portRow, g);

        // Divider
        g.gridy = 9;
        g.insets = new Insets(8, 0, 8, 0);
        JSeparator sep = new JSeparator();
        sep.setForeground(UiTheme.BORDER);
        card.add(sep, g);

        g.gridy = 10;
        g.insets = new Insets(0, 0, 3, 0);
        card.add(makeFieldLabel("Mật khẩu admin (tùy chọn)"), g);
        g.gridy = 11;
        g.insets = new Insets(0, 0, 14, 0);
        tfAdminPass.setFont(UiTheme.uiFont(Font.PLAIN, 14));
        tfAdminPass.setBorder(BorderFactory.createCompoundBorder(
                new UiTheme.RoundedBorder(10, UiTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(9, 13, 9, 13)));
        tfAdminPass.setBackground(UiTheme.BG);
        card.add(tfAdminPass, g);

        // Buttons
        g.gridy = 12;
        g.insets = new Insets(0, 0, 8, 0);
        JButton btnConnect = new JButton("Kết nối");
        UiTheme.stylePrimaryButton(btnConnect);
        btnConnect.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        btnConnect.addActionListener(e -> doConnect(false));
        card.add(btnConnect, g);

        g.gridy = 13;
        g.insets = new Insets(0, 0, 0, 0);
        JButton btnQuick = new JButton("Quick Connect  (" + "172.188.65.249" + ")");
        UiTheme.styleSecondaryButton(btnQuick);
        btnQuick.addActionListener(e -> doConnect(true));
        card.add(btnQuick, g);

        outer.add(card, new GridBagConstraints());
        return outer;
    }

    private JLabel makeFieldLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(UiTheme.uiFont(Font.BOLD, 10));
        l.setForeground(UiTheme.MUTED);
        return l;
    }

    private void styleLoginField(JTextField tf, String placeholder) {
        tf.setFont(UiTheme.uiFont(Font.PLAIN, 14));
        tf.setBackground(UiTheme.BG);
        tf.setForeground(UiTheme.TEXT);
        tf.setBorder(BorderFactory.createCompoundBorder(
                new UiTheme.RoundedBorder(10, UiTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(9, 13, 9, 13)));
        tf.setOpaque(true);
        if (!placeholder.isEmpty()) {
            addPlaceholder(tf, placeholder);
        }
    }

    private static void addPlaceholder(JTextField tf, String ph) {
        tf.setForeground(UiTheme.MUTED);
        if (tf.getText().isEmpty())
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

    private boolean isPlaceholder(JTextField tf, String ph) {
        return ph.equals(tf.getText().trim()) && tf.getForeground().equals(UiTheme.MUTED);
    }

    private String fieldText(JTextField tf, String ph) {
        return isPlaceholder(tf, ph) ? "" : tf.getText().trim();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WAIT SCREEN
    // ═══════════════════════════════════════════════════════════════════════════
    private JPanel buildWaitScreen() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(UiTheme.BG);

        UiTheme.RoundedPanel card = new UiTheme.RoundedPanel(new BorderLayout(), 16, UiTheme.SURFACE);
        card.setShadow(true);
        card.setPreferredSize(new Dimension(320, 300));
        card.setBorder(new EmptyBorder(40, 36, 36, 36));

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        // Spinner icon lớn
        lblWaitDot.setFont(UiTheme.uiFont(Font.PLAIN, 56));
        lblWaitDot.setForeground(UiTheme.PRIMARY);
        lblWaitDot.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel heading = new JLabel("Chờ phân phòng", SwingConstants.CENTER);
        heading.setFont(UiTheme.uiFont(Font.BOLD, 18));
        heading.setForeground(UiTheme.TEXT);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblWaitMsg.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        lblWaitMsg.setForeground(UiTheme.MUTED);
        lblWaitMsg.setAlignmentX(Component.CENTER_ALIGNMENT);

        center.add(Box.createVerticalGlue());
        center.add(lblWaitDot);
        center.add(Box.createRigidArea(new Dimension(0, 18)));
        center.add(heading);
        center.add(Box.createRigidArea(new Dimension(0, 8)));
        center.add(lblWaitMsg);
        center.add(Box.createVerticalGlue());
        card.add(center, BorderLayout.CENTER);

        JButton btnCancel = new JButton("Hủy kết nối");
        UiTheme.styleSecondaryButton(btnCancel);
        btnCancel.addActionListener(e -> disconnectToLogin());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.setOpaque(false);
        south.add(btnCancel);
        card.add(south, BorderLayout.SOUTH);

        outer.add(card, new GridBagConstraints());
        return outer;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CHAT SCREEN — sidebar phòng + khung chat
    // ═══════════════════════════════════════════════════════════════════════════
    private JPanel buildChatScreen() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiTheme.BG);

        // Danh sách phòng (bên trái) — giả lập, chỉ hiển thị phòng hiện tại
        JPanel roomsPanel = buildRoomsPanel();
        root.add(roomsPanel, BorderLayout.WEST);

        // Khung chat chính
        JPanel chatMain = new JPanel(new BorderLayout());
        chatMain.setBackground(UiTheme.SURFACE);

        // Header phòng
        JPanel header = buildChatHeader();
        chatMain.add(header, BorderLayout.NORTH);

        // Feed tin nhắn
        chatFeed = new JPanel();
        chatFeed.setLayout(new BoxLayout(chatFeed, BoxLayout.Y_AXIS));
        chatFeed.setBackground(UiTheme.BG);
        chatFeed.setBorder(new EmptyBorder(12, 16, 12, 16));

        chatScroll = new JScrollPane(chatFeed);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        UiTheme.applySlimScrollBar(chatScroll);
        chatMain.add(chatScroll, BorderLayout.CENTER);

        // Input bar
        JPanel inputBar = buildInputBar();
        chatMain.add(inputBar, BorderLayout.SOUTH);

        root.add(chatMain, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildRoomsPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setPreferredSize(new Dimension(240, 0));
        p.setBackground(UiTheme.ROOMS_BG);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UiTheme.BORDER));

        // Header
        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        hdr.setBorder(new EmptyBorder(14, 16, 10, 16));
        JLabel title = new JLabel("Tin nhắn");
        title.setFont(UiTheme.uiFont(Font.BOLD, 17));
        title.setForeground(UiTheme.TEXT);
        hdr.add(title, BorderLayout.WEST);
        p.add(hdr, BorderLayout.NORTH);

        // Placeholder — room hiện tại sẽ hiển thị ở đây
        JPanel body = new JPanel();
        roomListPanel = body; // Gán biến này để có thể cập nhật danh sách phòng sau
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        // Placeholder item
        JPanel item = makeRoomListItem("—", "Chưa có phòng", false);
        body.add(item);

        JScrollPane sp = new JScrollPane(body);
        sp.setBorder(BorderFactory.createEmptyBorder());
        UiTheme.applySlimScrollBar(sp);
        p.add(sp, BorderLayout.CENTER);

        return p;
    }

    private JPanel makeRoomListItem(String roomName, String lastMsg, boolean active) {
        JPanel item = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (active) {
                    g2.setColor(UiTheme.PRIMARY_LIGHT);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        item.setOpaque(false);
        item.setBorder(new EmptyBorder(9, 12, 9, 12));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));

        Color avColor = UiTheme.avatarColor(roomName);
        String init = UiTheme.initials(roomName);
        JLabel avatar = UiTheme.makeAvatar(init, avColor, 42);
        item.add(avatar, BorderLayout.WEST);

        JPanel info = new JPanel(new BorderLayout(0, 3));
        info.setOpaque(false);
        JLabel name = new JLabel(roomName);
        name.setFont(UiTheme.uiFont(Font.BOLD, 13));
        name.setForeground(UiTheme.TEXT);
        JLabel last = new JLabel(lastMsg);
        last.setFont(UiTheme.uiFont(Font.PLAIN, 12));
        last.setForeground(UiTheme.MUTED);
        info.add(name, BorderLayout.NORTH);
        info.add(last, BorderLayout.SOUTH);
        item.add(info, BorderLayout.CENTER);

        return item;
    }

    private JPanel buildChatHeader() {
        JPanel hdr = new JPanel(new BorderLayout(12, 0));
        hdr.setBackground(UiTheme.SURFACE);
        hdr.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
                new EmptyBorder(11, 16, 11, 16)));

        JPanel left = new JPanel(new BorderLayout(10, 0));
        left.setOpaque(false);
        lblChatAvatar = UiTheme.makeAvatar("?", UiTheme.PRIMARY, 40);
        left.add(lblChatAvatar, BorderLayout.WEST);

        JPanel info = new JPanel(new BorderLayout(0, 2));
        info.setOpaque(false);
        lblRoomHeader.setFont(UiTheme.uiFont(Font.BOLD, 15));
        lblRoomHeader.setForeground(UiTheme.TEXT);
        lblOnlineCount.setFont(UiTheme.uiFont(Font.PLAIN, 12));
        lblOnlineCount.setForeground(UiTheme.ONLINE);
        info.add(lblRoomHeader, BorderLayout.NORTH);
        info.add(lblOnlineCount, BorderLayout.SOUTH);
        left.add(info, BorderLayout.CENTER);
        hdr.add(left, BorderLayout.WEST);

        // Chỉ giữ nút "Rời phòng" — bỏ các nút icon không hiển thị
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        JButton btnLeave = new JButton("Rời phòng");
        UiTheme.styleSecondaryButton(btnLeave);
        btnLeave.addActionListener(e -> disconnectToLogin());
        actions.add(btnLeave);
        hdr.add(actions, BorderLayout.EAST);

        return hdr;
    }

    private JPanel buildInputBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBackground(UiTheme.SURFACE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER),
                new EmptyBorder(10, 14, 10, 14)));

        // Input field bo tròn (chiếm tối đa không gian)
        // Dùng font Segoe UI làm font chính, nó sẽ tự động fallback sang Segoe UI Emoji
        // cho các ký tự emoji trên Windows 10/11 mà không làm hỏng font chữ thường.
        tfMsg.setFont(UiTheme.uiFont(Font.PLAIN, 14));
        tfMsg.setBackground(UiTheme.BG);
        tfMsg.setForeground(UiTheme.TEXT);
        tfMsg.setBorder(BorderFactory.createCompoundBorder(
                new UiTheme.RoundedBorder(22, UiTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(9, 16, 9, 16)));
        tfMsg.setOpaque(true);
        addPlaceholder(tfMsg, PH_MSG);
        tfMsg.addActionListener(e -> sendTextUdp());
        bar.add(tfMsg, BorderLayout.CENTER);

        // Bên phải: [☺ Emoji] [Tệp ▾ → dropdown] [Gửi]
        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightBtns.setOpaque(false);

        // Nút emoji
        JButton btnEmoji = makeTextToolBtn("Emoji");
        btnEmoji.addActionListener(e -> showEmojiPopup(btnEmoji));

        // Nút "Tệp ▾" — sổ popup chọn: Nhãn dán / Đính kèm tệp
        JButton btnAttach = makeTextToolBtn("Đính kèm");
        JPopupMenu attachMenu = new JPopupMenu();
        attachMenu.setBackground(UiTheme.SURFACE);

        JMenuItem miSticker = new JMenuItem("Nhãn dán");
        miSticker.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        miSticker.setForeground(UiTheme.TEXT);
        miSticker.addActionListener(ev -> showStickerPicker());

        JMenuItem miFile = new JMenuItem("Đính kèm tệp");
        miFile.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        miFile.setForeground(UiTheme.TEXT);
        miFile.addActionListener(ev -> pickAndSendFile());

        attachMenu.add(miSticker);
        attachMenu.addSeparator();
        attachMenu.add(miFile);

        btnAttach.addActionListener(e -> attachMenu.show(btnAttach, 0, -attachMenu.getPreferredSize().height));

        // Nút Gửi (text)
        JButton btnSend = new JButton("Gửi");
        UiTheme.stylePrimaryButton(btnSend);
        btnSend.addActionListener(e -> sendTextUdp());

        rightBtns.add(btnEmoji);
        rightBtns.add(btnAttach);
        rightBtns.add(btnSend);
        bar.add(rightBtns, BorderLayout.EAST);

        return bar;
    }

    /** Nút tool nhỏ trong input bar dùng text thuần, không phụ thuộc emoji */
    private JButton makeTextToolBtn(String label) {
        JButton b = new JButton(label);
        UiTheme.styleSecondaryButton(b);
        b.setBorder(BorderFactory.createCompoundBorder(
                new UiTheme.RoundedBorder(8, UiTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        return b;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ADMIN SCREEN
    // ═══════════════════════════════════════════════════════════════════════════
    private JPanel buildAdminScreen() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiTheme.BG);

        // Titlebar admin
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(UiTheme.SIDEBAR_BG);
        titleBar.setBorder(new EmptyBorder(13, 20, 13, 20));
        JLabel lbTitle = new JLabel("⚙  Quản trị — ChatApp");
        lbTitle.setFont(UiTheme.uiFont(Font.BOLD, 15));
        lbTitle.setForeground(Color.WHITE);
        titleBar.add(lbTitle, BorderLayout.WEST);
        JButton btnBack = new JButton("← Quay lại");
        btnBack.setBackground(new Color(0x1E1B5E));
        btnBack.setForeground(new Color(0xA5A7FF));
        btnBack.setFont(UiTheme.uiFont(Font.BOLD, 12));
        btnBack.setBorder(BorderFactory.createCompoundBorder(
                new UiTheme.RoundedBorder(8, new Color(0x3D3AAA), 1),
                new EmptyBorder(6, 14, 6, 14)));
        btnBack.setFocusPainted(false);
        btnBack.setOpaque(true);
        btnBack.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnBack.addActionListener(e -> cardLayout.show(contentArea, SCREEN_CHAT));
        titleBar.add(btnBack, BorderLayout.EAST);
        root.add(titleBar, BorderLayout.NORTH);

        // Body: left list + right controls
        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(UiTheme.BG);

        // LEFT: 3 lists
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setPreferredSize(new Dimension(280, 0));
        leftPanel.setBackground(UiTheme.ROOMS_BG);
        leftPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UiTheme.BORDER));

        leftPanel.add(buildAdminListCard("● Users online", adminOnlineModel, UiTheme.ONLINE));
        leftPanel.add(buildAdminListCard("○ Chờ phòng", adminWaitingModel, UiTheme.WAITING_COLOR));
        leftPanel.add(buildAdminListCard("▣ Danh sách phòng", adminRoomsModel, UiTheme.PRIMARY));
        body.add(leftPanel, BorderLayout.WEST);

        // RIGHT: controls + log
        JPanel rightPanel = new JPanel(new BorderLayout(0, 10));
        rightPanel.setBackground(UiTheme.BG);
        rightPanel.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Control card
        UiTheme.RoundedPanel ctrlCard = new UiTheme.RoundedPanel(new GridBagLayout(), 12, UiTheme.SURFACE);
        ctrlCard.setBorder(new EmptyBorder(16, 18, 18, 18));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 8, 6, 8);
        g.anchor = GridBagConstraints.WEST;

        int row = 0;

        // Section label
        g.gridx = 0;
        g.gridy = row;
        g.gridwidth = 4;
        g.fill = GridBagConstraints.HORIZONTAL;
        JLabel lbRoom = UiTheme.sectionLabel("Quản lý phòng");
        ctrlCard.add(lbRoom, g);
        g.gridwidth = 1;
        g.fill = GridBagConstraints.NONE;

        row++;
        g.gridx = 0;
        g.gridy = row;
        ctrlCard.add(adminLabel("Tên phòng mới"), g);
        g.gridx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        styleAdminField(tfAdminNewRoom);
        ctrlCard.add(tfAdminNewRoom, g);
        g.gridx = 2;
        g.fill = GridBagConstraints.NONE;
        g.weightx = 0;
        JButton btnCreate = new JButton("Tạo phòng");
        UiTheme.stylePrimaryButton(btnCreate);
        btnCreate.addActionListener(e -> {
            String r = tfAdminNewRoom.getText().trim();
            if (!r.isEmpty()) {
                adminSendCmd("CREATE_ROOM|" + r);
                tfAdminNewRoom.setText("");
            }
        });
        ctrlCard.add(btnCreate, g);
        g.gridx = 3;
        JButton btnDel = new JButton("Xóa phòng");
        UiTheme.styleSecondaryButton(btnDel);
        btnDel.addActionListener(e -> {
            Object r = cbAdminRooms.getSelectedItem();
            if (r == null)
                return;
            int ok = JOptionPane.showConfirmDialog(this,
                    "Xóa phòng \"" + r + "\"?", "Xác nhận", JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION)
                adminSendCmd("DELETE_ROOM|" + r.toString().trim());
        });
        ctrlCard.add(btnDel, g);

        row++;
        g.gridx = 0;
        g.gridy = row;
        ctrlCard.add(adminLabel("User chờ"), g);
        g.gridx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        styleAdminCombo(cbAdminWaiting);
        ctrlCard.add(cbAdminWaiting, g);

        row++;
        g.gridx = 0;
        g.gridy = row;
        g.fill = GridBagConstraints.NONE;
        ctrlCard.add(adminLabel("Chuyển vào phòng"), g);
        g.gridx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        cbAdminRooms.addActionListener(e -> refillAdminInRoomCombo());
        styleAdminCombo(cbAdminRooms);
        ctrlCard.add(cbAdminRooms, g);
        g.gridx = 2;
        g.fill = GridBagConstraints.NONE;
        JButton btnAdd = new JButton("Add vào phòng");
        UiTheme.stylePrimaryButton(btnAdd);
        btnAdd.addActionListener(e -> {
            Object w = cbAdminWaiting.getSelectedItem();
            Object r = cbAdminRooms.getSelectedItem();
            if (w != null && r != null)
                adminSendCmd("ADD_USER|" + w.toString().trim() + "|" + r.toString().trim());
        });
        ctrlCard.add(btnAdd, g);

        row++;
        g.gridx = 0;
        g.gridy = row;
        ctrlCard.add(adminLabel("Kick user"), g);
        g.gridx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        styleAdminCombo(cbAdminInRoom);
        ctrlCard.add(cbAdminInRoom, g);
        g.gridx = 2;
        g.fill = GridBagConstraints.NONE;
        JButton btnKick = new JButton("Kick khỏi phòng");
        UiTheme.styleSecondaryButton(btnKick);
        btnKick.addActionListener(e -> {
            Object u = cbAdminInRoom.getSelectedItem();
            Object r = cbAdminRooms.getSelectedItem(); // Lấy phòng đang chọn để kick
            if (u != null && r != null)
                adminSendCmd("KICK_USER|" + u.toString().trim() + "|" + r.toString().trim());
        });
        ctrlCard.add(btnKick, g);

        row++;
        g.gridx = 0;
        g.gridy = row;
        g.gridwidth = 1;
        ctrlCard.add(adminLabel("Xóa user"), g);
        g.gridx = 1;
        g.gridwidth = 2;
        g.fill = GridBagConstraints.HORIZONTAL;
        JButton btnPurge = new JButton("Xóa dữ liệu user");
        UiTheme.styleSecondaryButton(btnPurge);
        btnPurge.setForeground(new Color(0xEF4444)); // Màu đỏ cảnh báo
        btnPurge.addActionListener(e -> {
            String u = JOptionPane.showInputDialog(this, "Nhập tên User muốn xóa sạch dữ liệu:");
            if (u != null && !u.trim().isEmpty()) {
                int ok = JOptionPane.showConfirmDialog(this,
                        "Xác nhận xóa sạch dữ liệu của \"" + u + "\"?\n(Người này sẽ bị mất hết phòng đã tham gia)",
                        "Cảnh báo", JOptionPane.YES_NO_OPTION);
                if (ok == JOptionPane.YES_OPTION) {
                    adminSendCmd("DELETE_USER_DATA|" + u.trim());
                }
            }
        });
        ctrlCard.add(btnPurge, g);
        g.gridwidth = 1;

        row++;
        g.gridx = 0;
        g.gridy = row;
        g.gridwidth = 4;
        g.anchor = GridBagConstraints.EAST;
        JButton btnRefresh = new JButton("Làm mới");
        UiTheme.styleSecondaryButton(btnRefresh);
        btnRefresh.addActionListener(e -> adminSendCmd("REQUEST_SNAPSHOT"));
        ctrlCard.add(btnRefresh, g);

        rightPanel.add(ctrlCard, BorderLayout.NORTH);

        // Log console
        adminLogArea.setEditable(false);
        adminLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        adminLogArea.setBackground(UiTheme.LOG_BG);
        adminLogArea.setForeground(UiTheme.LOG_TEXT);
        adminLogArea.setBorder(new EmptyBorder(10, 14, 10, 14));
        JScrollPane logScroll = new JScrollPane(adminLogArea);
        UiTheme.applySlimScrollBar(logScroll);
        UiTheme.RoundedPanel logWrap = new UiTheme.RoundedPanel(new BorderLayout(), 10, UiTheme.LOG_BG);
        logWrap.add(logScroll, BorderLayout.CENTER);
        rightPanel.add(logWrap, BorderLayout.CENTER);

        body.add(rightPanel, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildAdminListCard(String title, DefaultListModel<String> model, Color dotColor) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER));

        JLabel lbl = new JLabel(title);
        lbl.setFont(UiTheme.uiFont(Font.BOLD, 12));
        lbl.setForeground(UiTheme.TEXT);
        lbl.setBorder(new EmptyBorder(10, 16, 8, 16));
        p.add(lbl, BorderLayout.NORTH);

        JList<String> list = new JList<>(model);
        list.setFont(UiTheme.uiFont(Font.PLAIN, 12));
        list.setBackground(UiTheme.ROOMS_BG);
        list.setForeground(UiTheme.TEXT);
        list.setSelectionBackground(UiTheme.PRIMARY_LIGHT);
        list.setFixedCellHeight(24);
        list.setCellRenderer((l, val, idx, sel, foc) -> {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            row.setOpaque(sel);
            row.setBackground(sel ? UiTheme.PRIMARY_LIGHT : UiTheme.ROOMS_BG);
            JLabel dot = new JLabel("●");
            dot.setFont(UiTheme.uiFont(Font.PLAIN, 9));
            dot.setForeground(dotColor);
            JLabel txt = new JLabel(val.toString());
            txt.setFont(UiTheme.uiFont(Font.PLAIN, 12));
            txt.setForeground(UiTheme.TEXT);
            row.add(dot);
            row.add(txt);
            return row;
        });
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(new EmptyBorder(0, 8, 8, 8));
        UiTheme.applySlimScrollBar(sp);
        sp.setPreferredSize(new Dimension(0, 100));
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private JLabel adminLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        l.setForeground(UiTheme.TEXT);
        return l;
    }

    private void styleAdminField(JTextField tf) {
        tf.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        tf.setBorder(BorderFactory.createCompoundBorder(
                new UiTheme.RoundedBorder(8, UiTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        tf.setBackground(UiTheme.BG);
    }

    private void styleAdminCombo(JComboBox<String> cb) {
        cb.setFont(UiTheme.uiFont(Font.PLAIN, 13));
        cb.setPreferredSize(new Dimension(200, 32));
    }

    private void refillAdminInRoomCombo() {
        cbAdminInRoom.removeAllItems();
        // Sẽ được cập nhật khi nhận SNAPSHOT
    }

    private void adminAppendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            adminLogArea.append(line + "\n");
            adminLogArea.setCaretPosition(adminLogArea.getDocument().getLength());
        });
    }

    private void adminSendCmd(String cmd) {
        DataOutputStream out = adminTcpOut;
        if (out == null)
            return;
        try {
            synchronized (out) {
                out.writeUTF("ADMIN_CMD|" + cmd);
                out.flush();
            }
        } catch (IOException ex) {
            adminAppendLog("[Lỗi] " + ex.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONNECT LOGIC
    // ═══════════════════════════════════════════════════════════════════════════
    private void doConnect(boolean isQuick) {
        String adminPass = new String(tfAdminPass.getPassword()).trim();
        boolean tryAdmin = !adminPass.isEmpty();

        String u = fieldText(tfUser, "Nhập username...");
        // Nếu là admin thì không cần username; nếu là user thường thì validate
        if (!tryAdmin && (u.isEmpty() || u.contains("|"))) {
            JOptionPane.showMessageDialog(this, "Username không hợp lệ.", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (tryAdmin && u.isEmpty())
            u = "admin"; // placeholder, không gửi lên server

        final String host;
        final int tcpPort, udpPort;
        if (isQuick) {
            host = "172.188.65.249";
            tcpPort = Server.TCP_PORT;
            udpPort = Server.UDP_PORT;
        } else {
            String h = fieldText(tfHost, "172.188.65.249");
            host = h.isEmpty() ? "172.188.65.249" : h;
            try {
                tcpPort = Integer.parseInt(tfTcpPort.getText().trim());
                udpPort = Integer.parseInt(tfUdpPort.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Port không hợp lệ.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
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

                if (tryAdmin) {
                    out.writeUTF("ADMIN_LOGIN|" + adminPass);
                    out.flush();
                    String resp = in.readUTF().trim();
                    if (resp.startsWith("ADMIN_ERROR|")) {
                        s.close();
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                                this, resp.substring("ADMIN_ERROR|".length()), "Lỗi admin", JOptionPane.ERROR_MESSAGE));
                        stopNetworking();
                        return;
                    }
                    adminTcpSocket = s;
                    adminTcpIn = in;
                    adminTcpOut = out;
                    isAdmin = true;
                    SwingUtilities.invokeLater(() -> {
                        setTitle("ChatApp — [ADMIN]");
                        cardLayout.show(contentArea, SCREEN_ADMIN);
                        adminAppendLog("[OK] Đã kết nối admin vào " + host + ":" + tcpPort);
                    });
                    adminReaderThread = new Thread(this::adminReadLoop, "chatmulti-admin-reader");
                    adminReaderThread.setDaemon(true);
                    adminReaderThread.start();
                } else {
                    out.writeUTF("CONNECT|" + username);
                    out.flush();
                    String line = in.readUTF().trim();
                    if (line.startsWith("ERROR|")) {
                        s.close();
                        String err = line.length() > 6 ? line.substring(6) : "Lỗi";
                        // Theo yêu cầu: thông báo lỗi trùng tên nếu Admin chưa giữ thông tin
                        if (err.contains("đã có trong hệ thống")) {
                            err = "Tên đã tồn tại hoặc Admin chưa chấp nhận giữ thông tin.";
                        }
                        final String finalErr = err;
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                                this, finalErr, "Không kết nối được", JOptionPane.ERROR_MESSAGE));
                        stopNetworking();
                        return;
                    }
                    DatagramSocket ds = new DatagramSocket();
                    byte[] reg = ("REGISTER|" + username).getBytes(StandardCharsets.UTF_8);
                    ds.send(new DatagramPacket(reg, reg.length, InetAddress.getByName(host), udpPort));

                    // Gửi lệnh khôi phục session (lấy danh sách phòng cũ)
                    out.writeUTF("GET_MY_ROOMS");
                    out.flush();

                    tcpSocket = s;
                    tcpIn = in;
                    tcpOut = out;
                    udpSocket = ds;
                    isAdmin = false;

                    SwingUtilities.invokeLater(() -> {
                        setTitle("ChatApp — " + username);
                        showWaitUi();
                    });
                    tcpReaderThread = new Thread(this::tcpReadLoop, "chatmulti-client-tcp");
                    tcpReaderThread.setDaemon(true);
                    tcpReaderThread.start();
                }
            } catch (Exception ex) {
                String msg = (ex instanceof ConnectException || ex instanceof SocketTimeoutException)
                        ? "Kết nối thất bại: Server chưa chạy hoặc sai IP"
                        : "Lỗi kết nối: " + ex.getMessage();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this, msg, "Lỗi", JOptionPane.ERROR_MESSAGE));
                stopNetworking();
            }
        }, "chatmulti-connect").start();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TCP READ LOOP
    // ═══════════════════════════════════════════════════════════════════════════
    private void tcpReadLoop() {
        try {
            DataInputStream in = tcpIn;
            if (in == null)
                return;
            while (!closing) {
                String line = in.readUTF().trim();
                if (line.startsWith("ROOM_LIST|")) {
                    String list = line.substring("ROOM_LIST|".length());
                    java.util.List<String> rList = list.isEmpty() ? new java.util.ArrayList<>()
                            : java.util.Arrays.asList(list.split(","));
                    SwingUtilities.invokeLater(() -> {
                        updateSidebarRoomList(rList);
                        if (rList.isEmpty()) {
                            currentRoom = null;
                            backToWaitingUi();
                        } else if (currentRoom == null || !rList.contains(currentRoom)) {
                            currentRoom = rList.get(0);
                            switchToChat(currentRoom);
                        }
                    });
                } else if (line.startsWith("JOINED_ROOM|")) {
                    currentRoom = line.substring("JOINED_ROOM|".length());
                    SwingUtilities.invokeLater(() -> switchToChat(currentRoom));
                } else if ("BACK_TO_WAITING".equals(line)) {
                    currentRoom = null;
                    SwingUtilities.invokeLater(this::backToWaitingUi);
                } else if (line.startsWith("SYSTEM|")) {
                    String sys = line.substring("SYSTEM|".length());
                    SwingUtilities.invokeLater(() -> {
                        if (currentRoom != null)
                            addSystemBubble(currentRoom, sys);
                    });
                } else if ("FILE_RECV".equals(line)) {
                    String from = in.readUTF();
                    String fn = in.readUTF();
                    int len = in.readInt();
                    if (len < 0 || len > Server.MAX_FILE_BYTES)
                        break;
                    byte[] data = new byte[len];
                    in.readFully(data);
                    String targetRoom = in.readUTF();
                    SwingUtilities.invokeLater(() -> addFileBubble(targetRoom, from, fn, data, false));
                }
            }
        } catch (EOFException ignored) {
        } catch (IOException ignored) {
        } finally {
            if (!closing)
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Đã mất kết nối.", "Ngắt kết nối", JOptionPane.WARNING_MESSAGE);
                    disconnectToLogin();
                });
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ADMIN READ LOOP
    // ═══════════════════════════════════════════════════════════════════════════
    private void adminReadLoop() {
        try {
            DataInputStream in = adminTcpIn;
            if (in == null)
                return;
            while (!closing) {
                String line;
                try {
                    line = in.readUTF().trim();
                } catch (EOFException | SocketException e) {
                    break;
                }
                processAdminEvent(line);
            }
        } catch (IOException ignored) {
        } finally {
            if (!closing)
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Mất kết nối admin.", "Ngắt kết nối",
                            JOptionPane.WARNING_MESSAGE);
                    disconnectToLogin();
                });
        }
    }

    private void processAdminEvent(String event) {
        if (event.startsWith("SNAPSHOT|"))
            parseAndApplySnapshot(event);
        else if (event.startsWith("USER_CONNECTED|"))
            SwingUtilities.invokeLater(() -> adminAppendLog("[+] Kết nối: " + event.substring(15)));
        else if (event.startsWith("USER_DISCONNECTED|"))
            SwingUtilities.invokeLater(() -> adminAppendLog("[-] Rời: " + event.substring(17)));
        else if (event.startsWith("USER_ROOM_CHANGED|"))
            SwingUtilities.invokeLater(() -> adminAppendLog("[↔] " + event.substring(17).replace("|", " → ")));
        else if (event.startsWith("ROOM_CREATED|"))
            SwingUtilities.invokeLater(() -> adminAppendLog("[+] Phòng mới: " + event.substring(13)));
        else if (event.startsWith("ROOM_DELETED|"))
            SwingUtilities.invokeLater(() -> adminAppendLog("[x] Xóa phòng: " + event.substring(13)));
        else if (event.startsWith("ADMIN_ACK|"))
            SwingUtilities.invokeLater(() -> adminAppendLog("[OK] " + event.substring(10)));
        else if (event.startsWith("ADMIN_ERR|"))
            SwingUtilities.invokeLater(() -> adminAppendLog("[ERR] " + event.substring(10)));
        if (!event.startsWith("ADMIN_ACK") && !event.startsWith("ADMIN_ERR")) {
            // Không gửi yêu cầu snapshot quá dồn dập
            requestSnapshotThrottled();
        }
    }

    private long lastSnapReq = 0;

    private void requestSnapshotThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastSnapReq > 500) { // Tối đa 2 lần/giây
            lastSnapReq = now;
            adminSendCmd("REQUEST_SNAPSHOT");
        }
    }

    private void parseAndApplySnapshot(String snap) {
        String[] sections = snap.split("\\|");
        java.util.List<String> online = new java.util.ArrayList<>();
        java.util.List<String> waiting = new java.util.ArrayList<>();
        java.util.Map<String, java.util.List<String>> rooms = new java.util.LinkedHashMap<>();
        for (String sec : sections) {
            if (sec.startsWith("ONLINE:"))
                for (String u : sec.substring(7).split(",")) {
                    if (!u.isBlank())
                        online.add(u.trim());
                }
            else if (sec.startsWith("WAITING:"))
                for (String u : sec.substring(8).split(",")) {
                    if (!u.isBlank())
                        waiting.add(u.trim());
                }
            else if (sec.startsWith("ROOMS:"))
                for (String entry : sec.substring(6).split(",")) {
                    if (entry.isBlank())
                        continue;
                    int eq = entry.indexOf('=');
                    if (eq < 0) {
                        rooms.put(entry.trim(), new java.util.ArrayList<>());
                        continue;
                    }
                    String rn = entry.substring(0, eq).trim();
                    java.util.List<String> ms = new java.util.ArrayList<>();
                    for (String m : entry.substring(eq + 1).split(";"))
                        if (!m.isBlank())
                            ms.add(m.trim());
                    rooms.put(rn, ms);
                }
        }
        final Object selRoom = cbAdminRooms.getSelectedItem();
        final Object selWait = cbAdminWaiting.getSelectedItem();

        SwingUtilities.invokeLater(() -> {
            // Cập nhật các Model (danh sách text)
            updateListModel(adminOnlineModel, online);
            updateListModel(adminWaitingModel, waiting);
            updateListModel(adminRoomsModel, new java.util.ArrayList<>(rooms.keySet()));

            // Cập nhật ComboBox thông minh (không làm giật menu)
            updateComboSmart(cbAdminWaiting, waiting);
            updateComboSmart(cbAdminRooms, new java.util.ArrayList<>(rooms.keySet()));

            if (selRoom != null)
                cbAdminRooms.setSelectedItem(selRoom);
            if (selWait != null)
                cbAdminWaiting.setSelectedItem(selWait);

            // Cập nhật danh sách User trong phòng đang chọn
            Object cur = cbAdminRooms.getSelectedItem();
            if (cur != null) {
                java.util.List<String> ms = rooms.get(cur.toString());
                if (ms != null) {
                    updateComboSmart(cbAdminInRoom, ms);
                }
            } else {
                cbAdminInRoom.removeAllItems();
            }
        });
    }

    private void updateListModel(DefaultListModel<String> model, java.util.List<String> newData) {
        model.clear();
        for (String s : newData)
            model.addElement(s);
    }

    private void updateComboSmart(JComboBox<String> combo, java.util.List<String> newData) {
        int count = combo.getItemCount();
        if (count == newData.size()) {
            boolean match = true;
            for (int i = 0; i < count; i++) {
                if (!combo.getItemAt(i).equals(newData.get(i))) {
                    match = false;
                    break;
                }
            }
            if (match)
                return;
        }
        Object selected = combo.getSelectedItem();
        combo.removeAllItems();
        for (String s : newData)
            combo.addItem(s);
        if (selected != null)
            combo.setSelectedItem(selected);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UDP READ LOOP
    // ═══════════════════════════════════════════════════════════════════════════
    private void udpReadLoop() {
        byte[] buf = new byte[65507];
        while (!closing) {
            DatagramSocket ds = udpSocket;
            if (ds == null || ds.isClosed())
                break;
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                ds.receive(p);
            } catch (IOException e) {
                break;
            }
            String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).trim();
            if (!msg.startsWith("ROOM_MSG|"))
                continue;
            String rest = msg.substring("ROOM_MSG|".length());
            String[] parts = rest.split("\\|", 3);
            if (parts.length < 3)
                continue;
            String room = parts[0], user = parts[1], payload = parts[2];
            boolean isMe = user.equals(username);
            if (payload.startsWith("STICKER|")) {
                String em = payload.substring("STICKER|".length());
                SwingUtilities.invokeLater(() -> addStickerBubble(room, user, em, isMe));
            } else {
                SwingUtilities.invokeLater(() -> addTextBubble(room, user, payload, isMe));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEND HELPERS
    // ═══════════════════════════════════════════════════════════════════════════
    private void sendTextUdp() {
        String text = isPlaceholder(tfMsg, PH_MSG) ? "" : tfMsg.getText().trim();
        if (text.isEmpty())
            return;
        sendRoomUdpPayload(text);
        addTextBubble(currentRoom, username, text, true);
        tfMsg.setText("");
        tfMsg.setForeground(UiTheme.TEXT);
    }

    private void sendStickerUdp(String emoji) {
        sendRoomUdpPayload("STICKER|" + emoji);
        addStickerBubble(currentRoom, username, emoji, true);
    }

    private void sendRoomUdpPayload(String payload) {
        String room = currentRoom, u = username, host = serverHost;
        if (room == null || u == null || host == null)
            return;
        DatagramSocket ds = udpSocket;
        if (ds == null)
            return;
        int udpPort;
        try {
            udpPort = Integer.parseInt(tfUdpPort.getText().trim());
        } catch (NumberFormatException e) {
            udpPort = Server.UDP_PORT;
        }
        String packet = "ROOM_MSG|" + room + "|" + u + "|" + payload;
        byte[] data = packet.getBytes(StandardCharsets.UTF_8);
        if (data.length > 65507)
            return;
        try {
            ds.send(new DatagramPacket(data, data.length, InetAddress.getByName(host), udpPort));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Gửi thất bại: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void pickAndSendFile() {
        if (currentRoom == null || tcpOut == null)
            return;
        JFileChooser jfc = new JFileChooser();
        if (jfc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        java.io.File f = jfc.getSelectedFile();
        new Thread(() -> {
            try {
                byte[] buf = Files.readAllBytes(f.toPath());
                if (buf.length > Server.MAX_FILE_BYTES) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            this, "File tối đa 5 MB.", "Lỗi", JOptionPane.WARNING_MESSAGE));
                    return;
                }
                DataOutputStream out = tcpOut;
                synchronized (out) {
                    out.writeUTF("FILE");
                    out.writeUTF(f.getName());
                    out.writeLong(buf.length);
                    out.write(buf);
                    out.writeUTF(currentRoom);
                    out.flush();
                }
                SwingUtilities.invokeLater(() -> addFileBubble(currentRoom, username, f.getName(), buf, true));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        this, "Gửi file lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE));
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUBBLE COMPONENTS
    // ═══════════════════════════════════════════════════════════════════════════

    
    private JPanel getRoomFeed(String room) {
        if (room == null) room = "";
        return roomFeeds.computeIfAbsent(room, k -> {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBackground(UiTheme.BG);
            p.setBorder(new EmptyBorder(12, 16, 12, 16));
            return p;
        });
    }

    private void addTextBubble(String room, String user, String text, boolean isMe) {
        JPanel row = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 10, 4));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        if (!isMe) {
            JLabel av = UiTheme.makeAvatar(UiTheme.initials(user), UiTheme.avatarColor(user), 32);
            av.setAlignmentY(Component.BOTTOM_ALIGNMENT);
            row.add(av);
        }

        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setOpaque(false);

        if (!isMe) {
            JLabel nameLbl = new JLabel(user);
            nameLbl.setFont(UiTheme.uiFont(Font.BOLD, 11));
            nameLbl.setForeground(UiTheme.PRIMARY);
            nameLbl.setBorder(new EmptyBorder(0, 2, 3, 0));
            nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            group.add(nameLbl);
        }

        JLabel bubble = new JLabel("<html><body style='width:230px;margin:0'>" + escHtml(text) + "</body></html>") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isMe ? UiTheme.BUBBLE_ME : UiTheme.BUBBLE_OTHER);
                // Bubble shape: full rounded for first, small corner for bottom
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 18, 18));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setFont(UiTheme.uiFont(Font.PLAIN, 14));
        bubble.setForeground(isMe ? UiTheme.BUBBLE_ME_TEXT : UiTheme.BUBBLE_OTHER_TEXT);
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(9, 14, 9, 14));
        bubble.setAlignmentX(isMe ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);
        group.add(bubble);

        // Timestamp
        String timeStr = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
        JLabel timeLbl = new JLabel(timeStr + (isMe ? "  ✓✓" : ""));
        timeLbl.setFont(UiTheme.uiFont(Font.PLAIN, 10));
        timeLbl.setForeground(UiTheme.MUTED);
        timeLbl.setBorder(new EmptyBorder(2, 4, 0, 2));
        timeLbl.setAlignmentX(isMe ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT);
        group.add(timeLbl);

        row.add(group);
        getRoomFeed(room).add(row);
        getRoomFeed(room).add(Box.createRigidArea(new Dimension(0, 2)));
        getRoomFeed(room).revalidate();
        if (room.equals(currentRoom)) scrollChatBottom();
    }

    private void addStickerBubble(String room, String user, String emoji, boolean isMe) {
        JPanel row = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 10, 4));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        JLabel lbl = new JLabel(emoji, SwingConstants.CENTER);
        lbl.setFont(lbl.getFont().deriveFont(42f));
        lbl.setOpaque(false);
        row.add(lbl);
        getRoomFeed(room).add(row);
        getRoomFeed(room).add(Box.createRigidArea(new Dimension(0, 2)));
        getRoomFeed(room).revalidate();
        if (room.equals(currentRoom)) scrollChatBottom();
    }

    private void addSystemBubble(String room, String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JLabel lbl = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiTheme.SYSTEM_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.setColor(new Color(0xF0E0A0));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        lbl.setFont(UiTheme.uiFont(Font.PLAIN, 12));
        lbl.setForeground(UiTheme.SYSTEM_TEXT);
        lbl.setOpaque(false);
        lbl.setBorder(new EmptyBorder(5, 16, 5, 16));
        row.add(lbl);
        getRoomFeed(room).add(row);
        getRoomFeed(room).revalidate();
        if (room.equals(currentRoom)) scrollChatBottom();
    }

    private void addFileBubble(String room, String user, String fileName, byte[] data, boolean isMe) {
        JPanel row = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 10, 4));
        row.setOpaque(false);
        JPanel box = new JPanel(new BorderLayout(6, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isMe ? UiTheme.BUBBLE_ME : UiTheme.BUBBLE_OTHER);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        box.setOpaque(false);
        box.setBorder(new EmptyBorder(10, 12, 10, 12));
        JLabel cap = new JLabel(user + " · " + fileName);
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
                Image scaled = bi.getScaledInstance(Math.max(1, (int) (w * sc)), Math.max(1, (int) (h * sc)),
                        Image.SCALE_SMOOTH);
                box.add(new JLabel(new ImageIcon(scaled)), BorderLayout.CENTER);
            } else {
                JLabel inf = new JLabel("[file] " + fileName);
                inf.setForeground(isMe ? UiTheme.BUBBLE_ME_TEXT : UiTheme.BUBBLE_OTHER_TEXT);
                box.add(inf, BorderLayout.CENTER);
            }
        } catch (IOException e) {
            JLabel inf = new JLabel("📎 " + fileName);
            inf.setForeground(isMe ? UiTheme.BUBBLE_ME_TEXT : UiTheme.BUBBLE_OTHER_TEXT);
            box.add(inf, BorderLayout.CENTER);
        }
        row.add(box);
        getRoomFeed(room).add(row);
        getRoomFeed(room).add(Box.createRigidArea(new Dimension(0, 2)));
        getRoomFeed(room).revalidate();
        if (room.equals(currentRoom)) scrollChatBottom();
    }

    private void scrollChatBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = chatScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private void clearChatFeed() {
        roomFeeds.clear();
        chatScroll.setViewportView(new JPanel());
    }

    private static String escHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UI STATE TRANSITIONS
    // ═══════════════════════════════════════════════════════════════════════════
    private void showWaitUi() {
        dotPhase = 0;
        if (waitTimer != null)
            waitTimer.stop();
        String[] frames = { "◌", "◍", "●", "◍" };
        waitTimer = new Timer(400, e -> {
            dotPhase = (dotPhase + 1) % frames.length;
            lblWaitDot.setText(frames[dotPhase]);
            String dots = ".".repeat(dotPhase);
            lblWaitMsg.setText("Đang chờ server phân phòng" + dots);
        });
        waitTimer.start();
        cardLayout.show(contentArea, SCREEN_WAIT);
    }

    private void switchToChat(String room) {
        if (waitTimer != null) {
            waitTimer.stop();
            waitTimer = null;
        }
        lblRoomHeader.setText("Phòng: " + room);
        lblOnlineCount.setText("● Đang online");

        String init = UiTheme.initials(room);
        Color avColor = UiTheme.avatarColor(room);
        lblChatAvatar.setText(init);
        lblChatAvatar.setBackground(avColor);

        chatScroll.setViewportView(getRoomFeed(room));
        cardLayout.show(contentArea, SCREEN_CHAT);
        if (udpReaderThread == null || !udpReaderThread.isAlive()) {
            udpReaderThread = new Thread(this::udpReadLoop, "chatmulti-client-udp");
            udpReaderThread.setDaemon(true);
            udpReaderThread.start();
        }
    }

    private void updateSidebarRoomList(java.util.List<String> rooms) {
        if (roomListPanel == null)
            return;
        roomListPanel.removeAll();
        if (rooms.isEmpty()) {
            roomListPanel.add(makeRoomListItem("—", "Chưa có phòng", true));
        } else {
            for (String r : rooms) {
                boolean active = r.equals(currentRoom);
                JPanel item = makeRoomListItem(r, active ? "Đang chat" : "Nhấn để vào", active);
                item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                item.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseClicked(java.awt.event.MouseEvent e) {
                        currentRoom = r;
                        switchToChat(r);
                        updateSidebarRoomList(rooms); // vẽ lại để cập nhật trạng thái active
                    }
                });
                roomListPanel.add(item);
                roomListPanel.add(Box.createRigidArea(new Dimension(0, 2)));
            }
        }
        roomListPanel.revalidate();
        roomListPanel.repaint();
    }

    private void backToWaitingUi() {
        if (waitTimer != null)
            waitTimer.stop();
        if (roomListPanel != null) {
            updateSidebarRoomList(new java.util.ArrayList<>());
        }
        clearChatFeed();
        showWaitUi();
    }

    private void disconnectToLogin() {
        stopNetworking();
        closing = false;
        setTitle("ChatApp");
        cardLayout.show(contentArea, SCREEN_LOGIN);
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
        if (s != null)
            try {
                s.close();
            } catch (IOException ignored) {
            }
        DatagramSocket ds = udpSocket;
        udpSocket = null;
        if (ds != null && !ds.isClosed())
            ds.close();
        Socket as = adminTcpSocket;
        adminTcpSocket = null;
        adminTcpIn = null;
        adminTcpOut = null;
        if (as != null)
            try {
                as.close();
            } catch (IOException ignored) {
            }
        currentRoom = null;
        username = null;
        serverHost = null;
        isAdmin = false;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EMOJI / STICKER
    // ═══════════════════════════════════════════════════════════════════════════
    private void showEmojiPopup(JButton anchor) {
        JPopupMenu pop = new JPopupMenu();
        JPanel grid = new JPanel(new GridLayout(0, 6, 4, 4));
        grid.setBackground(UiTheme.SURFACE);
        grid.setBorder(new EmptyBorder(8, 10, 8, 10));
        for (String em : INLINE_EMOJIS) {
            JButton b = new JButton(em);
            b.setFont(b.getFont().deriveFont(18f));
            b.setFocusPainted(false);
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.addActionListener(ev -> {
                if (isPlaceholder(tfMsg, PH_MSG)) {
                    tfMsg.setText("");
                    tfMsg.setForeground(UiTheme.TEXT);
                }
                int pos = Math.min(tfMsg.getCaretPosition(), tfMsg.getText().length());
                try {
                    tfMsg.getDocument().insertString(pos, em, null);
                } catch (BadLocationException ex) {
                    tfMsg.setText(tfMsg.getText() + em);
                }
                pop.setVisible(false);
            });
            grid.add(b);
        }
        pop.add(grid);
        pop.show(anchor, 0, anchor.getHeight());
    }

    private void showStickerPicker() {
        if (currentRoom == null)
            return;
        int opt = JOptionPane.showOptionDialog(this, "Chọn sticker:", "Nhãn dán",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, STICKERS, STICKERS[0]);
        if (opt >= 0)
            sendStickerUdp(STICKERS[opt]);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN
    // ═══════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.out.println("ChatApp Client v2.0");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new Client().setVisible(true));
    }
}
