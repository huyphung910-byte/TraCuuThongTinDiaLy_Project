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
}
