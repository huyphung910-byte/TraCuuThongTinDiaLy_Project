package model;

public final class HistoryEntry {
    private final String keyword;
    private final String type;

    public HistoryEntry(String keyword, String type) {
        this.keyword = keyword;
        this.type = type;
    }

    public String getKeyword() { return keyword; }
    public String getType() { return type; }
}
