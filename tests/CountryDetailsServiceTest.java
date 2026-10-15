package service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dao.CountryDAO;
import model.Country;
import model.Message;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Offline API fixtures only: no real database, API key or production demo data. */
public final class CountryDetailsServiceTest {
    private static final Gson GSON = new Gson();
    private static final String METADATA = """
            {"name":"Vietnam","alpha2Code":"VN","capital":"Hanoi","population":97338583,
             "area":331212,"region":"Asia","subregion":"South-Eastern Asia","latlng":[16,108],
             "currencies":[{"code":"VND","name":"Vietnamese dong","symbol":"₫"}],
             "languages":[{"name":"Vietnamese"}],"flags":{"png":"https://flagcdn.com/w320/vn.png"},
             "borders":["CHN","LAO","KHM"]}
            """;
    private static final String POLITICS = "Quốc gia có hệ thống chính trị được mô tả trong đoạn văn thử nghiệm này.";
    private static final String ECONOMY = "Nền kinh tế có ngành công nghiệp và dịch vụ được ghi nhận trong nguồn thử nghiệm.";
    private static final String CLIMATE = "Khí hậu có sự khác nhau giữa các vùng địa lý theo bài viết thử nghiệm này.";
    private static final String SEASONS = "Các mùa trong năm tạo ra những đặc trưng khí hậu riêng cho từng khu vực.";
    private static final String CULTURE = "Văn hóa nổi bật với các hoạt động lễ hội và truyền thống được mô tả trong nguồn.";
    private static final String TOURISM = "Du lịch có những di sản và địa điểm nổi bật theo nội dung bài viết thử nghiệm.";
    private static final String UNRELATED = "KHONG_DUOC_LAY_NOI_DUNG_NAY Đây là nội dung mục tài liệu không liên quan.";
    private static final String NOISE = "KHONG_DUOC_LAY_NHIEU Đây là bảng, chỉ dẫn hoặc thành phần giao diện Wikipedia.";
    private static final String ARTICLE = """
            <html><head><script>KHONG_DUOC_LAY_NHIEU</script></head><body>
            <table class="infobox"><tr><td><p>%s</p></td></tr></table>
            <div class="mw-heading mw-heading2"><h2 id="Địa_lý">Địa lý</h2></div>
            <div class="mw-heading mw-heading3"><h3 id="Khí_hậu">Khí hậu</h3>
              <span class="mw-editsection">[sửa mã nguồn]</span></div>
            <p>%s<sup class="reference">[999]</sup></p>
            <h4 id="Các_mùa">Các mùa</h4><p>%s</p>
            <div class="navbox"><p>%s</p></div>
            <div role="navigation"><p>%s</p></div>
            <div class="mw-heading mw-heading2"><h2><span class="mw-headline" id="Chính_trị">Chính trị</span>
              <span class="mw-editsection">[sửa]</span></h2></div>
            <p>%s</p><table><tr><td><p>%s</p></td></tr></table>
            <div class="hatnote"><p>%s</p></div>
            <h2 id="Kinh_tế">Kinh tế</h2><p>%s</p>
            <h2 id="Văn_hóa">Văn hóa</h2><p>%s</p>
            <h2 id="Du_lịch">Du lịch</h2><p>%s</p>
            <h2 id="Tài_liệu">Tài liệu</h2><p>%s</p>
            </body></html>
            """.formatted(NOISE, CLIMATE, SEASONS, NOISE, NOISE, POLITICS, NOISE, NOISE,
                    ECONOMY, CULTURE, TOURISM, UNRELATED);
    private static final String GDP = """
            [{"page":1,"pages":1,"total":10},[
              {"indicator":{"id":"NY.GDP.MKTP.CD"},"country":{"id":"VN"},"countryiso3code":"VNM","date":"2022","value":123},
              {"indicator":{"id":"NY.GDP.MKTP.CD"},"country":{"id":"VN"},"countryiso3code":"VNM","date":"2025","value":null},
              {"indicator":{"id":"NY.GDP.MKTP.CD"},"country":{"id":"JP"},"countryiso3code":"JPN","date":"2026","value":999},
              {"indicator":{"id":"NY.GDP.MKTP.CD"},"country":{"id":"VN"},"countryiso3code":"CHN","date":"2027","value":999},
              {"indicator":{"id":"WRONG.INDICATOR"},"country":{"id":"VN"},"countryiso3code":"VNM","date":"2028","value":999},
              {"indicator":{"id":"NY.GDP.MKTP.CD"},"country":{"id":"VN"},"countryiso3code":"VNM","date":"2024","value":1234567890123.5},
              {"indicator":{"id":"NY.GDP.MKTP.CD"},"country":{"id":"VN"},"date":"2029","value":1e309},
              {"indicator":{"id":"NY.GDP.MKTP.CD"},"country":{"id":"VN"},"date":"2030","value":"1234"},
              {"indicator":{"id":"NY.GDP.MKTP.CD"},"country":{"id":"VN"},"date":"2031","value":-1},
              {"indicator":{"id":"NY.GDP.MKTP.CD"},"country":{"id":"VN"},"date":"bad","value":999}
            ]]
            """;

