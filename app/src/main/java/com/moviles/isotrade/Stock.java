package com.moviles.isotrade;

public class Stock {
    private String symbol;
    private double price;
    private String trend;

    public Stock(String symbol, double price, String trend) {
        this.symbol = symbol;
        this.price = price;
        this.trend = trend;
    }

    public String getSymbol() { return symbol; }
    public double getPrice() { return price; }
    public String getTrend() { return trend; }
}

