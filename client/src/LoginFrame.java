import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import model.Message;
import model.User;

import network.ClientSocketManager;
import util.AppTheme;
import util.InputValidation;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;

public class LoginFrame extends JFrame {

    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JButton loginButton;
    private final JButton registerButton;
    private final Gson gson;
    private volatile ClientSocketManager socketManager;
    private SwingWorker<Message, Void> loginWorker;
    private volatile boolean disposed;

    public LoginFrame() {
        super("Đăng nhập - Tra Cứu Thông Tin Địa Lý");
        gson = new Gson();
        usernameField  = AppTheme.styledField(22);
        passwordField  = AppTheme.styledPasswordField(22);
        loginButton    = AppTheme.primaryButton("Đăng nhập");
        registerButton = new JButton("Đăng ký ngay.");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        buildLayout();
        loginButton.addActionListener(e -> login());
        registerButton.addActionListener(e -> openRegisterFrame());
        usernameField.addActionListener(e -> login());
        passwordField.addActionListener(e -> login());
        pack();
        setLocationRelativeTo(null);
    }

    private void buildLayout() {
        // Nền gradient xanh nhạt
        JPanel bg = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                GradientPaint gp = new GradientPaint(0, 0, new Color(0xD5F5E3),
                        getWidth(), getHeight(), new Color(0xA9DFBF));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        bg.setPreferredSize(new Dimension(480, 560));

        // Card trung tâm
        JPanel card = AppTheme.cardPanel(new GridBagLayout());
        card.setPreferredSize(new Dimension(360, 430));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(32, 36, 28, 36)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.CENTER; gc.weightx = 1;

        // Tiêu đề
        gc.gridy = 0; gc.insets = new Insets(0, 0, 4, 0);
        JLabel title = new JLabel("ĐĂNG NHẬP / ĐĂNG KÝ", SwingConstants.CENTER);
        title.setFont(AppTheme.FONT_HEADER);
        title.setForeground(AppTheme.TEXT_PRIMARY);
        card.add(title, gc);

        gc.gridy = 1; gc.insets = new Insets(0, 0, 24, 0);
        JLabel sub = new JLabel("Tra Cứu Thông Tin Địa Lý", SwingConstants.CENTER);
        sub.setFont(AppTheme.FONT_SMALL);
        sub.setForeground(AppTheme.TEXT_SECONDARY);
        card.add(sub, gc);

        // Tài khoản
        gc.gridy = 2; gc.insets = new Insets(0, 0, 4, 0);
        JLabel userLbl = new JLabel("Tài khoản");
        userLbl.setFont(AppTheme.FONT_BOLD);
        userLbl.setForeground(AppTheme.TEXT_SECONDARY);
        card.add(userLbl, gc);

        gc.gridy = 3; gc.insets = new Insets(0, 0, 14, 0);
        usernameField.setPreferredSize(new Dimension(0, 40));
        card.add(usernameField, gc);

        // Mật khẩu
        gc.gridy = 4; gc.insets = new Insets(0, 0, 4, 0);
        JLabel pwdLbl = new JLabel("Mật khẩu");
        pwdLbl.setFont(AppTheme.FONT_BOLD);
        pwdLbl.setForeground(AppTheme.TEXT_SECONDARY);
        card.add(pwdLbl, gc);

        gc.gridy = 5; gc.insets = new Insets(0, 0, 6, 0);
        passwordField.setPreferredSize(new Dimension(0, 40));
        card.add(passwordField, gc);

        // Quên mật khẩu
        gc.gridy = 6; gc.insets = new Insets(0, 0, 22, 0);
        gc.anchor = GridBagConstraints.EAST;
        JLabel forgotLbl = new JLabel("Quên mật khẩu?");
        forgotLbl.setFont(AppTheme.FONT_SMALL);
        forgotLbl.setForeground(AppTheme.PRIMARY_DARK);
        forgotLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.add(forgotLbl, gc);
        gc.anchor = GridBagConstraints.CENTER;

        // Nút đăng nhập
        gc.gridy = 7; gc.insets = new Insets(0, 0, 14, 0);
        loginButton.setPreferredSize(new Dimension(0, 42));
        card.add(loginButton, gc);

        // Dòng đăng ký
        gc.gridy = 8; gc.insets = new Insets(0, 0, 0, 0);
        JPanel regRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        regRow.setOpaque(false);
        JLabel noAccLbl = new JLabel("Chưa có tài khoản?");
        noAccLbl.setFont(AppTheme.FONT_SMALL);
        noAccLbl.setForeground(AppTheme.TEXT_SECONDARY);
        registerButton.setFont(AppTheme.FONT_BOLD);
        registerButton.setForeground(AppTheme.PRIMARY_DARK);
        registerButton.setContentAreaFilled(false);
        registerButton.setBorderPainted(false);
        registerButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        registerButton.setFocusPainted(false);
        regRow.add(noAccLbl);
        regRow.add(registerButton);
        card.add(regRow, gc);

        bg.add(card);
        setContentPane(bg);
    }