    public static void main(String[] args) throws Exception {
        try (Fixture fixture = new Fixture()) {
            completeCountryAndCache(fixture);
            longSectionBoundary(fixture);
            unescoTourismLists(fixture);
            unambiguousArticleTitles(fixture);
            partialSources(fixture);
            missingAndInvalidData(fixture);
            parallelAndTimeout(fixture);
            responseSizeLimits(fixture);
            publicSearchRoute(fixture);
        }
        System.out.println("ĐẠT: thông tin quốc gia đầy đủ, phân đoạn Wikipedia, GDP có năm/nguồn, "
                + "lỗi từng phần, cache bản sao, API song song có giới hạn và tra cứu không ghi CSDL");
    }

    private static void completeCountryAndCache(Fixture fixture) throws Exception {
        CountryAPIService api = new CountryAPIService(fixture.base);
        Country base = api.getCountry("Việt Nam");
        CountryDetailsService details = fixture.service(api, "complete", "complete");
        Country country = details.getCountry("VN");
        check(country != base && country.getMaQuocGia().equals("VN"), "Enrichment copies base country");
        metadata(country);
        check(country.getPoliticalSystem().contains(POLITICS), "Political section extraction");
        check(country.getEconomySummary().contains(ECONOMY), "Economy section extraction");
        check(country.getClimate().contains(CLIMATE) && country.getClimate().contains(SEASONS),
                "Climate under geography includes nested subheading prose");
        check(country.getCultureAndTourism().contains(CULTURE) && country.getCultureAndTourism().contains(TOURISM),
                "Culture and tourism retain their separate sections");
        String combined = country.getPoliticalSystem() + country.getEconomySummary()
                + country.getClimate() + country.getCultureAndTourism();
        check(!combined.contains("KHONG_DUOC_LAY") && !combined.contains("999") && !combined.contains("sửa"),
                "Exclude tables, navigation, scripts, references, edit links and unrelated headings");
        check(!country.getPoliticalSystem().contains(CLIMATE) && !country.getClimate().contains(POLITICS),
                "Heading boundaries do not mix different topics");
        check(country.getGdpYear() == 2024 && country.getGdpUsd() == 1234567890123.5,
                "Newest valid same-country GDP skips null, wrong country/indicator, nonfinite and malformed numbers");
        check(country.getMetadataSourceUrl().equals("https://countries.dev/alpha/VN"), "Metadata attribution");
        source(country.getPoliticalSystemSourceUrl(), "Chính_trị");
        source(country.getEconomySourceUrl(), "Kinh_tế");
        source(country.getClimateSourceUrl(), "Khí_hậu");
        check(decoded(country.getCultureAndTourismSourceUrl()).contains("#Văn_hóa")
                && decoded(country.getCultureAndTourismSourceUrl()).contains("#Du_lịch"),
                "Both culture and tourism have article section attribution");
        check(country.getGdpSourceUrl().equals("https://data.worldbank.org/indicator/NY.GDP.MKTP.CD?locations=VN"),
                "GDP attribution includes selected country");
        check(empty(country.getDetailsNotice()), "Complete source data has no failure notice");
        check(base.getPoliticalSystem() == null && base.getGdpUsd() == null && base.getMetadataSourceUrl() == null,
                "Country metadata cache is not modified by enrichment");
        int wikiCalls = fixture.calls("wiki/complete"), gdpCalls = fixture.calls("bank/complete");
        country.setPoliticalSystem("consumer mutation");
        country.setPopulation(1L);
        country.setGdpUsd(0.0);
        Country again = details.getCountry("Việt Nam");
        check(again != country && again.getPoliticalSystem().contains(POLITICS)
                && again.getPopulation() == 97338583L && again.getGdpUsd() == 1234567890123.5,
                "Consumer edits cannot poison cached country details");
        check(fixture.calls("wiki/complete") == wikiCalls && fixture.calls("bank/complete") == gdpCalls,
                "Same ISO country's aliases reuse details cache");
        Country international = details.getCountry("VNM");
        check(international.getMaQuocGia().equals("VN") && fixture.calls("wiki/complete") == wikiCalls,
                "ISO alpha3 alias resolves to same country's cached details");

        Country oldFormat = fixture.service(api, "legacy", "complete").getCountry("VN");
        check(oldFormat.getPoliticalSystem().contains(POLITICS) && empty(oldFormat.getDetailsNotice()),
                "Older MediaWiki text.* response supported alongside formatversion2");
    }

