import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import model.Message;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;

public class LoginFrame extends JFrame {
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JButton loginButton;
    private final Gson gson;
    private ClientSocketManager socketManager;

    public LoginFrame() {
        super("Đăng nhập");
        this.gson = new Gson();
        this.usernameField = new JTextField(20);
        this.passwordField = new JPasswordField(20);
        this.loginButton = new JButton("Đăng nhập");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        buildLayout();
        loginButton.addActionListener(event -> login());
        pack();
        setLocationRelativeTo(null);
    }

    private void buildLayout() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 12, 20));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 6, 6, 6);
        constraints.anchor = GridBagConstraints.WEST;

        constraints.gridx = 0;
        constraints.gridy = 0;
        formPanel.add(new JLabel("Username:"), constraints);
        constraints.gridx = 1;
        formPanel.add(usernameField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        formPanel.add(new JLabel("Password:"), constraints);
        constraints.gridx = 1;
        formPanel.add(passwordField, constraints);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(loginButton);

        setLayout(new BorderLayout());
        add(formPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void login() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ thông tin.", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setLoginEnabled(false);
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                if (socketManager == null) {
                    socketManager = new ClientSocketManager();
                }
                JsonObject loginData = new JsonObject();
                loginData.addProperty("username", username);
                loginData.addProperty("password", password);
                return socketManager.sendRequest(new Message("LOGIN", loginData));
            }

            @Override
            protected void done() {
                try {
                    Message response = get();
                    if (response != null && "LOGIN_RESPONSE".equals(response.getAction()) && hasData(response)) {
                        JOptionPane.showMessageDialog(LoginFrame.this, "Đăng nhập thành công!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(LoginFrame.this, "Tên đăng nhập hoặc mật khẩu không đúng.", "Đăng nhập thất bại", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(LoginFrame.this, "Không thể kết nối đến Server: " + exception.getMessage(), "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setLoginEnabled(true);
                }
            }
        }.execute();
    }

    private boolean hasData(Message response) {
        if (response.getData() == null) {
            return false;
        }
        JsonElement dataElement = gson.toJsonTree(response.getData());
        return !dataElement.isJsonNull();
    }

    private void setLoginEnabled(boolean enabled) {
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        loginButton.setEnabled(enabled);
    }

    @Override
    public void dispose() {
        if (socketManager != null) {
            try {
                socketManager.close();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
        super.dispose();
    }
}