package service;

import com.google.gson.*;
import model.Country;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Adds attributed, real country information without writing search results to the database. */
public class CountryDetailsService {
    private static final Gson GSON = new Gson();
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final long COMPLETE_TTL = Duration.ofHours(24).toMillis();
    private static final long PARTIAL_TTL = Duration.ofMinutes(1).toMillis();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(7);
    private static final long ENRICHMENT_TIMEOUT_MILLIS = 7500;
    private static final int WIKI_LIMIT = 3 * 1024 * 1024;
    private static final int GDP_LIMIT = 512 * 1024;
    // CLDR names for these codes point to a different subject on Vietnamese Wikipedia.
    private static final Map<String, String> WIKIPEDIA_TITLES = Map.of(
            "GE", "Gruzia", "KP", "Cộng hòa Dân chủ Nhân dân Triều Tiên");
    private final CountryAPIService countries;
    private final String wikipediaEndpoint;
    private final String worldBankBase;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, CachedCountry> cache = new ConcurrentHashMap<>();

    public CountryDetailsService() {
        this(new CountryAPIService(), "https://vi.wikipedia.org/w/api.php", "https://api.worldbank.org");
    }

    // Endpoints can be replaced by local HTTP fixtures; production does not synthesize missing data.
    CountryDetailsService(CountryAPIService countries, String wikipediaEndpoint, String worldBankBase) {
        this.countries = Objects.requireNonNull(countries);
        this.wikipediaEndpoint = Objects.requireNonNull(wikipediaEndpoint);
        this.worldBankBase = Objects.requireNonNull(worldBankBase).replaceAll("/+$", "");
    }

    public Country getCountry(String nameOrCode) throws IOException {
        Country country = copy(countries.getCountry(nameOrCode));
        String code = text(country.getMaQuocGia()).toUpperCase(Locale.ROOT);
        String iso2 = iso2(code);
        country.setMetadataSourceUrl("https://countries.dev/alpha/" + encode(code));
        CachedCountry cached = cache.get(code);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) return copy(cached.country);
        if (iso2.isEmpty()) {
            country.setDetailsNotice("API quốc gia thiếu mã ISO hợp lệ để lấy thông tin Wikipedia và GDP.");
            return country;
        }

        String title = new Locale("", iso2).getDisplayCountry(VIETNAMESE);
        if (title.isEmpty() || title.equals(iso2)) title = country.getTenQuocGia();
        title = WIKIPEDIA_TITLES.getOrDefault(iso2, title);
        String separator = wikipediaEndpoint.contains("?") ? "&" : "?";
        String wikiUrl = wikipediaEndpoint + separator + "action=parse&page=" + encode(title)
                + "&redirects=1&prop=text&format=json&formatversion=2";
        String gdpUrl = worldBankBase + "/v2/country/" + iso2
                + "/indicator/NY.GDP.MKTP.CD?format=json&mrnev=1&per_page=5";
        Fetch wiki = fetch(wikiUrl, WIKI_LIMIT, "Wikipedia tiếng Việt");
        Fetch gdp = fetch(gdpUrl, GDP_LIMIT, "World Bank");
        List<String> notices = new ArrayList<>();
        try {
            CompletableFuture.allOf(wiki.result, gdp.result).get(ENRICHMENT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Both calls share one deadline, rather than adding two sequential network waits.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            wiki.cancel();
            gdp.cancel();
            country.setDetailsNotice("Đã hủy tải thông tin bổ sung của quốc gia.");
            return country;
        } catch (ExecutionException e) {
            // Fetch results normally report failures as data so the base country remains usable.
        }
        Result wikiResult = wiki.finish();
        Result gdpResult = gdp.finish();
        boolean wikiComplete = false;
        boolean gdpComplete = false;
        if (wikiResult.error != null) notices.add(wikiResult.error);
        else {
            try { wikiComplete = applyWikipedia(country, wikiResult.body, notices); }
            catch (IOException | RuntimeException e) { notices.add("Wikipedia tiếng Việt trả về nội dung thiếu hoặc không hợp lệ."); }
        }
        if (gdpResult.error != null) notices.add(gdpResult.error);
        else {
            try { gdpComplete = applyGdp(country, gdpResult.body, iso2); }
            catch (RuntimeException e) { gdpComplete = false; }
            if (!gdpComplete) notices.add("World Bank chưa có GDP hợp lệ cho quốc gia này.");
        }
        country.setDetailsNotice(String.join("\n", notices));
        if (cache.size() >= 300) cache.clear();
        cache.put(code, new CachedCountry(copy(country), System.currentTimeMillis()
                + (wikiComplete && gdpComplete ? COMPLETE_TTL : PARTIAL_TTL)));
        return country;
    }

