package service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import model.Country;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Offline regression coverage for sparse country records and optional borders enrichment. */
public final class CountryAPIServiceTest {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--live".equals(args[0])) {
            CountryAPIService api = new CountryAPIService();
            Country japan = api.getByCode("JP"), china = api.getCountry("Trung Quốc");
            System.out.println("Nhật Bản: dân số=" + japan.getPopulation() + ", có biên giới đất liền=" + japan.getHasLandBorders());
            System.out.println("Trung Quốc: dân số=" + china.getPopulation() + ", có biên giới đất liền=" + china.getHasLandBorders()
                    + ", quốc gia giáp ranh=" + china.getQuocGiaLienKe());
            check(japan.getPopulation() != null && Boolean.FALSE.equals(japan.getHasLandBorders()),
                    "Live Japan optional borders endpoint must confirm no land borders");
            check(china.getPopulation() != null && Boolean.TRUE.equals(china.getHasLandBorders()),
                    "Live China must provide valid population and border data");
            System.out.println("ĐẠT: API quốc gia thật xác nhận Nhật Bản không có biên giới đất liền và Trung Quốc có biên giới đất liền");
            return;
        }
        AtomicInteger borderCalls = new AtomicInteger(), recoveryCalls = new AtomicInteger();
        AtomicLong clock = new AtomicLong(1_000_000L);
        HttpServer fixture = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool();
        fixture.setExecutor(executor);
        fixture.createContext("/alpha/", exchange -> {
            String code = exchange.getRequestURI().getPath().substring("/alpha/".length());
            String body = switch (code) {
                case "JP" -> """
                        {"name":"Japan","alpha2Code":"JP","population":125836021,"capital":"Tokyo",
                         "currencies":[{"code":"JPY","name":"Japanese yen","symbol":"¥"}],
                         "languages":[{"name":"Japanese"}]}
                        """;
                case "CN" -> """
                        {"name":"China","alpha2Code":"CN","population":1402112000,"capital":"Beijing",
                         "borders":["RUS","VNM","IND"],"latlng":[35,105]}
                        """;
                case "KH" -> """
                        {"name":"Cambodia","alpha2Code":"KH","population":16718971,
                         "capital":"Phnom Penh","borders":["LAO","THA","VNM"]}
                        """;
                case "ML" -> """
                        {"name":"Malformedland","alpha2Code":"ML","population":234,"area":"unavailable",
                         "capital":[null,{"bad":true},"Valid Capital"],"capitalInfo":"bad",
                         "latlng":["bad",2],"timezones":[null,{},"UTC+02:00"],
                         "currencies":[null,1,{"code":"TST","name":"Fixture currency"}],
                         "languages":[null,{}, {"name":"Fixture language"}],"flags":1,
                         "region":{},"borders":null}
                        """;
                case "DL" -> """
                        {"name":{"common":"Dictionaryland"},"cca2":"DL","population":"NaN","area":"NaN",
                         "capital":[],"capitalInfo":{"latlng":{"bad":true}},"latlng":[91,2],
                         "currencies":{"ABC":null,"GOOD":{"name":"Known currency"}},
                         "languages":{"x":null,"y":{},"z":"Known language"},
                         "flags":{"png":null},"borders":[]}
                        """;
                case "NL" -> """
                        {"name":"Neighborland","alpha2Code":"NL","population":1234,"capital":"Capital"}
                        """;
                case "IL" -> """
                        {"name":"Invalidneighbors","alpha2Code":"IL","population":42}
                        """;
                case "NF" -> """
                        {"name":"Notfoundland","alpha2Code":"NF","population":77}
                        """;
                case "RL" -> """
                        {"name":"Recoveryland","alpha2Code":"RL","population":99}
                        """;
                case "SL" -> """
                        {"name":"Slowland","alpha2Code":"SL","population":73}
                        """;
                default -> "{}";
            };
            send(exchange, 200, body);
        });
        fixture.createContext("/borders/", exchange -> {
            borderCalls.incrementAndGet();
            String name = exchange.getRequestURI().getPath().substring("/borders/".length());
            if (name.equals("Japan")) send(exchange, 404, "No bordering countries found");
            else if (name.equals("Neighborland")) send(exchange, 200,
                    "[{\"name\":\"Vietnam\",\"alpha2Code\":\"VN\",\"alpha3Code\":\"VNM\"},"
                            + "{\"name\":{\"common\":\"China\"},\"cca2\":\"CN\",\"cca3\":\"CHN\"}]");
            else if (name.equals("Invalidneighbors")) send(exchange, 200, "[{\"broken\":true}]");
            else if (name.equals("Notfoundland")) send(exchange, 404, "Country not found");
            else if (name.equals("Recoveryland")) send(exchange, recoveryCalls.incrementAndGet() == 1 ? 503 : 200, "[]");
            else if (name.equals("Slowland")) {
                try { Thread.sleep(2500); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                try { send(exchange, 200, "[]"); } catch (IOException ignored) { }
            } else send(exchange, 503, "unavailable");
        });
        fixture.start();
        try {
            CountryAPIService api = new CountryAPIService("http://localhost:" + fixture.getAddress().getPort(), clock::get);
            Country japan = api.getCountry("Nhật Bản");
            check(Boolean.FALSE.equals(japan.getHasLandBorders()), "Absent Japan borders confirmed as no land borders");
            check(japan.getQuocGiaLienKe().isEmpty() && japan.getPrimaryCapital().equals("Tokyo"), "Japan retains capital");
            check(japan.getUrlQuocKy().equals("https://flagcdn.com/w320/jp.png"),
                    "A sparse v2 record still provides a genuine PNG flag by ISO code");
            int calls = borderCalls.get();
            check(api.getByCode("JP") == japan && borderCalls.get() == calls, "Successful optional borders cached");

            Country china = api.getCountry("Trung Quốc");
            check(Boolean.TRUE.equals(china.getHasLandBorders()) && china.getQuocGiaLienKe().contains("VNM"),
                    "China Vietnamese alias and explicit land borders");
            check(borderCalls.get() == calls, "Existing borders must not trigger enrichment");
            check(api.getCountry("Cam pu chia").getMaQuocGia().equals("KH"), "New Vietnamese country alias");

            Country malformed = api.getByCode("ML");
            check(malformed.getPopulation() == 234L && malformed.getTenQuocGia().equals("Malformedland"),
                    "Malformed optional fields retain valid metadata");
            check(malformed.getPrimaryCapital().equals("Valid Capital") && malformed.getCapitalLatitude() == null,
                    "Malformed capital entries do not erase valid capital");
            check(malformed.getDonViTienTe().contains("TST") && malformed.getNgonNgu().equals("Fixture language"),
                    "Malformed array entries retain valid currency and language");
            check(malformed.getLatitude() == null && malformed.getArea() == null
                    && malformed.getTimezones().equals("UTC+02:00") && malformed.getHasLandBorders() == null,
                    "Invalid numeric/coordinate/border data remains unknown");

            Country dictionary = api.getByCode("DL");
            check(dictionary.getDonViTienTe().contains("GOOD") && dictionary.getNgonNgu().equals("Known language"),
                    "Malformed dictionary entries retain valid v3 metadata");
            check(dictionary.getPopulation() == null && dictionary.getArea() == null && dictionary.getLatitude() == null
                    && Boolean.FALSE.equals(dictionary.getHasLandBorders()), "Nonfinite/invalid numbers never become zero");
            Country neighbors = api.getByCode("NL");
            check(Boolean.TRUE.equals(neighbors.getHasLandBorders()) && neighbors.getQuocGiaLienKe().equals("VNM, CHN"),
                    "Border endpoint handles v2/v3 neighbor schemas");
            Country invalid = api.getByCode("IL");
            check(invalid.getPopulation() == 42L && invalid.getHasLandBorders() == null,
                    "Malformed enrichment preserves metadata without false no-border claim");
            Country notfound = api.getByCode("NF");
            check(notfound.getPopulation() == 77L && notfound.getHasLandBorders() == null,
                    "Generic HTTP404 must not falsely declare absence of land borders");

            Country recovery = api.getByCode("RL");
            check(recovery.getPopulation() == 99L && recovery.getHasLandBorders() == null, "Enrichment failure is optional");
            check(api.getByCode("RL") == recovery && recoveryCalls.get() == 1, "Failed enrichment not retried every lookup");
            clock.addAndGet(60_000L);
            Country retried = api.getByCode("RL");
            check(Boolean.FALSE.equals(retried.getHasLandBorders()) && recoveryCalls.get() == 2,
                    "Transient border failure retried after short cache TTL");

            long started = System.nanoTime();
            Country slow = api.getByCode("SL");
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            check(slow.getPopulation() == 73L && slow.getHasLandBorders() == null && elapsedMs < 2000,
                    "Optional border timeout stays within one-second budget and preserves metadata");
            System.out.println("ĐẠT: tên quốc gia thay thế, trạng thái biên giới đất liền, trường tùy chọn, thời gian bổ sung dữ liệu và hạn thử lại");
        } finally {
            fixture.stop(0);
            executor.shutdownNow();
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
