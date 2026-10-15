package panel;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import model.Location;
import model.Message;
import network.ClientSocketManager;
import util.InputValidation;
import util.VietnameseText;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class TripPlannerPanel extends JPanel {
    private final ClientSocketManager socket;
    private final Gson gson = new Gson();
    private final JTextField destination = new JTextField(20);
    private final JSpinner days = new JSpinner(new SpinnerNumberModel(2, 1, 7, 1));
    private final JComboBox<Location> cities = new JComboBox<>();
    private final JButton search = new JButton("Tìm địa điểm"), plan = new JButton("Gợi ý lịch trình");
    private final JTextArea output = new JTextArea();
    private SwingWorker<Message, Void> worker;
    private long version;
    private boolean busy;

    public TripPlannerPanel(ClientSocketManager socket) {
        this.socket = socket;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel form = new JPanel(new GridLayout(0, 1, 5, 5));
        JPanel first = new JPanel(new FlowLayout(FlowLayout.LEFT));
        first.add(new JLabel("Địa điểm:")); first.add(destination); first.add(search);
        first.add(new JLabel("Số ngày (1–7):")); first.add(days);
        form.add(first);
        JPanel second = new JPanel(new BorderLayout(8, 0));
        second.add(cities, BorderLayout.CENTER); second.add(plan, BorderLayout.EAST);
        form.add(second);
        form.add(new JLabel("Chọn đúng thành phố. Gợi ý trong 10 km theo thời tiết hiện tại, không phải dự báo nhiều ngày."));
        add(form, BorderLayout.NORTH);
        cities.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                      boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof Location city) setText(VietnameseText.city(city.getCityName(), city.getCountryCode())
                        + " · " + city.getDescription() + " · " + VietnameseText.country(city.getCountry(), city.getCountryCode()));
                return this;
            }
        });
        output.setEditable(false); output.setLineWrap(true); output.setWrapStyleWord(true);
        output.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        output.setText("Nhập địa điểm, tìm và chọn thành phố, rồi bấm Gợi ý lịch trình.");
        add(new JScrollPane(output), BorderLayout.CENTER);
        search.addActionListener(e -> searchCities());
        destination.addActionListener(e -> searchCities());
        plan.addActionListener(e -> generate());
        destination.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() { if (!busy) { cities.removeAllItems(); plan.setEnabled(false); } }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
        });
        setBusy(false);
    }

    private void searchCities() {
        if (busy) return;
        try {
            String keyword = InputValidation.keyword(destination.getText(), "Địa điểm", 2, 200);
            cities.removeAllItems();
            JsonObject data = new JsonObject(); data.addProperty("keyword", keyword);
            request(new Message("SEARCH_TRIP_CITIES", data), "SEARCH_TRIP_CITIES_RESPONSE", false);
        } catch (InputValidation.ValidationException e) { output.setText(e.getMessage()); }
    }

    private void generate() {
        if (busy || cities.getSelectedItem() == null) return;
        try { days.commitEdit(); }
        catch (java.text.ParseException e) { output.setText("Số ngày phải là số nguyên từ 1 đến 7."); return; }
        Location city = (Location) cities.getSelectedItem();
        JsonObject data = new JsonObject();
        data.addProperty("id", city.getId()); data.addProperty("days", ((Number) days.getValue()).intValue());
        request(new Message("PLAN_TRIP", data), "PLAN_TRIP_RESPONSE", true);
    }

    private void request(Message message, String expected, boolean itinerary) {
        setBusy(true);
        output.setText(itinerary ? "Đang lấy thời tiết hiện tại và địa điểm trong 10 km..." : "Đang tìm địa điểm...");
        final long selectedVersion = ++version;
        worker = new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception { return socket.sendRequest(message); }
            @Override protected void done() {
                if (isCancelled() || selectedVersion != version || !isDisplayable()) return;
                try {
                    Message response = get();
                    if (response == null) throw new java.io.IOException("Máy chủ không trả lời.");
                    if (!expected.equals(response.getAction())) throw new java.io.IOException(String.valueOf(response.getData()));
                    if (itinerary) output.setText(format(gson.toJsonTree(response.getData()).getAsJsonObject()));
                    else {
                        List<Location> found = gson.fromJson(gson.toJsonTree(response.getData()), new TypeToken<List<Location>>() {}.getType());
                        if (found != null) for (Location city : found) cities.addItem(city);
                        output.setText(cities.getItemCount() == 0 ? "Không tìm thấy thành phố. Thử tên địa điểm khác."
                                : "Chọn thành phố trong danh sách rồi bấm Gợi ý lịch trình.");
                    }
                    output.setCaretPosition(0);
                } catch (Exception e) { output.setText("Không tạo được gợi ý: " + VietnameseText.errorMessage(e)); }
                finally { setBusy(false); }
            }
        };
        worker.execute();
    }

    static String format(JsonObject result) {
        StringBuilder text = new StringBuilder("LỊCH TRÌNH · ").append(result.get("city").getAsString()).append("\n\n");
        if (result.has("weather")) {
            JsonObject weather = result.getAsJsonObject("weather");
            text.append("Thời tiết hiện tại: ").append(VietnameseText.weather(weather.get("description").getAsString()))
                    .append(" · ").append(weather.get("temp").getAsDouble()).append(" °C\n");
            if (weather.has("updatedAt")) text.append("Thời điểm dữ liệu: ").append(weather.get("updatedAt").getAsString()).append('\n');
        } else if (result.has("weatherError")) text.append("Thời tiết: ").append(result.get("weatherError").getAsString()).append('\n');
        text.append(result.get("advice").getAsString()).append("\n").append(result.get("notice").getAsString()).append("\n\n");
        for (String field : new String[]{"placesError", "placesNotice", "placesMetadataError"})
            if (result.has(field)) text.append(result.get(field).getAsString()).append("\n\n");
        for (JsonElement value : result.getAsJsonArray("days")) {
            JsonObject day = value.getAsJsonObject();
            text.append("NGÀY ").append(day.get("day").getAsInt()).append('\n');
            for (JsonElement stop : day.getAsJsonArray("places")) {
                JsonObject place = stop.getAsJsonObject();
                String category = place.get("category").getAsString();
                text.append("• ").append(place.get("name").getAsString()).append(" · ")
                        .append(String.format(VietnameseText.LOCALE, "%.2f km", place.get("distance").getAsDouble() / 1000))
                        .append(" · ").append(category.equals("indoor") ? "Có thể ở trong nhà" : category.equals("outdoor") ? "Có thể ở ngoài trời" : "Cần kiểm tra loại địa điểm").append('\n');
                if (place.has("description")) text.append("  ").append(place.get("description").getAsString()).append('\n');
                if (place.has("url")) text.append("  Nguồn: ").append(place.get("url").getAsString()).append('\n');
            }
            text.append(day.get("note").getAsString()).append("\n\n");
        }
        return text.toString();
    }

    private void setBusy(boolean value) {
        busy = value;
        destination.setEnabled(!value); search.setEnabled(!value); days.setEnabled(!value); cities.setEnabled(!value);
        plan.setEnabled(!value && cities.getItemCount() > 0);
    }

    @Override public void removeNotify() {
        version++;
        if (worker != null) worker.cancel(true);
        setBusy(false);
        super.removeNotify();
    }
}