    private static void longSectionBoundary(Fixture fixture) throws Exception {
        for (int length : new int[]{1098, 1099}) {
            Country country = fixture.service("long" + length, "complete").getCountry("VN");
            check(!empty(country.getPoliticalSystem()) && country.getPoliticalSystem().length() <= 1000,
                    "Long section remains bounded at paragraph length " + length);
            check(country.getEconomySummary().contains(ECONOMY)
                    && country.getClimate().contains(CLIMATE)
                    && country.getCultureAndTourism().contains(CULTURE)
                    && country.getCultureAndTourism().contains(TOURISM),
                    "Nearly full prose buffer does not abort parsing later country sections at length " + length);
            check(country.getEconomySummary().length() <= 1000 && country.getClimate().length() <= 1000
                    && country.getCultureAndTourism().length() <= 2000 && empty(country.getDetailsNotice()),
                    "Section boundary does not introduce false missing-data notices or unbounded excerpts");
        }
    }

    private static void unescoTourismLists(Fixture fixture) throws Exception {
        String first = "Vịnh Di sản Một";
        String shortName = "Di sản Hai";
        String nestedParagraph = "Di sản Ba có những giá trị văn hóa được giới thiệu trong đoạn văn thử nghiệm.";
        for (int level : new int[]{2, 3}) {
            Country country = fixture.service("unesco" + level, "complete").getCountry("VN");
            String text = country.getCultureAndTourism();
            check(text.contains(first) && text.contains(shortName) && text.contains(nestedParagraph),
                    "UNESCO heading H" + level + " captures prose and short heritage names from LI items");
            check(occurrences(text, nestedParagraph) == 1,
                    "LI containing a paragraph must not duplicate the same heritage description at heading H" + level);
            check(!text.contains("KHONG_DUOC_LAY") && !text.contains("456")
                    && !country.getPoliticalSystem().contains("KHONG_DUOC_LAY")
                    && !country.getEconomySummary().contains("KHONG_DUOC_LAY"),
                    "Only tourism list prose is captured; other lists, navigation and references remain excluded");
            check(decoded(country.getCultureAndTourismSourceUrl()).contains("#Di_sản_văn_hóa_UNESCO")
                    && empty(country.getDetailsNotice()),
                    "UNESCO list retains source anchor without a false missing-tourism notice");
        }
    }

    private static int occurrences(String value, String part) {
        int count = 0;
        int position = 0;
        while ((position = value.indexOf(part, position)) >= 0) { count++; position += part.length(); }
        return count;
    }

