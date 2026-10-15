package model;

public class Country {
    private int id;
    private String tenQuocGia;
    private String maQuocGia;
    private String thuDo;
    private String donViTienTe;
    private String ngonNgu;
    private String urlQuocKy;
    private String quocGiaLienKe;
    private String diemDuLichNoiBat;
    private Long population;
    private Double area;
    private String region;
    private String subregion;
    private String timezones;
    private Double latitude;
    private Double longitude;
    private Double capitalLatitude;
    private Double capitalLongitude;
    private String primaryCapital;
    private Boolean hasLandBorders;
    private String politicalSystem;
    private String economySummary;
    private String climate;
    private String cultureAndTourism;
    private String metadataSourceUrl;
    private String politicalSystemSourceUrl;
    private String economySourceUrl;
    private String climateSourceUrl;
    private String cultureAndTourismSourceUrl;
    private String gdpSourceUrl;
    private String detailsNotice;
    private Double gdpUsd;
    private Integer gdpYear;

    public Country() {}

    public Country(int id, String tenQuocGia, String maQuocGia, String thuDo,
                   String donViTienTe, String ngonNgu, String urlQuocKy,
                   String quocGiaLienKe, String diemDuLichNoiBat) {
        this.id = id;
        this.tenQuocGia = tenQuocGia;
        this.maQuocGia = maQuocGia;
        this.thuDo = thuDo;
        this.donViTienTe = donViTienTe;
        this.ngonNgu = ngonNgu;
        this.urlQuocKy = urlQuocKy;
        this.quocGiaLienKe = quocGiaLienKe;
        this.diemDuLichNoiBat = diemDuLichNoiBat;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTenQuocGia() { return tenQuocGia; }
    public void setTenQuocGia(String tenQuocGia) { this.tenQuocGia = tenQuocGia; }

    public String getMaQuocGia() { return maQuocGia; }
    public void setMaQuocGia(String maQuocGia) { this.maQuocGia = maQuocGia; }

    public String getThuDo() { return thuDo; }
    public void setThuDo(String thuDo) { this.thuDo = thuDo; }

    public String getDonViTienTe() { return donViTienTe; }
    public void setDonViTienTe(String donViTienTe) { this.donViTienTe = donViTienTe; }

    public String getNgonNgu() { return ngonNgu; }
    public void setNgonNgu(String ngonNgu) { this.ngonNgu = ngonNgu; }

    public String getUrlQuocKy() { return urlQuocKy; }
    public void setUrlQuocKy(String urlQuocKy) { this.urlQuocKy = urlQuocKy; }

    public String getQuocGiaLienKe() { return quocGiaLienKe; }
    public void setQuocGiaLienKe(String quocGiaLienKe) { this.quocGiaLienKe = quocGiaLienKe; }

    public String getDiemDuLichNoiBat() { return diemDuLichNoiBat; }
    public void setDiemDuLichNoiBat(String diemDuLichNoiBat) { this.diemDuLichNoiBat = diemDuLichNoiBat; }

    public Long getPopulation() { return population; }
    public void setPopulation(Long population) { this.population = population; }
    public Double getArea() { return area; }
    public void setArea(Double area) { this.area = area; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getSubregion() { return subregion; }
    public void setSubregion(String subregion) { this.subregion = subregion; }
    public String getTimezones() { return timezones; }
    public void setTimezones(String timezones) { this.timezones = timezones; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Double getCapitalLatitude() { return capitalLatitude; }
    public void setCapitalLatitude(Double capitalLatitude) { this.capitalLatitude = capitalLatitude; }
    public Double getCapitalLongitude() { return capitalLongitude; }
    public void setCapitalLongitude(Double capitalLongitude) { this.capitalLongitude = capitalLongitude; }
    public String getPrimaryCapital() { return primaryCapital; }
    public void setPrimaryCapital(String primaryCapital) { this.primaryCapital = primaryCapital; }
    /** null means the API did not establish whether land borders exist. */
    public Boolean getHasLandBorders() { return hasLandBorders; }
    public void setHasLandBorders(Boolean hasLandBorders) { this.hasLandBorders = hasLandBorders; }

    public String getPoliticalSystem() { return politicalSystem; }
    public void setPoliticalSystem(String politicalSystem) { this.politicalSystem = politicalSystem; }
    public String getEconomySummary() { return economySummary; }
    public void setEconomySummary(String economySummary) { this.economySummary = economySummary; }
    public String getClimate() { return climate; }
    public void setClimate(String climate) { this.climate = climate; }
    public String getCultureAndTourism() { return cultureAndTourism; }
    public void setCultureAndTourism(String cultureAndTourism) { this.cultureAndTourism = cultureAndTourism; }
    public String getMetadataSourceUrl() { return metadataSourceUrl; }
    public void setMetadataSourceUrl(String metadataSourceUrl) { this.metadataSourceUrl = metadataSourceUrl; }
    public String getPoliticalSystemSourceUrl() { return politicalSystemSourceUrl; }
    public void setPoliticalSystemSourceUrl(String politicalSystemSourceUrl) { this.politicalSystemSourceUrl = politicalSystemSourceUrl; }
    public String getEconomySourceUrl() { return economySourceUrl; }
    public void setEconomySourceUrl(String economySourceUrl) { this.economySourceUrl = economySourceUrl; }
    public String getClimateSourceUrl() { return climateSourceUrl; }
    public void setClimateSourceUrl(String climateSourceUrl) { this.climateSourceUrl = climateSourceUrl; }
    public String getCultureAndTourismSourceUrl() { return cultureAndTourismSourceUrl; }
    public void setCultureAndTourismSourceUrl(String cultureAndTourismSourceUrl) { this.cultureAndTourismSourceUrl = cultureAndTourismSourceUrl; }
    public String getGdpSourceUrl() { return gdpSourceUrl; }
    public void setGdpSourceUrl(String gdpSourceUrl) { this.gdpSourceUrl = gdpSourceUrl; }
    public String getDetailsNotice() { return detailsNotice; }
    public void setDetailsNotice(String detailsNotice) { this.detailsNotice = detailsNotice; }
    public Double getGdpUsd() { return gdpUsd; }
    public void setGdpUsd(Double gdpUsd) { this.gdpUsd = gdpUsd; }
    public Integer getGdpYear() { return gdpYear; }
    public void setGdpYear(Integer gdpYear) { this.gdpYear = gdpYear; }
}
