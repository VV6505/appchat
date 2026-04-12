package chatmulti.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

public final class UiTheme {
    private UiTheme() {}

    public static final int IMAGE_PREVIEW_MAX_W = 260;
    public static final int IMAGE_PREVIEW_MAX_H = 300;

    /** Primary (Navy) — nút, accent bar */
    public static final Color PRIMARY = new Color(0x1D6FA4);
    public static final Color PRIMARY_DARK = new Color(0x165A85);
    /** Surface (Sky) — header phòng, nền tab */
    public static final Color SURFACE = new Color(0xE6F1FB);
    public static final Color WINDOW = new Color(0xFFFFFF);
    public static final Color PANEL = new Color(0xF4F6F8);
    public static final Color TEXT = new Color(0x1E1E1E);
    public static final Color MUTED = new Color(0x5F5E5A);
    public static final Color BORDER_BLUE = new Color(0xB5D4F4);
    public static final Color DIVIDER = new Color(0xD3D1C7);

    /** Online / running */
    public static final Color ONLINE_BG = new Color(0xD6EFE3);
    public static final Color ONLINE_TEXT = new Color(0x0A5C4A);
    /** Offline */
    public static final Color OFFLINE_BG = new Color(0xF1EFE8);
    public static final Color OFFLINE_TEXT = new Color(0x5F5E5A);

    /** Log console */
    public static final Color LOG_BG = new Color(0x1A1A2E);
    public static final Color LOG_TEXT = new Color(0x5DCAA5);

    /** Chat bubbles */
    public static final Color BUBBLE_ME = new Color(0x1D6FA4);
    public static final Color BUBBLE_ME_TEXT = Color.WHITE;
    public static final Color BUBBLE_OTHER = new Color(0xEEF2F7);
    public static final Color BUBBLE_OTHER_TEXT = new Color(0x1E1E1E);
    public static final Color BUBBLE_SYSTEM_BG = new Color(0xFFF8E1);
    public static final Color BUBBLE_SYSTEM_TEXT = new Color(0xB45309);

    /** Chờ phòng */
    public static final Color WAIT_BG = new Color(0xD6EFE3);
    public static final Color WAIT_ACCENT = new Color(0x006B5D);

    public static final Color LIST_SELECTION = new Color(0xC8E0F5);
    public static final Color BUTTON_SECONDARY_BG = new Color(0xF1EFE8);

    /** Tương thích tên cũ */
    public static final Color WINDOW_BG = PANEL;
    public static final Color CARD = WINDOW;
    public static final Color CHAT_BG = WINDOW;
    public static final Color BORDER = BORDER_BLUE;
    public static final Color TEXT_MUTED = MUTED;
    public static final Color ACCENT = PRIMARY;
    public static final Color ACCENT_DARK = PRIMARY_DARK;
    public static final Color SUCCESS_BG = ONLINE_BG;
    public static final Color SUCCESS_TEXT = ONLINE_TEXT;

    public static Font uiFont(int style, int size) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    public static void stylePrimaryButton(JButton b) {
        b.setUI(new BasicButtonUI());
        b.setFont(uiFont(Font.BOLD, 13));
        b.setBackground(PRIMARY);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(true);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PRIMARY_DARK, 1),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
    }

    public static void styleSecondaryButton(JButton b) {
        b.setUI(new BasicButtonUI());
        b.setFont(uiFont(Font.BOLD, 13));
        b.setBackground(BUTTON_SECONDARY_BG);
        b.setForeground(TEXT);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(true);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DIVIDER, 1),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
    }

    public static Border fieldBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_BLUE, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10));
    }

    public static JPanel accentBar() {
        JPanel p = new JPanel();
        p.setPreferredSize(new Dimension(0, 4));
        p.setBackground(PRIMARY);
        return p;
    }

    /** Nút vuông nhỏ trên thanh chat (emoji / menu ⋯). */
    public static void styleChatMiniButton(JButton b) {
        b.setUI(new BasicButtonUI());
        b.setFont(uiFont(Font.PLAIN, 18));
        b.setBackground(WINDOW);
        b.setForeground(TEXT);
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(true);
        b.setPreferredSize(new Dimension(40, 38));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_BLUE, 1, true),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
    }
}
