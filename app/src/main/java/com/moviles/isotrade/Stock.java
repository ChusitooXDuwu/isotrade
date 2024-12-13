package com.moviles.isotrade;

public class Stock {
    private String symbol;
    private String name;
    private String currentPrice;
    private String changePercent;
    private boolean isPriceUp;
    private String open;
    private String high;
    private String low;
    private String volume;

    // Constructor
    public Stock(String symbol, String name, String currentPrice, String changePercent, boolean isPriceUp, String open, String high, String low, String volume) {
        this.symbol = symbol;
        this.name = name;
        this.currentPrice = currentPrice;
        this.changePercent = changePercent;
        this.isPriceUp = isPriceUp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.volume = volume;
    }

    // Getters
    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getCurrentPrice() {
        return currentPrice;
    }

    public String getChangePercent() {
        return changePercent;
    }

    public boolean isPriceUp() {
        return isPriceUp;
    }

    public String getOpen() {
        return open;
    }

    public String getHigh() {
        return high;
    }

    public String getLow() {
        return low;
    }

    public String getVolume() {
        return volume;
    }
}


