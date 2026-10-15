package panel;

import java.util.Locale;
import javax.swing.*;

/** Offline checks: no requests to public OSM servers. */
public final class CityMapPanelTest {
    public static void main(String[] args) throws Exception {
        check(Math.abs(CityMapPanel.worldX(0, 2) - 512) < 0.0001, "Prime meridian");
        check(Math.abs(CityMapPanel.worldY(0, 2) - 512) < 0.0001, "Equator");
        check(CityMapPanel.worldX(105.8542, 11) > CityMapPanel.worldX(0, 11), "East longitude");
        check(CityMapPanel.worldY(21.0285, 11) < CityMapPanel.worldY(0, 11), "North latitude");
        check(Double.isFinite(CityMapPanel.worldY(90, 18)), "Polar coordinates stay finite");
        check(Double.isFinite(CityMapPanel.worldY(-90, 18)), "South pole stays finite");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            String link = CityMapPanel.mapUri(21.0285, 105.8542, 11).toString();
            check(link.contains("mlat=21.0285000&mlon=105.8542000"), "Marker uses original coordinates and decimal points");
        } finally { Locale.setDefault(previous); }
        SwingUtilities.invokeAndWait(() -> {
            CityMapPanel panel = new CityMapPanel();
            panel.showCity("Hà Nội", 21.0285, 105.8542);
            panel.showCity("Tokyo", 35.6762, 139.6503);
            panel.showCity("Invalid", Double.NaN, 0);
            panel.clear();
            check(!panel.isShowing(), "Hidden panel does not load network tiles");
        });
        System.out.println("CityMapPanelTest: projection, coordinates, locale-safe map links and hidden-panel lifecycle passed.");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
