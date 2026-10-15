package model;

public class Hotel {
    private int id;
    private String tenKhachSan;
    private String tenThanhPho;
    private String diaChi;
    private double giaMotDem;
    private String donViTien;
    private double danhGia;
    private String moTa;

    public Hotel() {}

    public Hotel(int id, String tenKhachSan, String tenThanhPho, String diaChi,
                 double giaMotDem, String donViTien, double danhGia, String moTa) {
        this.id = id;
        this.tenKhachSan = tenKhachSan;
        this.tenThanhPho = tenThanhPho;
        this.diaChi = diaChi;
        this.giaMotDem = giaMotDem;
        this.donViTien = donViTien;
        this.danhGia = danhGia;
        this.moTa = moTa;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTenKhachSan() { return tenKhachSan; }
    public void setTenKhachSan(String tenKhachSan) { this.tenKhachSan = tenKhachSan; }

    public String getTenThanhPho() { return tenThanhPho; }
    public void setTenThanhPho(String tenThanhPho) { this.tenThanhPho = tenThanhPho; }

    public String getDiaChi() { return diaChi; }
    public void setDiaChi(String diaChi) { this.diaChi = diaChi; }

    public double getGiaMotDem() { return giaMotDem; }
    public void setGiaMotDem(double giaMotDem) { this.giaMotDem = giaMotDem; }

    public String getDonViTien() { return donViTien; }
    public void setDonViTien(String donViTien) { this.donViTien = donViTien; }

    public double getDanhGia() { return danhGia; }
    public void setDanhGia(double danhGia) { this.danhGia = danhGia; }

    public String getMoTa() { return moTa; }
    public void setMoTa(String moTa) { this.moTa = moTa; }
}
