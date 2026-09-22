import com.formdev.flatlaf.FlatLightLaf;

public class ClientMain {
    private static LoginFrame loginFrame;

    public static void main(String[] args) {
        FlatLightLaf.setup();
        System.out.println("Client started. Opening login window...");
        loginFrame = new LoginFrame();
        loginFrame.setVisible(true);
    }
}