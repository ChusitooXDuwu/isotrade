package com.moviles.isotrade;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.content.pm.PackageManager;
import android.Manifest;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.HashMap;
import java.util.Map;

public class StockPriceWorker extends Worker {
    private static final String TAG = "StockPriceWorker";
    private static final String CHANNEL_ID = "stock_notifications";
    private static final double THRESHOLD = 5.0;
    private static final long CACHE_DURATION = 3600000;
    private ApiService apiService;
    private static final Map<String, CachedStockData> cache = new HashMap<>();

    public StockPriceWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://www.alphavantage.co/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(ApiService.class);
        Log.d(TAG, "StockPriceWorker initialized");
    }

    @NonNull
    @Override
    public Result doWork() {
        String symbol = getInputData().getString("symbol");
        Log.d(TAG, "doWork called with symbol: " + symbol);
        if (symbol != null) {
            fetchStockDataForNotification(symbol);
        }
        return Result.success();
    }

    private void fetchStockDataForNotification(String symbol) {
        Log.d(TAG, "Fetching stock data for symbol: " + symbol);
        long currentTime = System.currentTimeMillis();
        CachedStockData cachedData = cache.get(symbol);

        if (cachedData != null && (currentTime - cachedData.timestamp) < CACHE_DURATION) {
            Log.d(TAG, "Using cached data for symbol: " + symbol);
            checkThreshold(cachedData.stock);
        } else {
            String function = "TIME_SERIES_DAILY";
            String apiKey = "KYZBJMS6CTE4CGQ1";

            Call<JsonObject> call = apiService.getStockData(function, symbol, apiKey);
            call.enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    Log.d(TAG, "Stock data fetched successfully");
                    if (response.isSuccessful() && response.body() != null) {
                        JsonObject jsonObject = response.body();
                        Stock stock = parseStockData(jsonObject, symbol);
                        if (stock != null) {
                            cache.put(symbol, new CachedStockData(stock, currentTime));
                            checkThreshold(stock);
                        } else {
                            Log.e(TAG, "Failed to parse stock data");
                        }
                    } else {
                        Log.e(TAG, "Response unsuccessful or body is null");
                    }
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    Log.e(TAG, "Failed to fetch stock data", t);
                }
            });
        }
    }

    private Stock parseStockData(JsonObject jsonObject, String symbol) {
        Log.d(TAG, "Parsing stock data for symbol: " + symbol);
        JsonObject metaData = jsonObject.getAsJsonObject("Meta Data");
        if (metaData == null) {
            Log.e(TAG, "Meta Data is null");
            return null;
        }
        String name = metaData.get("2. Symbol").getAsString();

        JsonObject timeSeries = jsonObject.getAsJsonObject("Time Series (Daily)");
        if (timeSeries == null || timeSeries.keySet().isEmpty()) {
            Log.e(TAG, "Time Series (Daily) is null or empty");
            return null;
        }
        String latestDate = timeSeries.keySet().iterator().next();
        JsonObject dayData = timeSeries.getAsJsonObject(latestDate);

        if (dayData == null) {
            Log.e(TAG, "Day data is null");
            return null;
        }

        String open = dayData.get("1. open").getAsString();
        String high = dayData.get("2. high").getAsString();
        String low = dayData.get("3. low").getAsString();
        String close = dayData.get("4. close").getAsString();
        String volume = dayData.get("5. volume").getAsString();

        return new Stock(symbol, name, close, "0", true, open, high, low, volume);
    }

    private void checkThreshold(Stock stock) {
        Log.d(TAG, "Checking threshold for stock: " + stock.getSymbol());
        double currentPrice = Double.parseDouble(stock.getCurrentPrice());
        double openPrice = Double.parseDouble(stock.getOpen());
        double changePercent = ((currentPrice - openPrice) / openPrice) * 100;

        if (Math.abs(changePercent) >= THRESHOLD) {
            showNotification(stock, changePercent);
        }
    }

    private void showNotification(Stock stock, double changePercent) {
        Log.d(TAG, "Showing notification for stock: " + stock.getSymbol());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Stock Price Alert")
                .setContentText(stock.getName() + " price changed by " + String.format("%.2f", changePercent) + "%")
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(stock.getSymbol().hashCode(), builder.build());
            }
        } else {
            notificationManager.notify(stock.getSymbol().hashCode(), builder.build());
        }
    }

    private static class CachedStockData {
        Stock stock;
        long timestamp;

        CachedStockData(Stock stock, long timestamp) {
            this.stock = stock;
            this.timestamp = timestamp;
        }
    }
}