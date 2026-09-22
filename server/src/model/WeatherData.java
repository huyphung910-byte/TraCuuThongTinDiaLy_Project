package model;

public class WeatherData {
    private String cityName;
    private double temp;
    private int humidity;
    private String description;
    private double windSpeed;
    private String updatedAt;

    public WeatherData() {
    }

    public WeatherData(String cityName, double temp, int humidity, String description, double windSpeed, String updatedAt) {
        this.cityName = cityName;
        this.temp = temp;
        this.humidity = humidity;
        this.description = description;
        this.windSpeed = windSpeed;
        this.updatedAt = updatedAt;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public double getTemp() {
        return temp;
    }

    public void setTemp(double temp) {
        this.temp = temp;
    }

    public int getHumidity() {
        return humidity;
    }

    public void setHumidity(int humidity) {
        this.humidity = humidity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getWindSpeed() {
        return windSpeed;
    }

    public void setWindSpeed(double windSpeed) {
        this.windSpeed = windSpeed;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "WeatherData{" +
                "cityName='" + cityName + '\'' +
                ", temp=" + temp +
                ", humidity=" + humidity +
                ", description='" + description + '\'' +
                ", windSpeed=" + windSpeed +
                ", updatedAt='" + updatedAt + '\'' +
                '}';
    }
}