    private static void unambiguousArticleTitles(Fixture fixture) throws Exception {
        for (String[] entry : new String[][]{{"GE", "Gruzia"}, {"KP", "Cộng hòa Dân chủ Nhân dân Triều Tiên"}}) {
            Country country = fixture.service("alias-" + entry[0], "alias-" + entry[0]).getCountry(entry[0]);
            check(country.getMaQuocGia().equals(entry[0]) && country.getPoliticalSystem().contains(POLITICS),
                    "ISO country uses unambiguous Vietnamese article title instead of disambiguation page: " + entry[0]);
            check(decoded(country.getPoliticalSystemSourceUrl()).equals(
                            "https://vi.wikipedia.org/wiki/" + entry[1].replace(' ', '_') + "#Chính_trị"),
                    "Resolved article source remains attributed to the selected country's page: " + entry[0]);
        }
    }

    private static void partialSources(Fixture fixture) throws Exception {
        Country wikiUnavailable = fixture.service("fail", "complete").getCountry("VN");
        metadata(wikiUnavailable);
        check(wikiUnavailable.getGdpYear() == 2024 && wikiUnavailable.getGdpUsd() > 0,
                "Wikipedia outage retains genuine World Bank GDP and base metadata");
        check(empty(wikiUnavailable.getPoliticalSystem()) && empty(wikiUnavailable.getClimate())
                && wikiUnavailable.getDetailsNotice().contains("Wikipedia")
                && wikiUnavailable.getDetailsNotice().contains("503"), "Wikipedia HTTP failure is explicit");

        Country gdpUnavailable = fixture.service("complete", "fail").getCountry("VN");
        metadata(gdpUnavailable);
        check(gdpUnavailable.getPoliticalSystem().contains(POLITICS) && gdpUnavailable.getClimate().contains(CLIMATE),
                "World Bank outage retains Wikipedia information");
        check(gdpUnavailable.getGdpUsd() == null && gdpUnavailable.getGdpYear() == null
                && gdpUnavailable.getDetailsNotice().contains("World Bank")
                && gdpUnavailable.getDetailsNotice().contains("429"), "GDP failure is explicit, never converted to zero");
    }

    private static void missingAndInvalidData(Fixture fixture) throws Exception {
        Country missing = fixture.service("missing", "empty").getCountry("VN");
        metadata(missing);
        check(missing.getPoliticalSystem().contains(POLITICS) && empty(missing.getClimate())
                && empty(missing.getEconomySummary()) && empty(missing.getCultureAndTourism()),
                "Missing sections retain only available prose, without invented facts");
        for (String topic : new String[]{"khí hậu", "kinh tế", "văn hóa", "du lịch", "GDP"}) {
            check(missing.getDetailsNotice().contains(topic), "Missing section notice: " + topic);
        }
        check(missing.getGdpUsd() == null && missing.getGdpYear() == null,
                "All-null World Bank records stay unknown");

        Country malformed = fixture.service("malformed", "malformed").getCountry("VN");
        metadata(malformed);
        check(empty(malformed.getPoliticalSystem()) && malformed.getGdpUsd() == null
                && malformed.getDetailsNotice().contains("Wikipedia")
                && malformed.getDetailsNotice().contains("World Bank"), "Malformed source responses produce explicit notices");

        Country noArticle = fixture.service("missingtitle", "complete").getCountry("VN");
        check(noArticle.getDetailsNotice().contains("chưa có bài viết") && noArticle.getGdpUsd() != null,
                "MediaWiki missingtitle error preserves GDP and describes absent article");

        int beforeWiki = fixture.calls("wiki/complete"), beforeGdp = fixture.calls("bank/complete");
        Country unknownIso = fixture.service("complete", "complete").getCountry("ZZ");
        check(unknownIso.getPopulation() == 42L && unknownIso.getDetailsNotice().contains("ISO"),
                "Unknown ISO cannot fetch unrelated article or GDP");
        check(fixture.calls("wiki/complete") == beforeWiki && fixture.calls("bank/complete") == beforeGdp,
                "Invalid country ISO avoids both enrichment requests");
    }

