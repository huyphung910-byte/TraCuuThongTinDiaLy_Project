package panel;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import model.Message;
import model.User;
import network.ClientSocketManager;
import util.AppTheme;
import util.VietnameseText;
import util.InputValidation;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/** Quản lý thông tin của tài khoản đang đăng nhập. */
public final class AccountPanel extends JPanel {
    @FunctionalInterface interface RequestSender { Message send(Message request) throws Exception; }
    @FunctionalInterface interface DialogHandler { void show(Component parent, String message, String title, int type); }
    @FunctionalInterface private interface ResponseHandler { void accept(Message response) throws Exception; }

    private final User currentUser;
    private final RequestSender requests;
    private final Runnable onProfileUpdated;
    private final Runnable onLogout;
    private final DialogHandler dialogs;
    private final Gson gson = new Gson();
    final JTextField usernameField = new JTextField(26);
    final JTextField fullnameField = new JTextField(26);
    final JTextField emailField = new JTextField(26);
    final JPasswordField currentPasswordField = new JPasswordField(26);
    final JPasswordField newPasswordField = new JPasswordField(26);
    final JPasswordField confirmPasswordField = new JPasswordField(26);
    final JButton saveButton = new JButton("Lưu thay đổi");
    final JButton restoreButton = new JButton("Khôi phục");
    final JButton reloadButton = new JButton("Tải lại thông tin");
    final JButton changePasswordButton = new JButton("Đổi mật khẩu");
    final JButton logoutButton = new JButton("Đăng xuất");
    private final JTextArea status = new JTextArea(2, 50);
    private boolean busy;
    private boolean disposed;
    private SwingWorker<Message, Void> worker;

    public AccountPanel(User user, ClientSocketManager manager, Runnable onProfileUpdated, Runnable onLogout) {
        this(user, request -> {
            if (manager == null) throw new IOException("Chưa kết nối tới máy chủ.");
            return manager.sendRequest(request);
        }, onProfileUpdated, onLogout, (parent, message, title, type) ->
                JOptionPane.showMessageDialog(parent, message, title, type));
    }

    AccountPanel(User user, RequestSender requests, Runnable onProfileUpdated, Runnable onLogout, DialogHandler dialogs) {
        this.currentUser = Objects.requireNonNull(user, "Thiếu tài khoản đang đăng nhập.");
        this.requests = Objects.requireNonNull(requests);
        this.onProfileUpdated = onProfileUpdated == null ? () -> { } : onProfileUpdated;
        this.onLogout = onLogout == null ? () -> { } : onLogout;
        this.dialogs = Objects.requireNonNull(dialogs);
        buildLayout();
        fillProfileFields();
        saveButton.addActionListener(e -> saveProfile());
        restoreButton.addActionListener(e -> { if (!busy) { fillProfileFields(); status.setText("Đã khôi phục thông tin hiện tại."); } });
        reloadButton.addActionListener(e -> reloadProfile());
        changePasswordButton.addActionListener(e -> changePassword());
        logoutButton.addActionListener(e -> logout());
    }

