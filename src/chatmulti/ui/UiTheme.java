package chatmulti.ui;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public final class UiTheme {
    private UiTheme() {}

    // ── Image preview ──────────────────────────────────────────────────────────
    public static final int IMAGE_PREVIEW_MAX_W = 260;
    public static final int IMAGE_PREVIEW_MAX_H = 300;

    // ── Core palette ───────────────────────────────────────────────────────────
    public static final Color PRIMARY        = new Color(0x5B5EF4);
    public static final Color PRIMARY_DARK   = new Color(0x4341D1);
    public static final Color PRIMARY_LIGHT  = new Color(0xE8E8FE);

    public static final Color SIDEBAR_BG     = new Color(0x0F0C29);
    public static final Color SIDEBAR_ICON   = new Color(0xFFFFFF, true); // with alpha handling

    public static final Color SURFACE        = new Color(0xFFFFFF);
    public static final Color BG             = new Color(0xF0F2F9);
    public static final Color ROOMS_BG       = new Color(0xF7F8FC);

    public static final Color TEXT           = new Color(0x1A1740);
    public static final Color MUTED          = new Color(0x8B8FA8);
    public static final Color BORDER         = new Color(0xE4E6F0);

    public static final Color BUBBLE_ME      = PRIMARY;
    public static final Color BUBBLE_ME_TEXT = Color.WHITE;
    public static final Color BUBBLE_OTHER   = SURFACE;
    public static final Color BUBBLE_OTHER_TEXT = TEXT;

    public static final Color SYSTEM_BG      = new Color(0xFFF8E7);
    public static final Color SYSTEM_TEXT    = new Color(0x8A6400);

    public static final Color ONLINE         = new Color(0x22C55E);
    public static final Color WAITING_COLOR  = new Color(0xF59E0B);

    public static final Color LOG_BG         = SIDEBAR_BG;
    public static final Color LOG_TEXT       = new Color(0x7DD3FC);

    // Backward-compat
    public static final Color WINDOW         = SURFACE;
    public static final Color PANEL          = BG;
    public static final Color ACCENT         = PRIMARY;
    public static final Color ACCENT_DARK    = PRIMARY_DARK;
    public static final Color BORDER_BLUE    = new Color(0xC7C8FC);
    public static final Color DIVIDER        = BORDER;
    public static final Color TEXT_MUTED     = MUTED;
    public static final Color ONLINE_BG      = new Color(0xD1FAE5);
    public static final Color ONLINE_TEXT    = new Color(0x065F46);
    public static final Color SUCCESS_BG     = ONLINE_BG;
    public static final Color SUCCESS_TEXT   = ONLINE_TEXT;
    public static final Color BUBBLE_SYSTEM_BG   = SYSTEM_BG;
    public static final Color BUBBLE_SYSTEM_TEXT = SYSTEM_TEXT;
    public static final Color WAIT_BG        = new Color(0xEEF2FF);
    public static final Color WAIT_ACCENT    = PRIMARY;
    public static final Color LIST_SELECTION = PRIMARY_LIGHT;
    public static final Color CHAT_BG        = BG;
    public static final Color CARD           = SURFACE;
    public static final Color WINDOW_BG      = BG;
    public static final Color BUTTON_SECONDARY_BG = BG;

    // ── Fonts ──────────────────────────────────────────────────────────────────
    public static Font uiFont(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    /** Font render emoji trên Windows (Segoe UI Emoji), fallback SansSerif */
    public static Font emojiFont(int size) {
        // Segoe UI Emoji có sẵn trên Windows 10/11, hỗ trợ đầy đủ Unicode emoji
        Font f = new Font("Segoe UI Emoji", Font.PLAIN, size);
        if (f.getFamily().equals("Dialog")) {
            // Fallback: thử Noto Emoji hoặc SansSerif
            f = new Font("SansSerif", Font.PLAIN, size);
        }
        return f;
    }

    private static Font cachedEmojiFont = null;

    public static Font loadEmojiFont() {
        if (cachedEmojiFont != null) {
            return cachedEmojiFont.deriveFont(Font.PLAIN, 18f);
        }
        
        // CẢNH BÁO: Java Swing KHÔNG hỗ trợ định dạng ảnh Bitmap (CBDT) của NotoColorEmoji.ttf 
        // dẫn đến việc font này bị tàng hình (trắng bóc) khi được vẽ lên JPopupMenu.
        // Giải pháp an toàn nhất để bảng Emoji hiển thị được là dùng Segoe UI Emoji mặc định của Windows.
        cachedEmojiFont = new Font("Segoe UI Emoji", Font.PLAIN, 18);
        return cachedEmojiFont;
    }

    public static void styleRoundedField(JTextField tf) {
        // Dùng loadEmojiFont (Segoe UI Emoji) để ô nhập liệu hiển thị được cả chữ và Emoji
        tf.setFont(loadEmojiFont().deriveFont(Font.PLAIN, 14f));
        tf.setOpaque(false);
        tf.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(22, BORDER, 1),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        tf.setBackground(BG);
        tf.setForeground(TEXT);
    }

    // ── Rounded border tùy chỉnh ───────────────────────────────────────────────
    public static class RoundedBorder extends AbstractBorder {
        private final int radius;
        private final Color color;
        private final int thickness;

        public RoundedBorder(int radius, Color color, int thickness) {
            this.radius = radius;
            this.color = color;
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            g2.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, radius, radius));
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness + 2, thickness + 2, thickness + 2, thickness + 2);
        }
    }

    // ── Shadow border ──────────────────────────────────────────────────────────
    public static Border fieldBorder() {
        return BorderFactory.createCompoundBorder(
                new RoundedBorder(10, BORDER, 1),
                BorderFactory.createEmptyBorder(9, 13, 9, 13));
    }

    public static Border bubbleBorder(boolean isMe) {
        return BorderFactory.createCompoundBorder(
                new RoundedBorder(18, isMe ? new Color(0x4341D1, true) : BORDER, 0),
                BorderFactory.createEmptyBorder(9, 14, 9, 14));
    }

    // ── RoundedPanel helper ────────────────────────────────────────────────────
    public static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color bg;
        private Color shadowColor;
        private boolean hasShadow;

        public RoundedPanel(LayoutManager layout, int radius, Color bg) {
            super(layout);
            this.radius = radius;
            this.bg = bg;
            setOpaque(false);
        }

        public void setShadow(boolean b) { this.hasShadow = b; this.shadowColor = new Color(0, 0, 0, 18); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (hasShadow) {
                g2.setColor(shadowColor);
                g2.fill(new RoundRectangle2D.Float(2, 3, getWidth() - 4, getHeight() - 2, radius, radius));
            }
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ── Avatar circle ──────────────────────────────────────────────────────────
    public static JLabel makeAvatar(String initials, Color bg, int size) {
        JLabel lbl = new JLabel(initials, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        lbl.setPreferredSize(new Dimension(size, size));
        lbl.setMinimumSize(new Dimension(size, size));
        lbl.setMaximumSize(new Dimension(size, size));
        lbl.setFont(uiFont(Font.BOLD, size / 3));
        lbl.setForeground(Color.WHITE);
        lbl.setOpaque(false);
        return lbl;
    }

    // ── Button styles ──────────────────────────────────────────────────────────

    /** Nút chính – bo tròn, primary color */
    public static void stylePrimaryButton(JButton b) {
        b.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                AbstractButton btn = (AbstractButton) c;
                Color base = btn.getModel().isPressed() ? PRIMARY_DARK
                           : btn.getModel().isRollover() ? new Color(0x6B6EF8) : PRIMARY;
                g2.setColor(base);
                g2.fill(new RoundRectangle2D.Float(0, 0, c.getWidth(), c.getHeight(), 12, 12));
                g2.dispose();
                super.paint(g, c);
            }
        });
        b.setFont(uiFont(Font.BOLD, 14));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(11, 22, 11, 22));
    }

    /** Nút phụ – bo tròn, nền sáng */
    public static void styleSecondaryButton(JButton b) {
        b.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                AbstractButton btn = (AbstractButton) c;
                Color base = btn.getModel().isPressed() ? new Color(0xDDDEFE)
                           : btn.getModel().isRollover() ? PRIMARY_LIGHT : BG;
                g2.setColor(base);
                g2.fill(new RoundRectangle2D.Float(0, 0, c.getWidth(), c.getHeight(), 12, 12));
                g2.setColor(BORDER);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(new RoundRectangle2D.Float(0.6f, 0.6f, c.getWidth() - 1.2f, c.getHeight() - 1.2f, 12, 12));
                g2.dispose();
                super.paint(g, c);
            }
        });
        b.setFont(uiFont(Font.BOLD, 13));
        b.setForeground(TEXT);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
    }

    /** Nút gửi tròn (send button) */
    public static void styleSendButton(JButton b) {
        b.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                AbstractButton btn = (AbstractButton) c;
                Color base = btn.getModel().isPressed() ? PRIMARY_DARK
                           : btn.getModel().isRollover() ? new Color(0x6B6EF8) : PRIMARY;
                g2.setColor(base);
                g2.fillOval(0, 0, c.getWidth(), c.getHeight());
                g2.dispose();
                super.paint(g, c);
            }
        });
        b.setFont(uiFont(Font.BOLD, 16));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(42, 42));
        b.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    }

    /** Nút mini chat bar (emoji, file, sticker) */
    public static void styleChatMiniButton(JButton b) {
        b.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                AbstractButton btn = (AbstractButton) c;
                if (btn.getModel().isRollover() || btn.getModel().isPressed()) {
                    g2.setColor(PRIMARY_LIGHT);
                    g2.fill(new RoundRectangle2D.Float(0, 0, c.getWidth(), c.getHeight(), 9, 9));
                }
                g2.dispose();
                super.paint(g, c);
            }
        });
        b.setFont(emojiFont(20));   // Segoe UI Emoji
        b.setForeground(MUTED);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(38, 38));
        b.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    }

    /** Nút icon trong header phòng chat */
    public static void styleChatHeaderButton(JButton b) {
        b.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                AbstractButton btn = (AbstractButton) c;
                Color base = btn.getModel().isRollover() ? PRIMARY_LIGHT : BG;
                g2.setColor(base);
                g2.fill(new RoundRectangle2D.Float(0, 0, c.getWidth(), c.getHeight(), 9, 9));
                g2.setColor(BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, c.getWidth() - 1, c.getHeight() - 1, 9, 9));
                g2.dispose();
                super.paint(g, c);
            }
        });
        b.setFont(emojiFont(16));   // Segoe UI Emoji
        b.setForeground(MUTED);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(34, 34));
        b.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    }

    // ── Sidebar icon button ────────────────────────────────────────────────────
    public static void styleSidebarIcon(JButton b, boolean active) {
        b.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                AbstractButton btn = (AbstractButton) c;
                if (active) {
                    g2.setColor(new Color(0x3D3AAA));
                    g2.fill(new RoundRectangle2D.Float(0, 0, c.getWidth(), c.getHeight(), 11, 11));
                } else if (btn.getModel().isRollover()) {
                    g2.setColor(new Color(0x1E1B5E));
                    g2.fill(new RoundRectangle2D.Float(0, 0, c.getWidth(), c.getHeight(), 11, 11));
                }
                g2.dispose();
                super.paint(g, c);
            }
        });
        b.setFont(emojiFont(20));   // dùng Segoe UI Emoji để render icon đúng
        b.setForeground(active ? new Color(0xA5A7FF) : new Color(0xAAACCC));
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(44, 44));
        b.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    }

    // ── Slim scrollbar ─────────────────────────────────────────────────────────
    public static void applySlimScrollBar(JScrollPane sp) {
        sp.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                this.thumbColor = new Color(0xD0D3E8);
                this.trackColor = new Color(0, 0, 0, 0);
            }
            @Override protected JButton createDecreaseButton(int o) { return invisBtn(); }
            @Override protected JButton createIncreaseButton(int o) { return invisBtn(); }
            @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(thumbColor);
                g2.fillRoundRect(r.x + 2, r.y, r.width - 4, r.height, 4, 4);
                g2.dispose();
            }
            @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {}
            private JButton invisBtn() {
                JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); b.setVisible(false); return b;
            }
        });
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        sp.setBorder(BorderFactory.createEmptyBorder());
    }

    // ── Section label ──────────────────────────────────────────────────────────
    public static JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(uiFont(Font.BOLD, 10));
        l.setForeground(MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(8, 16, 4, 16));
        return l;
    }

    // ── Badge ──────────────────────────────────────────────────────────────────
    public static JLabel makeBadge(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(PRIMARY);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setFont(uiFont(Font.BOLD, 10));
        l.setForeground(Color.WHITE);
        l.setOpaque(false);
        l.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return l;
    }

    /** Accent bar (không còn dùng ở top, giữ compat) */
    public static JPanel accentBar() {
        JPanel p = new JPanel();
        p.setPreferredSize(new Dimension(0, 0));
        p.setBackground(PRIMARY);
        return p;
    }


    // ── Random avatar color by name ────────────────────────────────────────────
    private static final Color[] AVATAR_COLORS = {
        new Color(0x5B5EF4), new Color(0x8B5CF6), new Color(0x14B8A6),
        new Color(0xF59E0B), new Color(0xEF4444), new Color(0x0891B2),
        new Color(0x10B981), new Color(0x6366F1)
    };

    public static Color avatarColor(String name) {
        if (name == null || name.isEmpty()) return AVATAR_COLORS[0];
        return AVATAR_COLORS[Math.abs(name.hashCode()) % AVATAR_COLORS.length];
    }

    public static String initials(String name) {
        if (name == null || name.isEmpty()) return "?";
        return String.valueOf(name.charAt(0)).toUpperCase();
    }
}
