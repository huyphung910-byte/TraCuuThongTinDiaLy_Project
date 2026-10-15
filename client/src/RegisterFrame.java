import com.google.gson.JsonObject;
import model.Message;
import network.ClientSocketManager;
import util.AppTheme;
import util.InputValidation;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;

public class RegisterFrame extends JFrame {

    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JPasswordField confirmPasswordField;
    private final JTextField fullnameField;
    private final JTextField emailField;
    private final JButton registerButton;
    private final JButton cancelButton;
    private volatile ClientSocketManager socketManager;
    private SwingWorker<Message, Void> registerWorker;
    private volatile boolean disposed;

    public RegisterFrame() {
        super("Dang ky tai khoan");
        usernameField       = AppTheme.styledField(22);
        passwordField       = AppTheme.styledPasswordField(22);
        confirmPasswordField = AppTheme.styledPasswordField(22);
        fullnameField       = AppTheme.styledField(22);
        emailField          = AppTheme.styledField(22);
        registerButton      = AppTheme.primaryButton("Dang ky");
        cancelButton        = AppTheme.outlineButton("Huy");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        buildLayout();
        registerButton.addActionListener(e -> register());
        cancelButton.addActionListener(e -> dispose());
        confirmPasswordField.addActionListener(e -> register());
        emailField.addActionListener(e -> register());
        pack();
        setLocationRelativeTo(null);
    }

    private void buildLayout() {
        JPanel bg = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                GradientPaint gp = new GradientPaint(0, 0, new Color(0xD5F5E3), getWidth(), getHeight(), new Color(0xA9DFBF));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        bg.setPreferredSize(new Dimension(500, 620));

        JPanel card = AppTheme.cardPanel(new GridBagLayout());
        card.setPreferredSize(new Dimension(400, 530));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppTheme.BORDER_COLOR, 1, true),
                BorderFactory.createEmptyBorder(28, 36, 24, 36)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1;

        gc.gridy = 0; gc.insets = new Insets(0, 0, 4, 0);
        JLabel title = new JLabel("DANG KY TAI KHOAN", SwingConstants.CENTER);
        title.setFont(AppTheme.FONT_HEADER);
        title.setForeground(AppTheme.TEXT_PRIMARY);
        card.add(title, gc);

        gc.gridy = 1; gc.insets = new Insets(0, 0, 20, 0);
        JLabel sub = new JLabel("Tao tai khoan de su dung dich vu", SwingConstants.CENTER);
        sub.setFont(AppTheme.FONT_SMALL);
        sub.setForeground(AppTheme.TEXT_SECONDARY);
        card.add(sub, gc);

        addLabelAndField(card, gc, 2, "Ten dang nhap", usernameField);
        addLabelAndField(card, gc, 4, "Mat khau", passwordField);
        addLabelAndField(card, gc, 6, "Xac nhan mat khau", confirmPasswordField);
        addLabelAndField(card, gc, 8, "Ho va ten", fullnameField);
        addLabelAndField(card, gc, 10, "Dia chi email", emailField);

        gc.gridy = 12; gc.insets = new Insets(20, 0, 0, 0);
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 10, 0));
        btnRow.setOpaque(false);
        cancelButton.setPreferredSize(new Dimension(0, 40));
        registerButton.setPreferredSize(new Dimension(0, 40));
        btnRow.add(cancelButton);
        btnRow.add(registerButton);
        card.add(btnRow, gc);

        bg.add(card);
        setContentPane(bg);
    }

    private static void addLabelAndField(JPanel card, GridBagConstraints gc, int row, String labelText, JComponent field) {
        gc.gridy = row; gc.insets = new Insets(row == 2 ? 0 : 10, 0, 4, 0);
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(AppTheme.FONT_BOLD);
        lbl.setForeground(AppTheme.TEXT_SECONDARY);
        card.add(lbl, gc);
        gc.gridy = row + 1; gc.insets = new Insets(0, 0, 0, 0);
        if (field instanceof JTextField) ((JTextField)field).setPreferredSize(new Dimension(0, 38));
        card.add(field, gc);
    }

    private void register() {
        if (disposed || !registerButton.isEnabled()) return;
        final String username, password, confirmPassword, fullname, email;
        char[] enteredPassword = passwordField.getPassword();
        char[] enteredConfirmation = confirmPasswordField.getPassword();
        try {
            username        = InputValidation.username(usernameField.getText());
            password        = InputValidation.password(new String(enteredPassword), "Mat khau");
            confirmPassword = new String(enteredConfirmation);
            fullname        = InputValidation.fullname(fullnameField.getText());
            email           = InputValidation.email(emailField.getText());
        } catch (InputValidation.ValidationException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Loi", JOptionPane.WARNING_MESSAGE);
            return;
        } finally {
            Arrays.fill(enteredPassword, '\0');
            Arrays.fill(enteredConfirmation, '\0');
        }
        if (!password.equals(confirmPassword)) {
            JOptionPane.showMessageDialog(this, "Mat khau xac nhan khong khop.", "Loi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        setFormEnabled(false);
        registerWorker = new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                if (socketManager == null) socketManager = new ClientSocketManager();
                if (disposed || isCancelled()) {
                    ClientSocketManager c = socketManager; socketManager = null;
                    if (c != null) c.close();
                    throw new IOException("Da huy dang ky.");
                }
                JsonObject data = new JsonObject();
                data.addProperty("username", username);
                data.addProperty("password", password);
                data.addProperty("fullname", fullname);
                data.addProperty("email", email);
                return socketManager.sendRequest(new Message("REGISTER", data));
            }
            @Override protected void done() {
                if (disposed || isCancelled()) return;
                try {
                    Message response = get();
                    if (response == null) throw new IOException("May chu khong tra loi.");
                    if ("ERROR".equals(response.getAction())) {
                        JOptionPane.showMessageDialog(RegisterFrame.this, "May chu bao loi: " + response.getData(), "Dang ky that bai", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    if (!"REGISTER_RESPONSE".equals(response.getAction()) || !(response.getData() instanceof Boolean))
                        throw new IOException("Phan hoi dang ky khong hop le.");
                    if ((Boolean) response.getData()) {
                        JOptionPane.showMessageDialog(RegisterFrame.this, "Dang ky thanh cong! Hay dang nhap.", "Thong bao", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(RegisterFrame.this, "Dang ky that bai. Ten dang nhap hoac email co the da ton tai.", "Loi", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception exception) {
                    if (socketManager != null) { try { socketManager.close(); } catch (IOException ignored) { } socketManager = null; }
                    JOptionPane.showMessageDialog(RegisterFrame.this, "Khong dang ky duoc: " + util.VietnameseText.errorMessage(exception), "Loi ket noi", JOptionPane.ERROR_MESSAGE);
                } finally {
                    registerWorker = null;
                    if (!disposed) setFormEnabled(true);
                }
            }
        };
        registerWorker.execute();
    }

    private void setFormEnabled(boolean enabled) {
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        confirmPasswordField.setEnabled(enabled);
        fullnameField.setEnabled(enabled);
        emailField.setEnabled(enabled);
        registerButton.setEnabled(enabled);
    }

    @Override public void dispose() {
        disposed = true;
        if (registerWorker != null) registerWorker.cancel(true);
        passwordField.setText("");
        confirmPasswordField.setText("");
        if (socketManager != null) { try { socketManager.close(); } catch (IOException e) { e.printStackTrace(); } }
        super.dispose();
    }
}