    private Fetch fetch(String url, int limit, String source) {
        LimitedSubscriber subscriber = new LimitedSubscriber(limit);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(HTTP_TIMEOUT)
                    .header("User-Agent", "TraCuuThongTinDiaLy/1.0 (educational geography application)")
                    .header("Accept", "application/json").GET().build();
            CompletableFuture<HttpResponse<String>> response = http.sendAsync(request, info -> subscriber);
            CompletableFuture<Result> result = response.handle((value, failure) -> {
                if (failure != null) {
                    Throwable cause = failure;
                    while (cause.getCause() != null) cause = cause.getCause();
                    String reason = cause instanceof HttpTimeoutException ? "hết thời gian chờ"
                            : cause instanceof BodyTooLargeException ? "nội dung vượt kích thước cho phép"
                            : "không kết nối được API";
                    return new Result(null, source + ": " + reason + ".");
                }
                int status = value.statusCode();
                if (status < 200 || status >= 300) return new Result(null, source + ": lỗi HTTP " + status + ".");
                return new Result(value.body(), null);
            });
            return new Fetch(response, result, subscriber, source);
        } catch (RuntimeException e) {
            return new Fetch(null, CompletableFuture.completedFuture(new Result(null,
                    source + ": địa chỉ API không hợp lệ.")), subscriber, source);
        }
    }

    private static boolean applyWikipedia(Country country, String body, List<String> notices) throws IOException {
        JsonElement raw = JsonParser.parseString(body);
        if (!raw.isJsonObject()) throw new IOException("Invalid Wikipedia payload");
        JsonObject root = raw.getAsJsonObject();
        JsonObject error = object(root, "error");
        if (error != null) {
            String code = string(error, "code");
            notices.add(code.equals("missingtitle") ? "Wikipedia tiếng Việt chưa có bài viết cho quốc gia này."
                    : "Wikipedia tiếng Việt báo lỗi: " + (code.matches("[a-zA-Z0-9_-]{1,80}") ? code : "không xác định") + ".");
            return false;
        }
        JsonObject parsed = object(root, "parse");
        if (parsed == null) throw new IOException("Missing Wikipedia parse result");
        String title = string(parsed, "title");
        String html = string(parsed, "text");
        // Older MediaWiki format wraps the HTML in text.*.
        if (html.isEmpty()) html = string(object(parsed, "text"), "*");
        if (title.isEmpty() || html.isEmpty()) throw new IOException("Missing article title or text");
        ArticleParser article = new ArticleParser();
        new ParserDelegator().parse(new StringReader(html), article, true);
        String articleUrl = "https://vi.wikipedia.org/wiki/" + encode(title.replace(' ', '_'));
        String politics = article.excerpt(Topic.POLITICS);
        String economy = article.excerpt(Topic.ECONOMY);
        String climate = article.excerpt(Topic.CLIMATE);
        String culture = article.excerpt(Topic.CULTURE);
        String tourism = article.excerpt(Topic.TOURISM);
        if (!politics.isEmpty()) {
            country.setPoliticalSystem(politics);
            country.setPoliticalSystemSourceUrl(article.source(Topic.POLITICS, articleUrl));
        } else notices.add("Wikipedia tiếng Việt chưa có nội dung thể chế chính trị phù hợp.");
        if (!economy.isEmpty()) {
            country.setEconomySummary(economy);
            country.setEconomySourceUrl(article.source(Topic.ECONOMY, articleUrl));
        } else notices.add("Wikipedia tiếng Việt chưa có nội dung kinh tế phù hợp.");
        if (!climate.isEmpty()) {
            country.setClimate(climate);
            country.setClimateSourceUrl(article.source(Topic.CLIMATE, articleUrl));
        } else notices.add("Wikipedia tiếng Việt chưa có nội dung khí hậu phù hợp.");
        List<String> culturalParts = new ArrayList<>();
        List<String> culturalSources = new ArrayList<>();
        if (!culture.isEmpty()) {
            culturalParts.add("Văn hóa: " + culture);
            culturalSources.add(article.source(Topic.CULTURE, articleUrl));
        } else notices.add("Wikipedia tiếng Việt chưa có nội dung văn hóa phù hợp.");
        if (!tourism.isEmpty()) {
            culturalParts.add("Du lịch: " + tourism);
            culturalSources.add(article.source(Topic.TOURISM, articleUrl));
        } else notices.add("Wikipedia tiếng Việt chưa có nội dung du lịch phù hợp.");
        country.setCultureAndTourism(truncate(String.join("\n\n", culturalParts), 2000));
        country.setCultureAndTourismSourceUrl(String.join("\n", culturalSources));
        return !politics.isEmpty() && !economy.isEmpty() && !climate.isEmpty()
                && !culture.isEmpty() && !tourism.isEmpty();
    }

    private static boolean applyGdp(Country country, String body, String iso2) {
        JsonElement raw = JsonParser.parseString(body);
        if (!raw.isJsonArray() || raw.getAsJsonArray().size() != 2) return false;
        JsonArray root = raw.getAsJsonArray();
        if (!root.get(0).isJsonObject() || !root.get(1).isJsonArray()) return false;
        String iso3;
        try { iso3 = new Locale("", iso2).getISO3Country(); }
        catch (MissingResourceException e) { return false; }
        Integer latestYear = null;
        Double latestValue = null;
        for (JsonElement element : root.get(1).getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject record = element.getAsJsonObject();
            String countryCode = string(object(record, "country"), "id");
            String countryIso3 = string(record, "countryiso3code");
            if (countryCode.isEmpty() && countryIso3.isEmpty()) continue;
            if (!countryCode.isEmpty() && !countryCode.equalsIgnoreCase(iso2)) continue;
            if (!countryIso3.isEmpty() && !countryIso3.equalsIgnoreCase(iso3)) continue;
            String indicator = string(object(record, "indicator"), "id");
            if (!indicator.equals("NY.GDP.MKTP.CD")) continue;
            String yearText = string(record, "date");
            if (!yearText.matches("[12][0-9]{3}")) continue;
            int year = Integer.parseInt(yearText);
            JsonElement number = record.get("value");
            if (number == null || !number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber()) continue;
            double value = number.getAsDouble();
            if (!Double.isFinite(value) || value < 0) continue;
            if (latestYear == null || year > latestYear) {
                latestYear = year;
                latestValue = value;
            }
        }
        if (latestYear == null) return false;
        country.setGdpUsd(latestValue);
        country.setGdpYear(latestYear);
        country.setGdpSourceUrl("https://data.worldbank.org/indicator/NY.GDP.MKTP.CD?locations=" + iso2);
        return true;
    }

    private enum Topic { POLITICS, ECONOMY, CLIMATE, CULTURE, TOURISM }

    /** Captures section prose, excluding infoboxes, reference numbers, navigation and edit links. */
    private static final class ArticleParser extends HTMLEditorKit.ParserCallback {
        private final Deque<Frame> frames = new ArrayDeque<>();
        private final EnumMap<Topic, Section> sections = new EnumMap<>(Topic.class);
        private final EnumMap<Topic, Integer> active = new EnumMap<>(Topic.class);
        private final StringBuilder heading = new StringBuilder();
        private final StringBuilder paragraph = new StringBuilder();
        private final StringBuilder tourismListItem = new StringBuilder();
        private int ignoredDepth;
        private int headingLevel;
        private boolean inParagraph;
        private int tourismListDepth;
        private String headingAnchor;

        @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            String name = tag.toString().toLowerCase(Locale.ROOT);
            boolean ignored = ignoredDepth > 0 || ignoredTag(name, attributes);
            frames.push(new Frame(name, ignored));
            if (ignored) ignoredDepth++;
            if (ignoredDepth != 0) return;
            if (name.matches("h[2-6]")) {
                headingLevel = name.charAt(1) - '0';
                heading.setLength(0);
                headingAnchor = attribute(attributes, HTML.Attribute.ID);
            } else if (headingLevel > 0 && headingAnchor.isEmpty()) {
                headingAnchor = attribute(attributes, HTML.Attribute.ID);
            }
            if (name.equals("p")) {
                inParagraph = true;
                paragraph.setLength(0);
                if (tourismListDepth > 0) tourismListItem.append(' ');
            }
            if (name.equals("li") && active.containsKey(Topic.TOURISM)) {
                if (tourismListDepth == 0) tourismListItem.setLength(0);
                else tourismListItem.append("; ");
                tourismListDepth++;
            }
        }

        @Override public void handleText(char[] data, int position) {
            if (ignoredDepth != 0) return;
            if (headingLevel > 0) heading.append(data);
            else {
                if (inParagraph) paragraph.append(data);
                if (tourismListDepth > 0) tourismListItem.append(data);
            }
        }

        @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (ignoredDepth == 0 && tag == HTML.Tag.BR) {
                if (headingLevel > 0) heading.append(' ');
                else {
                    if (inParagraph) paragraph.append(' ');
                    if (tourismListDepth > 0) tourismListItem.append(' ');
                }
            }
        }

        @Override public void handleEndTag(HTML.Tag tag, int position) {
            String name = tag.toString().toLowerCase(Locale.ROOT);
            if (ignoredDepth == 0 && headingLevel > 0 && name.equals("h" + headingLevel)) {
                finishHeading();
            } else if (ignoredDepth == 0 && name.equals("p") && inParagraph) {
                String prose = clean(paragraph.toString());
                // Heritage-list prose is emitted once by its enclosing LI, including nested paragraphs.
                if (prose.length() >= 25 && tourismListDepth == 0 && !active.isEmpty()) {
                    int deepestLevel = Collections.max(active.values());
                    for (Map.Entry<Topic, Integer> entry : active.entrySet()) {
                        if (entry.getValue() == deepestLevel) sections.get(entry.getKey()).append(prose);
                    }
                }
                inParagraph = false;
                if (tourismListDepth > 0) tourismListItem.append(' ');
            } else if (ignoredDepth == 0 && name.equals("li") && tourismListDepth > 0) {
                tourismListDepth--;
                if (tourismListDepth == 0 && active.containsKey(Topic.TOURISM)) {
                    String prose = clean(tourismListItem.toString());
                    if (prose.length() >= 10) sections.get(Topic.TOURISM).append(prose);
                }
            }
            // The parser supplies implied closing tags; unwind only when a corresponding start exists.
            boolean exists = frames.stream().anyMatch(frame -> frame.tag.equals(name));
            if (exists) {
                Frame frame;
                do {
                    frame = frames.pop();
                    if (frame.ignored) ignoredDepth--;
                } while (!frame.tag.equals(name));
            }
        }

        private void finishHeading() {
            int level = headingLevel;
            active.entrySet().removeIf(entry -> level <= entry.getValue());
            String name = clean(heading.toString());
            Topic topic = topic(name);
            if (topic != null) {
                sections.computeIfAbsent(topic, ignored -> new Section(
                        headingAnchor.isEmpty() ? name.replace(' ', '_') : headingAnchor));
                // Multiple relevant sections may contribute, while unrelated peer headings end capture.
                active.put(topic, level);
            }
            headingLevel = 0;
        }

        String excerpt(Topic topic) {
            Section section = sections.get(topic);
            return section == null ? "" : truncate(section.prose.toString(), 1000);
        }

        String source(Topic topic, String articleUrl) {
            Section section = sections.get(topic);
            return section == null || section.anchor.isEmpty() ? articleUrl : articleUrl + "#" + encode(section.anchor);
        }

        private static boolean ignoredTag(String name, MutableAttributeSet attributes) {
            if (Set.of("table", "script", "style", "sup", "head", "nav", "figure").contains(name)) return true;
            String classes = attribute(attributes, HTML.Attribute.CLASS).toLowerCase(Locale.ROOT);
            if (classes.matches(".*(?:hatnote|navbox|metadata|mbox|reflist|references|reference|mw-editsection|toc).*")) return true;
            Object role = attributes.getAttribute("role");
            return role != null && (role.toString().equals("navigation") || role.toString().equals("note"));
        }

        private static String attribute(MutableAttributeSet attributes, HTML.Attribute field) {
            Object value = attributes.getAttribute(field);
            return value == null ? "" : value.toString();
        }

        private static Topic topic(String heading) {
            String normalized = Normalizer.normalize(heading.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").replace('đ', 'd').replaceAll("\\s+", " ").trim();
            if (normalized.matches("(?:chinh tri|the che chinh tri|he thong chinh tri|chinh phu|chinh tri va chinh phu|chinh tri va phap luat)")) return Topic.POLITICS;
            if (normalized.matches("(?:kinh te|nen kinh te|kinh te va co so ha tang)")) return Topic.ECONOMY;
            if (normalized.matches("(?:khi hau|khi hau va moi truong)")) return Topic.CLIMATE;
            if (normalized.matches("(?:van hoa|van hoa va xa hoi|xa hoi va van hoa)")) return Topic.CULTURE;
            if (normalized.matches("(?:du lich|nganh du lich|di san van hoa unesco|di san the gioi)")) return Topic.TOURISM;
            return null;
        }
    }

    private static final class Section {
        final String anchor;
        final StringBuilder prose = new StringBuilder();
        Section(String anchor) { this.anchor = anchor; }
        void append(String paragraph) {
            int separatorLength = prose.length() > 0 ? 2 : 0;
            int remaining = 1100 - prose.length() - separatorLength;
            if (remaining <= 0) return;
            if (separatorLength > 0) prose.append("\n\n");
            prose.append(truncate(paragraph, remaining));
        }
    }

    private static final class Frame {
        final String tag;
        final boolean ignored;
        Frame(String tag, boolean ignored) { this.tag = tag; this.ignored = ignored; }
    }

    /** Body is cancelled before buffering more than the specified byte limit. */
    private static final class LimitedSubscriber implements HttpResponse.BodySubscriber<String> {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<String> body = new CompletableFuture<>();
        private volatile Flow.Subscription subscription;
        LimitedSubscriber(int limit) { this.limit = limit; }
        @Override public CompletionStage<String> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            if (body.isDone()) value.cancel();
            else value.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                int length = buffer.remaining();
                if (length > limit - bytes.size()) {
                    cancel();
                    body.completeExceptionally(new BodyTooLargeException());
                    return;
                }
                byte[] chunk = new byte[length];
                buffer.get(chunk);
                bytes.write(chunk, 0, length);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { body.completeExceptionally(error); }
        @Override public void onComplete() { body.complete(bytes.toString(StandardCharsets.UTF_8)); }
        void cancel() {
            Flow.Subscription value = subscription;
            if (value != null) value.cancel();
        }
    }

    private static final class BodyTooLargeException extends IOException {}
    private static final class Result {
        final String body;
        final String error;
        Result(String body, String error) { this.body = body; this.error = error; }
    }
    private static final class Fetch {
        final CompletableFuture<?> response;
        final CompletableFuture<Result> result;
        final LimitedSubscriber subscriber;
        final String source;
        Fetch(CompletableFuture<?> response, CompletableFuture<Result> result, LimitedSubscriber subscriber, String source) {
            this.response = response; this.result = result; this.subscriber = subscriber; this.source = source;
        }
        Result finish() {
            if (!result.isDone()) {
                cancel();
                return new Result(null, source + ": hết thời gian chờ.");
            }
            return result.getNow(new Result(null, source + ": không tải được dữ liệu."));
        }
        void cancel() {
            subscriber.cancel();
            if (response != null) response.cancel(true);
        }
    }
    private static final class CachedCountry {
        final Country country;
        final long expiresAt;
        CachedCountry(Country country, long expiresAt) { this.country = country; this.expiresAt = expiresAt; }
    }

    private static Country copy(Country country) { return GSON.fromJson(GSON.toJson(country), Country.class); }
    private static String iso2(String code) {
        if (code.matches("[A-Z]{2}") && Arrays.asList(Locale.getISOCountries()).contains(code)) return code;
        if (code.matches("[A-Z]{3}")) {
            for (String candidate : Locale.getISOCountries()) {
                try { if (new Locale("", candidate).getISO3Country().equals(code)) return candidate; }
                catch (MissingResourceException ignored) {}
            }
        }
        return "";
    }
    private static JsonObject object(JsonObject parent, String name) {
        JsonElement value = parent == null ? null : parent.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
    private static String string(JsonObject parent, String name) {
        JsonElement value = parent == null ? null : parent.get(name);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString().trim() : "";
    }
    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static String clean(String value) { return value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim(); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String truncate(String value, int limit) {
        if (limit <= 0) return "";
        if (value.length() <= limit) return value;
        int cut = value.lastIndexOf(' ', limit - 1);
        if (cut < limit / 2) cut = limit - 1;
        if (cut > 0 && Character.isHighSurrogate(value.charAt(cut - 1))) cut--;
        return value.substring(0, cut).trim() + "…";
    }
}
