package panel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import model.Country;
import model.Message;
import model.User;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Offline fixtures: no visible windows, database writes, or external API requests. */
public final class CountrySearchPanelTest {
    private static final String VN_FLAG = "https://flagcdn.com/w320/vn.png";
    private static final String JP_FLAG = "https://flagcdn.com/w320/jp.png";

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        formatterAndDto();
        missingData();
        boundedPngLoader();
        displayActualFlagAndSources();
        enterWhileBusyDoesNotInterruptRequest();
        staleFlagCannotReplaceNewCountry();
        requestFailuresClearFlag();
        flagFailurePreservesCountryDetails();
        disposedWorkersDoNotUpdatePanel();
        historyUsesServerSession();
        System.out.println("ĐẠT: 12 mục quốc gia, dữ liệu thiếu, GDP theo năm, quốc kỳ PNG, nguồn dữ liệu và tra cứu bất đồng bộ an toàn");
    }

    private static void formatterAndDto() {
        Country country = vietnam();
        country.setPoliticalSystem("Thể chế từ nguồn thử nghiệm");
        country.setEconomySummary("Kinh tế từ nguồn thử nghiệm");
        country.setClimate("Khí hậu từ nguồn thử nghiệm");
        country.setCultureAndTourism("Văn hóa và du lịch từ nguồn thử nghiệm");
        country.setMetadataSourceUrl("https://countries.dev/v3.1/alpha/VN");
        country.setPoliticalSystemSourceUrl("https://vi.wikipedia.org/wiki/Việt_Nam#Chính_trị");
        country.setEconomySourceUrl("https://vi.wikipedia.org/wiki/Việt_Nam#Kinh_tế");
        country.setClimateSourceUrl("https://vi.wikipedia.org/wiki/Việt_Nam#Khí_hậu");
        country.setCultureAndTourismSourceUrl("https://vi.wikipedia.org/wiki/Việt_Nam#Văn_hóa\nhttps://vi.wikipedia.org/wiki/Việt_Nam#Du_lịch");
        country.setGdpSourceUrl("https://api.worldbank.org/v2/country/VNM/indicator/NY.GDP.MKTP.CD");
        country.setGdpUsd(476_000_000_000.0);
        country.setGdpYear(2024);
        country.setDetailsNotice("Nguồn dữ liệu thử nghiệm; không dùng cho bản chạy chính thức.");
        country.setPrimaryCapital("Hanoi");
        country.setCapitalLatitude(21.0285);
        country.setCapitalLongitude(105.8542);
        Gson gson = new Gson();
        Country received = gson.fromJson(gson.toJson(country), Country.class);
        check(gson.toJson(received).equals(gson.toJson(country)), "Preserve extended DTO fields over JSON");
        String text = CountryInfoFormatter.format(received);
        String[] labels = {"Tên quốc gia", "Thủ đô", "Vị trí địa lý / Châu lục", "Diện tích", "Dân số",
                "Ngôn ngữ chính / chính thức", "Tiền tệ", "Quốc kỳ", "Thể chế chính trị", "Kinh tế / GDP",
                "Khí hậu", "Văn hóa và du lịch nổi bật"};
        for (int index = 0; index < labels.length; index++) contains(text, (index + 1) + ". " + labels[index]);
        for (String value : List.of("Việt Nam", "Hà Nội", "Châu Á", "Đông Nam Á", "331.212 km²", "100.300.000 người",
                "Tiếng Việt", "VND", "Thể chế từ nguồn thử nghiệm", "476.000.000.000 USD (năm 2024)",
                "Khí hậu từ nguồn thử nghiệm", "Văn hóa và du lịch từ nguồn thử nghiệm", "CC BY-SA",
                country.getGdpSourceUrl(), country.getDetailsNotice())) contains(text, value);
        check(!text.contains("không suy diễn"), "Do not expose implementation explanations in the product");
    }

    private static void missingData() {
        Country country = new Country();
        String text = CountryInfoFormatter.format(country);
        for (int number = 1; number <= 12; number++) contains(text, number + ". ");
        contains(text, "GDP danh nghĩa (USD hiện hành): Chưa có dữ liệu");
        check(!text.contains("0 USD") && !text.contains("0 người") && !text.contains("0 km²"), "Missing data is not zero");
        country.setGdpUsd(12.0);
        contains(CountryInfoFormatter.format(country), "GDP danh nghĩa (USD hiện hành): Chưa có dữ liệu");
        country.setGdpYear(2024);
        country.setGdpUsd(Double.NaN);
        check(!CountryInfoFormatter.format(country).contains("NaN"), "Invalid API numeric data must not be shown");
        country.setGdpUsd(Double.POSITIVE_INFINITY);
        check(!CountryInfoFormatter.format(country).contains("∞"), "Infinite GDP is missing");
        country.setArea(-1.0);
        country.setPopulation(-1L);
        country.setLatitude(100.0);
        country.setLongitude(200.0);
        check(!CountryInfoFormatter.format(country).contains("100°"), "Invalid coordinates are missing");
    }

    private static void boundedPngLoader() throws Exception {
        check(CountryFlagLoader.trustedAddress(VN_FLAG).getHost().equals("flagcdn.com"), "Trusted flag CDN");
        CountryFlagLoader.trustedAddress("https://upload.wikimedia.org/wikipedia/commons/thumb/2/21/Flag.png");
        for (String unsafe : List.of("http://flagcdn.com/vn.png", "https://flagcdn.com.evil.test/vn.png",
                "https://localhost/vn.png", "https://flagcdn.com:8443/vn.png", "https://user@flagcdn.com/vn.png",
                "file:///flags/vn.png")) {
            expectIOException(() -> CountryFlagLoader.trustedAddress(unsafe), "Reject unsafe flag URL");
        }
        BufferedImage image = CountryFlagLoader.decodePng(png(640, 400, 0xffff0000));
        check(image.getWidth() == 256 && image.getHeight() == 160, "Scale PNG proportionally to bounded display size");
        check((image.getRGB(100, 100) & 0x00ffffff) == 0x00ff0000, "Return decoded flag pixels");
        expectIOException(() -> CountryFlagLoader.decodePng("<svg/>".getBytes(StandardCharsets.UTF_8)), "Reject SVG");
        expectIOException(() -> CountryFlagLoader.decodePng(png(4097, 1, 0xffff0000)), "Reject excessive PNG dimensions");
        expectIOException(() -> CountryFlagLoader.decodePng(png(2100, 2100, 0xffff0000)), "Reject decompression above pixel limit");
        expectIOException(() -> CountryFlagLoader.readBounded(new ByteArrayInputStream(new byte[CountryFlagLoader.MAX_BYTES + 1])),
                "Bound flag download bytes");
    }

    private static void displayActualFlagAndSources() throws Exception {
        Country country = vietnam();
        country.setCultureAndTourismSourceUrl("https://vi.wikipedia.org/wiki/Việt_Nam#Văn_hóa\nhttps://vi.wikipedia.org/wiki/Việt_Nam#Du_lịch");
        AtomicBoolean loadedOffEdt = new AtomicBoolean();
        CountrySearchPanel panel = panel(request -> {
            check(!SwingUtilities.isEventDispatchThread(), "Country request must not block EDT");
            return response(country);
        }, url -> {
            loadedOffEdt.set(!SwingUtilities.isEventDispatchThread());
            check(VN_FLAG.equals(url), "Load the actual API flag URL");
            return CountryFlagLoader.decodePng(png(640, 400, 0xffff0000));
        });
        search(panel, "Việt Nam");
        waitUntil(() -> panel.flagLabel.getIcon() != null, "Flag did not appear");
        edt(() -> {
            ImageIcon icon = (ImageIcon) panel.flagLabel.getIcon();
            check(icon.getIconWidth() == 256 && icon.getIconHeight() == 160, "Show actual decoded PNG icon");
            check(panel.flagLabel.getText().isEmpty(), "Do not display only a URL");
            contains(panel.resultArea.getText(), "12. Văn hóa và du lịch nổi bật");
            check(buttons(panel).stream().anyMatch(button -> button.getText().equals("Văn hóa")), "Clickable culture source");
            check(buttons(panel).stream().anyMatch(button -> button.getText().equals("Du lịch")), "Clickable tourism source");
            return null;
        });
        check(loadedOffEdt.get(), "Image download/decode must not block EDT");
    }

    private static void enterWhileBusyDoesNotInterruptRequest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        AtomicBoolean interrupted = new AtomicBoolean();
        CountrySearchPanel panel = panel(request -> {
            requests.incrementAndGet();
            entered.countDown();
            try { await(release, "Search request was not released"); }
            catch (InterruptedException error) { interrupted.set(true); throw error; }
            return response(vietnam());
        }, url -> new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB));
        search(panel, "Việt Nam");
        await(entered, "Search did not start");
        edt(() -> {
            check(!panel.searchButton.isEnabled(), "Search is busy");
            panel.searchField.setText("Nhật Bản");
            panel.searchField.postActionEvent();
            panel.searchField.postActionEvent();
            panel.search();
            return null;
        });
        check(requests.get() == 1 && !interrupted.get(), "Enter must not cancel an in-flight TCP request or log out the session");
        release.countDown();
        waitUntil(() -> panel.searchButton.isEnabled(), "Search completion did not enable button");
        check(requests.get() == 1 && !interrupted.get(), "Only one network request while busy");
    }

    private static void staleFlagCannotReplaceNewCountry() throws Exception {
        CountDownLatch oldEntered = new CountDownLatch(1);
        CountDownLatch oldRelease = new CountDownLatch(1);
        CountDownLatch oldExited = new CountDownLatch(1);
        Country japan = japan();
        CountrySearchPanel panel = panel(request -> response(name(request).equals("Việt Nam") ? vietnam() : japan), url -> {
            if (VN_FLAG.equals(url)) {
                oldEntered.countDown();
                waitIgnoringInterrupt(oldRelease);
                oldExited.countDown();
                return color(0xffff0000);
            }
            return color(0xff0000ff);
        });
        search(panel, "Việt Nam");
        await(oldEntered, "Old flag did not start");
        waitUntil(() -> panel.searchButton.isEnabled(), "First search did not complete");
        edt(() -> {
            panel.searchField.setText("Nhật Bản");
            panel.search();
            check(panel.flagLabel.getIcon() == null, "Clear old flag immediately on new search");
            return null;
        });
        waitUntil(() -> panel.flagLabel.getIcon() != null, "New flag did not appear");
        oldRelease.countDown();
        await(oldExited, "Old flag did not finish");
        flushDone();
        edt(() -> {
            contains(panel.resultArea.getText(), "Nhật Bản");
            check(iconColor(panel) == 0xff0000ff, "An old flag cannot overwrite the current country");
            return null;
        });
    }

    private static void requestFailuresClearFlag() throws Exception {
        for (Message failure : new Message[]{new Message("ERROR", "Nguồn quốc gia báo lỗi."), response(null)}) {
            AtomicInteger count = new AtomicInteger();
            CountrySearchPanel panel = panel(request -> count.getAndIncrement() == 0 ? response(vietnam()) : failure,
                    url -> color(0xffff0000));
            search(panel, "Việt Nam");
            waitUntil(() -> panel.flagLabel.getIcon() != null, "Initial flag did not appear");
            search(panel, "Không tồn tại");
            waitUntil(() -> panel.searchButton.isEnabled(), "Failed search did not complete");
            edt(() -> {
                check(panel.flagLabel.getIcon() == null, "Clear flag after failed/missing country response");
                String text = panel.resultArea.getText();
                check(text.contains("Không lấy được") || text.contains("Không tìm thấy"), "Clear failed lookup message");
                return null;
            });
        }
    }

    private static void flagFailurePreservesCountryDetails() throws Exception {
        CountrySearchPanel panel = panel(request -> response(vietnam()), url -> { throw new IOException("Ảnh không khả dụng."); });
        search(panel, "Việt Nam");
        waitUntil(() -> panel.flagLabel.getText().equals("Không tải được ảnh quốc kỳ."), "Flag error not shown");
        edt(() -> {
            contains(panel.resultArea.getText(), "Việt Nam");
            check(panel.flagLabel.getIcon() == null, "Failed flag has no icon");
            contains(panel.flagLabel.getToolTipText(), "Ảnh không khả dụng");
            return null;
        });
    }

    private static void disposedWorkersDoNotUpdatePanel() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        CountrySearchPanel panel = panel(request -> {
            entered.countDown();
            waitIgnoringInterrupt(release);
            exited.countDown();
            return response(vietnam());
        }, url -> { throw new AssertionError("Do not load flag after closing lookup"); });
        search(panel, "Việt Nam");
        await(entered, "Disposed search did not start");
        String before = edt(() -> {
            panel.removeNotify();
            return panel.resultArea.getText();
        });
        release.countDown();
        await(exited, "Disposed search did not return");
        flushDone();
        edt(() -> {
            check(before.equals(panel.resultArea.getText()), "Closed panel must ignore late country response");
            check(panel.flagLabel.getIcon() == null, "Closed panel has no flag");
            return null;
        });

        CountDownLatch flagEntered = new CountDownLatch(1);
        CountDownLatch flagRelease = new CountDownLatch(1);
        CountDownLatch flagExited = new CountDownLatch(1);
        CountrySearchPanel flagPanel = panel(request -> response(vietnam()), url -> {
            flagEntered.countDown();
            waitIgnoringInterrupt(flagRelease);
            flagExited.countDown();
            return color(0xffff0000);
        });
        search(flagPanel, "Việt Nam");
        await(flagEntered, "Disposed flag did not start");
        edt(() -> { flagPanel.removeNotify(); return null; });
        flagRelease.countDown();
        await(flagExited, "Disposed flag did not return");
        flushDone();
        edt(() -> {
            check(flagPanel.flagLabel.getIcon() == null, "Closed panel ignores late flag pixels");
            check(flagPanel.flagLabel.getText().equals("Đã đóng tra cứu quốc gia."), "Preserve closed flag state");
            return null;
        });
    }

    private static void historyUsesServerSession() throws Exception {
        List<Message> sent = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch history = new CountDownLatch(1);
        User user = new User(19, "fixture", "Người thử nghiệm", "fixture@example.com");
        CountrySearchPanel panel = edt(() -> new CountrySearchPanel(user, request -> {
            sent.add(request);
            if (request.getAction().equals("SAVE_HISTORY")) { history.countDown(); return new Message("SAVE_HISTORY_RESPONSE", true); }
            return response(vietnam());
        }, url -> color(0xffff0000)));
        search(panel, "Việt Nam");
        await(history, "Logged-in country lookup did not record history");
        JsonObject data = (JsonObject) sent.stream().filter(request -> request.getAction().equals("SAVE_HISTORY")).findFirst().orElseThrow().getData();
        check(!data.has("userId") && !data.has("id"), "History account identity belongs to server session");
        check(data.get("keyword").getAsString().equals("Việt Nam"), "Record searched country keyword");
        check(data.get("type").getAsString().equals("quoc_gia"), "Separate country history type");
    }

    private static Country vietnam() {
        Country country = new Country();
        country.setTenQuocGia("Vietnam"); country.setMaQuocGia("VN"); country.setThuDo("Hanoi");
        country.setRegion("Asia"); country.setSubregion("South-Eastern Asia");
        country.setLatitude(16.16667); country.setLongitude(107.83333); country.setTimezones("UTC+07:00");
        country.setPopulation(100_300_000L); country.setArea(331_212.0); country.setNgonNgu("Vietnamese");
        country.setDonViTienTe("Vietnamese đồng (VND, ₫)"); country.setUrlQuocKy(VN_FLAG);
        country.setHasLandBorders(true); country.setQuocGiaLienKe("CHN, LAO, KHM");
        return country;
    }

    private static Country japan() {
        Country country = new Country();
        country.setTenQuocGia("Japan"); country.setMaQuocGia("JP"); country.setThuDo("Tokyo");
        country.setUrlQuocKy(JP_FLAG); country.setHasLandBorders(false);
        return country;
    }

    private static Message response(Country country) { return new Message("SEARCH_COUNTRY_RESPONSE", country); }
    private static String name(Message request) { return ((JsonObject) request.getData()).get("countryName").getAsString(); }

    private static CountrySearchPanel panel(CountrySearchPanel.RequestSender sender, CountrySearchPanel.FlagLoader loader) throws Exception {
        return edt(() -> new CountrySearchPanel(null, sender, loader));
    }

    private static void search(CountrySearchPanel panel, String keyword) throws Exception {
        edt(() -> { panel.searchField.setText(keyword); panel.search(); return null; });
    }

    private static BufferedImage color(int rgb) {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, rgb); image.setRGB(1, 0, rgb);
        return image;
    }

    private static int iconColor(CountrySearchPanel panel) {
        return ((BufferedImage) ((ImageIcon) panel.flagLabel.getIcon()).getImage()).getRGB(0, 0);
    }

    private static byte[] png(int width, int height, int rgb) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, rgb);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        check(ImageIO.write(image, "png", bytes), "PNG fixture encoder");
        return bytes.toByteArray();
    }

    private static List<JButton> buttons(Container container) {
        List<JButton> result = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button) result.add(button);
            if (component instanceof Container nested) result.addAll(buttons(nested));
        }
        return result;
    }

    private static void waitUntil(Callable<Boolean> ready, String failure) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!edt(ready)) {
            if (System.nanoTime() >= deadline) throw new AssertionError(failure);
            Thread.sleep(10);
        }
    }

    private static void await(CountDownLatch latch, String failure) throws InterruptedException {
        check(latch.await(5, TimeUnit.SECONDS), failure);
    }

    private static void waitIgnoringInterrupt(CountDownLatch latch) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (latch.getCount() != 0) {
            try { if (latch.await(50, TimeUnit.MILLISECONDS)) return; }
            catch (InterruptedException ignored) { }
            if (System.nanoTime() >= deadline) throw new IOException("Fixture response was not released");
        }
    }

    private static void flushDone() throws Exception {
        CountDownLatch drained = new CountDownLatch(1);
        edt(() -> {
            javax.swing.Timer timer = new javax.swing.Timer(120, event -> drained.countDown());
            timer.setRepeats(false); timer.start(); return null;
        });
        await(drained, "EDT callbacks did not drain");
        edt(() -> null);
    }

    private static <T> T edt(Callable<T> task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return task.call();
        FutureTask<T> future = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(future);
        return future.get();
    }

    private static void expectIOException(CheckedRunnable task, String failure) throws Exception {
        try { task.run(); throw new AssertionError(failure); }
        catch (IOException expected) { }
    }

    private static void contains(String actual, String required) {
        check(actual != null && actual.contains(required), "Missing expected text: " + required + " in " + actual);
    }

    private static void check(boolean condition, String failure) { if (!condition) throw new AssertionError(failure); }
    @FunctionalInterface private interface CheckedRunnable { void run() throws Exception; }
}