    private static void parallelAndTimeout(Fixture fixture) throws Exception {
        Country parallel = fixture.service("parallel", "parallel").getCountry("VN");
        check(fixture.parallelStarted.getCount() == 0 && fixture.parallelObserved.get(),
                "Both independent enrichment requests are in progress concurrently");
        check(parallel.getGdpYear() == 2024 && parallel.getClimate().contains(CLIMATE),
                "Concurrent enrichment produces complete country details");
        long started = System.nanoTime();
        Country slow = fixture.service("slow", "slow").getCountry("VN");
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        metadata(slow);
        check(elapsed < 9000 && slow.getGdpUsd() == null && empty(slow.getPoliticalSystem()),
                "Two slow enrichments share one bounded request budget; elapsed=" + elapsed);
        check(slow.getDetailsNotice().contains("Wikipedia") && slow.getDetailsNotice().contains("World Bank")
                && slow.getDetailsNotice().contains("thời gian chờ"), "Both slow sources show timeout reasons");
    }

    private static void responseSizeLimits(Fixture fixture) throws Exception {
        Country oversized = fixture.service("big", "big").getCountry("VN");
        metadata(oversized);
        check(oversized.getDetailsNotice().contains("kích thước") && oversized.getGdpUsd() == null
                && empty(oversized.getPoliticalSystem()), "Oversized external source bodies are rejected without losing metadata");
    }

    private static void publicSearchRoute(Fixture fixture) throws Exception {
        Class<?> type = Class.forName("ClientHandler");
        Object handler = type.getConstructor(Socket.class, KeyPair.class).newInstance(new Socket(), null);
        Field details = type.getDeclaredField("countryDetailsService");
        details.setAccessible(true);
        details.set(handler, fixture.service("complete", "complete"));
        // If the unused DAO remains in the handler, fail immediately on accidental database access.
        try {
            Field dao = type.getDeclaredField("countryDAO");
            dao.setAccessible(true);
            dao.set(handler, new CountryDAO() {
                @Override public List<Country> searchByName(String keyword) {
                    throw new AssertionError("SEARCH_COUNTRY must not access saved country database records");
                }
            });
        } catch (NoSuchFieldException ignored) { }
        Method request = type.getDeclaredMethod("handleRequest", String.class);
        request.setAccessible(true);
        JsonObject data = new JsonObject();
        data.addProperty("countryName", "Việt Nam");
        JsonObject result = response(request, handler, data);
        check(result.get("action").getAsString().equals("SEARCH_COUNTRY_RESPONSE"),
                "Public SEARCH_COUNTRY route accepts Vietnamese country query without login");
        Country country = GSON.fromJson(result.get("data"), Country.class);
        metadata(country);
        check(country.getPoliticalSystem().contains(POLITICS) && country.getGdpYear() == 2024,
                "Search response serializes enriched fields, not just legacy metadata");
        data.addProperty("countryName", "XX");
        result = response(request, handler, data);
        check(result.get("action").getAsString().equals("ERROR")
                && result.get("data").getAsString().contains("Không tra cứu được quốc gia"),
                "Unknown country gives clear route error instead of a demo record");
    }

    private static JsonObject response(Method request, Object handler, JsonObject data) throws Exception {
        return JsonParser.parseString((String) request.invoke(handler,
                new Message("SEARCH_COUNTRY", data).toJson())).getAsJsonObject();
    }

    private static void metadata(Country country) {
        check(country.getTenQuocGia().equals("Vietnam") && country.getThuDo().equals("Hanoi"), "Name and capital retained");
        check(country.getRegion().equals("Asia") && country.getLatitude() == 16 && country.getLongitude() == 108,
                "Continent and geographical coordinates retained");
        check(country.getArea() == 331212 && country.getPopulation() == 97338583L, "Area and population retained");
        check(country.getNgonNgu().equals("Vietnamese") && country.getDonViTienTe().contains("VND"),
                "Language and currency retained");
        check(country.getUrlQuocKy().equals("https://flagcdn.com/w320/vn.png"), "Raster flag image URL retained");
    }