    private void openRegisterFrame() { new RegisterFrame().setVisible(true); }

    private void login() {
        if (disposed || !loginButton.isEnabled()) return;
        final String username;
        final String password;
        char[] enteredPassword = passwordField.getPassword();
        try {
            username = InputValidation.username(usernameField.getText());
            password = InputValidation.password(new String(enteredPassword), "Mật khẩu");
        } catch (InputValidation.ValidationException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        } finally {
            Arrays.fill(enteredPassword, '\0');
        }
        setLoginEnabled(false);
        loginWorker = new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                if (socketManager == null) socketManager = new ClientSocketManager();
                if (disposed || isCancelled()) {
                    ClientSocketManager c = socketManager; socketManager = null;
                    if (c != null) c.close();
                    throw new IOException("Đã hủy đăng nhập.");
                }
                JsonObject d = new JsonObject();
                d.addProperty("username", username);
                d.addProperty("password", password);
                return socketManager.sendRequest(new Message("LOGIN", d));
            }
            @Override protected void done() {
                if (disposed || isCancelled()) return;
                try {
                    Message response = get();
                    if (response == null) throw new IOException("Máy chủ không trả lời.");
                    if ("ERROR".equals(response.getAction())) {
                        JOptionPane.showMessageDialog(LoginFrame.this,
                                "Máy chủ báo lỗi: " + response.getData(), "Đăng nhập thất bại", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    if (!"LOGIN_RESPONSE".equals(response.getAction()))
                        throw new IOException("Phản hồi đăng nhập của máy chủ không hợp lệ.");
                    if (hasData(response)) {
                        User user = gson.fromJson(gson.toJsonTree(response.getData()), User.class);
                        JOptionPane.showMessageDialog(LoginFrame.this, "Đăng nhập thành công!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                        new MainFrame(user, socketManager).setVisible(true);
                        socketManager = null;
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(LoginFrame.this, "Tên đăng nhập hoặc mật khẩu không đúng.", "Đăng nhập thất bại", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception exception) {
                    if (socketManager != null) { try { socketManager.close(); } catch (IOException ignored) { } socketManager = null; }
                    JOptionPane.showMessageDialog(LoginFrame.this, "Không đăng nhập được: " + util.VietnameseText.errorMessage(exception), "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                } finally {
                    loginWorker = null;
                    if (!disposed) setLoginEnabled(true);
                }
            }
        };
        loginWorker.execute();
    }

    private boolean hasData(Message response) {
        if (response.getData() == null) return false;
        return !gson.toJsonTree(response.getData()).isJsonNull();
    }

    private void setLoginEnabled(boolean enabled) {
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        loginButton.setEnabled(enabled);
        registerButton.setEnabled(enabled);
    }

    @Override public void dispose() {
        disposed = true;
        if (loginWorker != null) loginWorker.cancel(true);
        passwordField.setText("");
        if (socketManager != null) { try { socketManager.close(); } catch (IOException e) { e.printStackTrace(); } }
        super.dispose();
    }
}