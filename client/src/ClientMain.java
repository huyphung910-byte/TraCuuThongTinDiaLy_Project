import com.formdev.flatlaf.FlatLightLaf;
import util.VietnameseText;
import util.ConsoleEncoding;
import util.AppTheme;
import javax.swing.*;
import java.util.Locale;

public class ClientMain {
    private static LoginFrame loginFrame;

    public static void main(String[] args) {
        ConsoleEncoding.configureUtf8();
        Locale.setDefault(VietnameseText.LOCALE);
        JComponent.setDefaultLocale(VietnameseText.LOCALE);
        FlatLightLaf.setup();
        AppTheme.applyGlobalDefaults();
        configureVietnameseUI();
        System.out.println("Máy khách đã khởi động. Đang mở cửa sổ đăng nhập...");
        SwingUtilities.invokeLater(() -> {
            loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }

    static void configureVietnameseUI() {
        Locale.setDefault(VietnameseText.LOCALE);
        JComponent.setDefaultLocale(VietnameseText.LOCALE);
        String[][] labels = {
                {"OptionPane.yesButtonText", "Có"}, {"OptionPane.noButtonText", "Không"},
                {"OptionPane.cancelButtonText", "Hủy"}, {"OptionPane.okButtonText", "Đồng ý"},
                {"OptionPane.messageDialogTitle", "Thông báo"}, {"OptionPane.inputDialogTitle", "Nhập thông tin"},
                {"FileChooser.openButtonText", "Mở"}, {"FileChooser.saveButtonText", "Lưu"},
                {"FileChooser.cancelButtonText", "Hủy"}, {"FileChooser.updateButtonText", "Cập nhật"},
                {"FileChooser.helpButtonText", "Trợ giúp"}, {"FileChooser.lookInLabelText", "Tìm trong:"},
                {"FileChooser.saveInLabelText", "Lưu trong:"}, {"FileChooser.fileNameLabelText", "Tên tệp:"},
                {"FileChooser.filesOfTypeLabelText", "Loại tệp:"}, {"FileChooser.openDialogTitleText", "Mở tệp"},
                {"FileChooser.saveDialogTitleText", "Lưu tệp"}, {"FileChooser.allFilesFilterText", "Tất cả tệp"},
                {"FileChooser.upFolderToolTipText", "Thư mục cha"}, {"FileChooser.newFolderToolTipText", "Tạo thư mục"},
                {"FileChooser.homeFolderToolTipText", "Thư mục cá nhân"}, {"FileChooser.listViewButtonToolTipText", "Danh sách"},
                {"FileChooser.detailsViewButtonToolTipText", "Chi tiết"}, {"FileChooser.acceptAllFileFilterText", "Tất cả tệp"},
                {"ColorChooser.okText", "Đồng ý"}, {"ColorChooser.cancelText", "Hủy"},
                {"ColorChooser.resetText", "Đặt lại"}
        };
        for (String[] label : labels) UIManager.put(label[0], label[1]);
    }
}