    private static void source(String value, String anchor) {
        check(decoded(value).equals("https://vi.wikipedia.org/wiki/Việt_Nam#" + anchor),
                "Wikipedia source links to correct article and section: " + anchor);
    }

    private static String decoded(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
    private static boolean empty(String value) { return value == null || value.isBlank(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class Fixture implements AutoCloseable {
        private final HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();
        final CountDownLatch parallelStarted = new CountDownLatch(2);
        final AtomicBoolean parallelObserved = new AtomicBoolean();
        final String base;

        Fixture() throws IOException {
            server.setExecutor(executor);
            server.createContext("/alpha/", exchange -> {
                String code = exchange.getRequestURI().getPath().substring("/alpha/".length());
                if (code.equals("VN") || code.equals("VNM")) send(exchange, 200, METADATA);
                else if (code.equals("GE") || code.equals("KP")) send(exchange, 200,
                        "{\"name\":\"Country fixture\",\"alpha2Code\":\"" + code + "\",\"borders\":[]}");
                else if (code.equals("ZZ")) send(exchange, 200,
                        "{\"name\":\"Fixtureland\",\"alpha2Code\":\"ZZ\",\"population\":42,\"borders\":[]}");
                else send(exchange, 404, "Country not found");
            });
            server.createContext("/wiki/", exchange -> {
                String mode = exchange.getRequestURI().getPath().substring("/wiki/".length());
                increment("wiki/" + mode);
                String query = decoded(exchange.getRequestURI().getRawQuery());
                String articleTitle = mode.equals("alias-GE") ? "Gruzia"
                        : mode.equals("alias-KP") ? "Cộng hòa Dân chủ Nhân dân Triều Tiên" : "Việt Nam";
                check(query.contains("action=parse") && query.contains("page=" + articleTitle)
                        && query.contains("prop=text"), "Wikipedia requests Vietnamese country article HTML");
                if (mode.equals("parallel")) rendezvous();
                if (mode.equals("slow")) pause();
                if (mode.equals("fail")) send(exchange, 503, "unavailable");
                else if (mode.equals("malformed")) send(exchange, 200, "{bad JSON");
                else if (mode.equals("missingtitle")) send(exchange, 200, "{\"error\":{\"code\":\"missingtitle\"}}");
                else if (mode.equals("big")) send(exchange, 200, "x".repeat(3 * 1024 * 1024 + 1024));
                else if (mode.equals("missing")) send(exchange, 200,
                        wikipedia("<h2 id=\"Chính_trị\">Chính trị</h2><p>" + POLITICS + "</p>", false));
                else if (mode.startsWith("long")) {
                    int length = Integer.parseInt(mode.substring("long".length()));
                    String article = "<h2 id=\"Chính_trị\">Chính trị</h2><p>" + "A".repeat(length)
                            + "</p><p>" + POLITICS + "</p><h2 id=\"Kinh_tế\">Kinh tế</h2><p>" + ECONOMY
                            + "</p><h2 id=\"Khí_hậu\">Khí hậu</h2><p>" + CLIMATE
                            + "</p><h2 id=\"Văn_hóa\">Văn hóa</h2><p>" + CULTURE
                            + "</p><h2 id=\"Du_lịch\">Du lịch</h2><p>" + TOURISM + "</p>";
                    send(exchange, 200, wikipedia(article, false));
                }
                else if (mode.startsWith("unesco")) {
                    int level = Integer.parseInt(mode.substring("unesco".length()));
                    String article = "<h2 id=\"Chính_trị\">Chính trị</h2><p>" + POLITICS
                            + "</p><ul><li>" + NOISE + "</li></ul>"
                            + "<h2 id=\"Kinh_tế\">Kinh tế</h2><p>" + ECONOMY
                            + "</p><ul><li>" + NOISE + "</li></ul>"
                            + "<h2 id=\"Khí_hậu\">Khí hậu</h2><p>" + CLIMATE
                            + "</p><h2 id=\"Văn_hóa\">Văn hóa</h2><p>" + CULTURE
                            + "</p><h" + level + " id=\"Di_sản_văn_hóa_UNESCO\">Di sản văn hóa UNESCO</h" + level + ">"
                            + "<ul><li><a href=\"/wiki/Test\">Vịnh Di sản Một</a> được bảo tồn theo nguồn thử nghiệm."
                            + "<sup class=\"reference\">[456]</sup></li>"
                            + "<li><a href=\"/wiki/Test2\">Di sản Hai</a></li>"
                            + "<li><p>Di sản Ba có những giá trị văn hóa được giới thiệu trong đoạn văn thử nghiệm.</p></li></ul>"
                            + "<div class=\"navbox\"><ul><li>" + NOISE + "</li></ul></div>"
                            + "<div class=\"reflist\"><ul><li>" + NOISE + "</li></ul></div>"
                            + "<h2>Liên kết ngoài</h2><ul><li>" + UNRELATED + "</li></ul>";
                    send(exchange, 200, wikipedia(article, false));
                }
                else if (mode.startsWith("alias-")) send(exchange, 200, wikipedia(ARTICLE, false, articleTitle));
                else send(exchange, 200, wikipedia(ARTICLE, mode.equals("legacy")));
            });
            server.createContext("/bank/", exchange -> {
                String mode = exchange.getRequestURI().getPath().split("/")[2];
                increment("bank/" + mode);
                String countryCode = mode.startsWith("alias-") ? mode.substring("alias-".length()) : "VN";
                check(exchange.getRequestURI().getPath().endsWith("/v2/country/" + countryCode + "/indicator/NY.GDP.MKTP.CD"),
                        "World Bank request uses selected country's ISO and nominal GDP indicator");
                check(exchange.getRequestURI().getRawQuery().contains("format=json"), "World Bank JSON response requested");
                if (mode.equals("parallel")) rendezvous();
                if (mode.equals("slow")) pause();
                if (mode.equals("fail")) send(exchange, 429, "rate limited");
                else if (mode.equals("malformed")) send(exchange, 200, "{\"error\":true}");
                else if (mode.equals("big")) send(exchange, 200, "x".repeat(512 * 1024 + 1024));
                else if (mode.equals("empty")) send(exchange, 200, "[{\"page\":1},[{\"indicator\":{\"id\":\"NY.GDP.MKTP.CD\"},"
                        + "\"country\":{\"id\":\"VN\"},\"date\":\"2025\",\"value\":null}]]");
                else send(exchange, 200, GDP);
            });
            server.start();
            base = "http://localhost:" + server.getAddress().getPort();
        }

        CountryDetailsService service(String wiki, String bank) {
            return service(new CountryAPIService(base), wiki, bank);
        }
        CountryDetailsService service(CountryAPIService countries, String wiki, String bank) {
            return new CountryDetailsService(countries, base + "/wiki/" + wiki, base + "/bank/" + bank);
        }
        int calls(String name) { AtomicInteger count = requests.get(name); return count == null ? 0 : count.get(); }
        private void increment(String name) { requests.computeIfAbsent(name, ignored -> new AtomicInteger()).incrementAndGet(); }
        private void rendezvous() {
            parallelStarted.countDown();
            try { if (parallelStarted.await(2, TimeUnit.SECONDS)) parallelObserved.set(true); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        private static void pause() {
            try { Thread.sleep(11000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        @Override public void close() { server.stop(0); executor.shutdownNow(); }
    }

    private static String wikipedia(String html, boolean legacy) {
        return wikipedia(html, legacy, "Việt Nam");
    }

    private static String wikipedia(String html, boolean legacy, String articleTitle) {
        JsonObject parse = new JsonObject();
        parse.addProperty("title", articleTitle);
        if (legacy) {
            JsonObject text = new JsonObject();
            text.addProperty("*", html);
            parse.add("text", text);
        } else parse.addProperty("text", html);
        JsonObject root = new JsonObject();
        root.add("parse", parse);
        return root.toString();
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        } catch (IOException failure) {
            // Timeout and size-limit tests intentionally cancel the receiving request.
            if (!exchange.getRequestURI().getPath().contains("/slow")
                    && !exchange.getRequestURI().getPath().contains("/big")) throw failure;
        } finally { exchange.close(); }
    }
}
