import com.google.gson.JsonObject;
import model.Message;
import network.ClientSocketManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class RegisterFrame extends JFrame {
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JPasswordField confirmPasswordField;
    private final JTextField fullnameField;
    private final JTextField emailField;
    private final JButton registerButton;
    private final JButton cancelButton;
    private ClientSocketManager socketManager;

    public RegisterFrame() {
        super("Đăng ký tài khoản");
        this.usernameField = new JTextField(20);
        this.passwordField = new JPasswordField(20);
        this.confirmPasswordField = new JPasswordField(20);
        this.fullnameField = new JTextField(20);
        this.emailField = new JTextField(20);
        this.registerButton = new JButton("Đăng ký");
        this.cancelButton = new JButton("Hủy");

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        buildLayout();
        registerButton.addActionListener(event -> register());
        cancelButton.addActionListener(event -> dispose());
        pack();
        setLocationRelativeTo(null);
    }

    private void buildLayout() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 12, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Tên đăng nhập:"), gbc);
        gbc.gridx = 1;
        formPanel.add(usernameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Mật khẩu:"), gbc);
        gbc.gridx = 1;
        formPanel.add(passwordField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Xác nhận mật khẩu:"), gbc);
        gbc.gridx = 1;
        formPanel.add(confirmPasswordField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Họ tên:"), gbc);
        gbc.gridx = 1;
        formPanel.add(fullnameField, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        formPanel.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1;
        formPanel.add(emailField, gbc);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(registerButton);
        buttonPanel.add(cancelButton);

        setLayout(new BorderLayout());
        add(formPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void register() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());
        String fullname = fullnameField.getText().trim();
        String email = emailField.getText().trim();

        if (username.isEmpty() || password.isEmpty() || fullname.isEmpty() || email.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ thông tin.", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!password.equals(confirmPassword)) {
            JOptionPane.showMessageDialog(this, "Mật khẩu xác nhận không khớp.", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setFormEnabled(false);
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                if (socketManager == null) {
                    socketManager = new ClientSocketManager();
                }
                JsonObject data = new JsonObject();
                data.addProperty("username", username);
                data.addProperty("password", password);
                data.addProperty("fullname", fullname);
                data.addProperty("email", email);
                return socketManager.sendRequest(new Message("REGISTER", data));
            }

            @Override
            protected void done() {
                try {
                    Message response = get();
                    if (response != null && "REGISTER_RESPONSE".equals(response.getAction()) && (Boolean)response.getData()) {
                        JOptionPane.showMessageDialog(RegisterFrame.this, "Đăng ký thành công! Hãy đăng nhập.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(RegisterFrame.this, "Đăng ký thất bại. Tên đăng nhập/email có thể đã tồn tại.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(RegisterFrame.this, "Không thể kết nối đến Server.", "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setFormEnabled(true);
                }
            }
        }.execute();
    }

    private void setFormEnabled(boolean enabled) {
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        confirmPasswordField.setEnabled(enabled);
        fullnameField.setEnabled(enabled);
        emailField.setEnabled(enabled);
        registerButton.setEnabled(enabled);
    }

    @Override
    public void dispose() {
        if (socketManager != null) {
            try {
                socketManager.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.dispose();
    }
}