    private void buildLayout() {
        setLayout(new BorderLayout(12, 12));
        setBackground(AppTheme.BG_MAIN);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // Header
        add(AppTheme.headerPanel("Ho so cua ban - Tai khoan"), BorderLayout.NORTH);

        JPanel forms = new JPanel(new GridBagLayout());
        forms.setBackground(AppTheme.BG_MAIN);
        forms.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        GridBagConstraints outer = new GridBagConstraints();
        outer.gridx = 0; outer.gridy = 0;
        outer.weightx = 1; outer.fill = GridBagConstraints.HORIZONTAL;
        outer.anchor = GridBagConstraints.NORTHWEST;
        outer.insets = new Insets(0, 0, 14, 0);

        JPanel profile = form("Thong tin tai khoan");
        usernameField.setEditable(false);
        usernameField.setBackground(AppTheme.PRIMARY_LIGHT);
        addField(profile, 0, "Ten dang nhap:", usernameField);
        addField(profile, 1, "Ho va ten:", fullnameField);
        addField(profile, 2, "Dia chi email:", emailField);
        addActions(profile, 3, saveButton, restoreButton, reloadButton);
        forms.add(profile, outer);

        JPanel password = form("Doi mat khau");
        addField(password, 0, "Mat khau hien tai:", currentPasswordField);
        addField(password, 1, "Mat khau moi:", newPasswordField);
        addField(password, 2, "Xac nhan mat khau moi:", confirmPasswordField);
        GridBagConstraints note = constraints(0, 3);
        note.gridwidth = 2;
        JLabel noteLbl = new JLabel("Mat khau moi co tu 8 den 128 ky tu va khac mat khau hien tai.");
        noteLbl.setFont(AppTheme.FONT_SMALL);
        noteLbl.setForeground(AppTheme.TEXT_SECONDARY);
        password.add(noteLbl, note);
        addActions(password, 4, changePasswordButton);
        outer.gridy = 1;
        forms.add(password, outer);
        outer.gridy = 2; outer.weighty = 1; outer.fill = GridBagConstraints.BOTH;
        forms.add(new JPanel(), outer);
        add(AppTheme.neatScrollPane(forms), BorderLayout.CENTER);

        status.setEditable(false);
        status.setFont(AppTheme.FONT_SMALL);
        status.setForeground(AppTheme.TEXT_SECONDARY);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setOpaque(false);
        status.setText("Ban co the cap nhat thong tin tai khoan hoac doi mat khau tai day.");
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBackground(AppTheme.BG_HEADER);
        footer.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        status.setForeground(AppTheme.TEXT_WHITE);
        footer.add(status, BorderLayout.CENTER);
        logoutButton.setFont(AppTheme.FONT_BOLD);
        logoutButton.setBackground(AppTheme.DANGER);
        logoutButton.setForeground(AppTheme.TEXT_WHITE);
        logoutButton.setFocusPainted(false);
        logoutButton.setBorderPainted(false);
        logoutButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        footer.add(logoutButton, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }

    private static JPanel form(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    private static GridBagConstraints constraints(int column, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = column; gbc.gridy = row;
        gbc.insets = new Insets(7, 10, 7, 10);
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    private static void addField(JPanel panel, int row, String label, JComponent field) {
        panel.add(new JLabel(label), constraints(0, row));
        GridBagConstraints gbc = constraints(1, row);
        gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, gbc);
    }

    private static void addActions(JPanel panel, int row, JButton... buttons) {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        for (JButton button : buttons) actions.add(button);
        GridBagConstraints gbc = constraints(0, row);
        gbc.gridwidth = 2;
        panel.add(actions, gbc);
    }

    private void fillProfileFields() {
        usernameField.setText(value(currentUser.getUsername()));
        fullnameField.setText(value(currentUser.getFullname()));
        emailField.setText(value(currentUser.getEmail()));
    }

    void saveProfile() {
        if (busy || disposed) return;
        final String fullname, email;
        try {
            fullname = InputValidation.fullname(fullnameField.getText());
            email = InputValidation.email(emailField.getText());
        } catch (InputValidation.ValidationException exception) {
            showMessage(exception.getMessage(), JOptionPane.WARNING_MESSAGE);
            return;
        }
        JsonObject data = identity();
        data.addProperty("fullname", fullname);
        data.addProperty("email", email);
        execute("UPDATE_PROFILE", data, "UPDATE_PROFILE_RESPONSE", "Đang lưu thông tin tài khoản...", response -> {
            requireSuccess(response, "Không lưu được thông tin tài khoản. Hãy kiểm tra địa chỉ email và thử lại.");
            currentUser.setFullname(fullname);
            currentUser.setEmail(email);
            fillProfileFields();
            onProfileUpdated.run();
            showMessage("Đã cập nhật thông tin tài khoản.", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    void reloadProfile() {
        if (busy || disposed) return;
        execute("GET_PROFILE", identity(), "GET_PROFILE_RESPONSE", "Đang tải thông tin tài khoản...", response -> {
            User refreshed = gson.fromJson(gson.toJsonTree(response.getData()), User.class);
            if (refreshed == null || refreshed.getId() != currentUser.getId()
                    || refreshed.getUsername() == null || refreshed.getUsername().isBlank()) {
                throw new IOException("Máy chủ trả về thông tin tài khoản không hợp lệ.");
            }
            currentUser.setUsername(refreshed.getUsername());
            currentUser.setFullname(refreshed.getFullname());
            currentUser.setEmail(refreshed.getEmail());
            fillProfileFields();
            onProfileUpdated.run();
            status.setText("Đã tải lại thông tin tài khoản.");
        });
    }

    void changePassword() {
        if (busy || disposed) return;
        char[] current = currentPasswordField.getPassword(), next = newPasswordField.getPassword();
        char[] confirmation = confirmPasswordField.getPassword();
        try {
            String currentText = InputValidation.password(new String(current), "Mật khẩu hiện tại");
            String nextText = InputValidation.password(new String(next), "Mật khẩu mới");
            if (Arrays.equals(current, next)) {
                showMessage("Mật khẩu mới phải khác mật khẩu hiện tại.", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!Arrays.equals(next, confirmation)) {
                showMessage("Mật khẩu xác nhận không khớp với mật khẩu mới.", JOptionPane.WARNING_MESSAGE);
                return;
            }
            JsonObject data = identity();
            data.addProperty("currentPassword", currentText);
            data.addProperty("newPassword", nextText);
            execute("CHANGE_PASSWORD", data, "CHANGE_PASSWORD_RESPONSE", "Đang đổi mật khẩu...", response -> {
                requireSuccess(response, "Không đổi được mật khẩu. Hãy kiểm tra mật khẩu hiện tại và thử lại.");
                clearPasswords();
                showMessage("Đã đổi mật khẩu thành công.", JOptionPane.INFORMATION_MESSAGE);
            });
        } catch (InputValidation.ValidationException exception) {
            showMessage(exception.getMessage(), JOptionPane.WARNING_MESSAGE);
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(next, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }

    void logout() {
        if (busy || disposed) return;
        clearPasswords();
        setBusy(true);
        status.setText("Đang đăng xuất...");
        try { onLogout.run(); }
        catch (RuntimeException e) {
            setBusy(false);
            showMessage("Không đăng xuất được: " + VietnameseText.errorMessage(e), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void execute(String action, JsonObject data, String expected, String progress, ResponseHandler completion) {
        setBusy(true);
        status.setText(progress);
        worker = new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try { return requests.send(new Message(action, data)); }
                finally {
                    data.remove("currentPassword");
                    data.remove("newPassword");
                }
            }
            @Override protected void done() {
                if (disposed || isCancelled()) return;
                try {
                    Message response = get();
                    requireResponse(response, expected);
                    completion.accept(response);
                } catch (Exception e) {
                    showMessage(VietnameseText.errorMessage(e), JOptionPane.ERROR_MESSAGE);
                } finally { worker = null; setBusy(false); }
            }
        };
        worker.execute();
    }

    private JsonObject identity() {
        return new JsonObject();
    }

    private void requireSuccess(Message response, String failure) throws IOException {
        JsonElement data = gson.toJsonTree(response.getData());
        if (!data.isJsonPrimitive() || !data.getAsJsonPrimitive().isBoolean()) {
            throw new IOException("Máy chủ trả về kết quả không hợp lệ.");
        }
        if (!data.getAsBoolean()) throw new IOException(failure);
    }

    private static void requireResponse(Message response, String expected) throws IOException {
        if (response == null) throw new IOException("Máy chủ không trả lời.");
        if ("ERROR".equals(response.getAction())) {
            throw new IOException("Máy chủ báo lỗi: " + String.valueOf(response.getData()));
        }
        if (!expected.equals(response.getAction())) throw new IOException("Phản hồi của máy chủ không hợp lệ.");
    }

    private void setBusy(boolean value) {
        busy = value;
        for (JComponent component : new JComponent[]{usernameField, fullnameField, emailField, currentPasswordField,
                newPasswordField, confirmPasswordField, saveButton, restoreButton, reloadButton, changePasswordButton, logoutButton}) {
            component.setEnabled(!value);
        }
    }

    private void showMessage(String message, int type) {
        status.setText(message);
        dialogs.show(this, message, type == JOptionPane.INFORMATION_MESSAGE ? "Thông báo" : "Thông tin tài khoản", type);
    }

    private void clearPasswords() {
        currentPasswordField.setText("");
        newPasswordField.setText("");
        confirmPasswordField.setText("");
    }

    @Override public void removeNotify() {
        disposed = true;
        if (worker != null) worker.cancel(true);
        clearPasswords();
        super.removeNotify();
    }

    private static String value(String text) { return text == null ? "" : text; }
}
