package panel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import model.Country;
import model.Message;
import model.User;
import network.ClientSocketManager;

import javax.swing.*;
import java.awt.*;

public class CountrySearchPanel extends JPanel {
    private final User currentUser;
    private final ClientSocketManager socketManager;
    private final Gson gson;
    private JTextField searchField;
    private JButton searchButton;
    private JTextArea resultArea;

    public CountrySearchPanel(User user, ClientSocketManager socketManager) {
        this.currentUser = user;
        this.socketManager = socketManager;
        this.gson = new Gson();
        buildLayout();
    }

    private void buildLayout() {
        setLayout(new BorderLayout());

        // Top search bar
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchField = new JTextField(25);
        searchButton = new JButton("Tra cứu quốc gia");
        topPanel.add(new JLabel("Tên quốc gia: "));
        topPanel.add(searchField);
        topPanel.add(searchButton);
        add(topPanel, BorderLayout.NORTH);

        // Center result area
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        resultArea.setMargin(new Insets(10, 10, 10, 10));
        add(new JScrollPane(resultArea), BorderLayout.CENTER);

        searchButton.addActionListener(e -> search());
    }

    private void search() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) return;

        resultArea.setText("Đang tìm kiếm thông tin quốc gia...");
        saveHistory(keyword);

        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("countryName", keyword);
                return socketManager.sendRequest(new Message("SEARCH_COUNTRY", data));
            }

            @Override
            protected void done() {
                try {
                    Message response = get();
                    if (response != null && "SEARCH_COUNTRY_RESPONSE".equals(response.getAction())) {
                        if (response.getData() != null && !gson.toJsonTree(response.getData()).isJsonNull()) {
                            Country country = gson.fromJson(gson.toJsonTree(response.getData()), Country.class);
                            displayCountryInfo(country);
                        } else {
                            resultArea.setText("Không tìm thấy thông tin cho quốc gia: " + keyword);
                        }
                    }
                } catch (Exception e) {
                    resultArea.setText("Lỗi khi lấy thông tin quốc gia.");
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    private void saveHistory(String keyword) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                JsonObject data = new JsonObject();
                data.addProperty("userId", currentUser.getId());
                data.addProperty("keyword", keyword + " (Quốc gia)");
                socketManager.sendRequest(new Message("SAVE_HISTORY", data));
                return null;
            }
        }.execute();
    }

    private void displayCountryInfo(Country country) {
        StringBuilder sb = new StringBuilder();
        sb.append("===== THÔNG TIN QUỐC GIA =====\n\n");
        sb.append(String.format("Tên quốc gia: %s (%s)\n", country.getTenQuocGia(), country.getMaQuocGia()));
        sb.append(String.format("Thủ đô      : %s\n", country.getThuDo() != null && !country.getThuDo().isEmpty() ? country.getThuDo() : "Không rõ"));
        sb.append(String.format("Tiền tệ     : %s\n", country.getDonViTienTe() != null && !country.getDonViTienTe().isEmpty() ? country.getDonViTienTe() : "Không rõ"));
        sb.append(String.format("Ngôn ngữ    : %s\n", country.getNgonNgu() != null && !country.getNgonNgu().isEmpty() ? country.getNgonNgu() : "Không rõ"));
        sb.append(String.format("Biên giới   : %s\n", country.getQuocGiaLienKe() != null && !country.getQuocGiaLienKe().isEmpty() ? country.getQuocGiaLienKe() : "Không có (Đảo quốc)"));
        
        if (country.getDiemDuLichNoiBat() != null && !country.getDiemDuLichNoiBat().isEmpty()) {
            sb.append(String.format("\nĐiểm du lịch: %s\n", country.getDiemDuLichNoiBat()));
        }
        
        if (country.getUrlQuocKy() != null && !country.getUrlQuocKy().isEmpty()) {
            sb.append(String.format("\nQuốc kỳ (URL): %s\n", country.getUrlQuocKy()));
        }

        resultArea.setText(sb.toString());
        resultArea.setCaretPosition(0);
    }
}
