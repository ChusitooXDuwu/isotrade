package com.moviles.isotrade;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import android.Manifest;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class StockPriceWorker extends Worker {
    private static final String CHANNEL_ID = "stock_notifications";
    private static final double THRESHOLD = 5.0; // Example threshold
    private ApiService apiService;

    public StockPriceWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://www.alphavantage.co/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(ApiService.class);
    }

    @NonNull
    @Override
    public Result doWork() {
        String symbol = getInputData().getString("symbol");
        if (symbol != null) {
            fetchStockDataForNotification(symbol);
        }
        return Result.success();
    }

    private void fetchStockDataForNotification(String symbol) {
        String function = "TIME_SERIES_DAILY";
        String apiKey = "your_api_key"; // Replace with your actual API key

        Call<JsonObject> call = apiService.getStockData(function, symbol, apiKey);
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject jsonObject = response.body();
                    Stock stock = parseStockData(jsonObject, symbol);
                    if (stock != null) {
                        checkThreshold(stock);
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                // Handle the failure
            }
        });
    }

    private Stock parseStockData(JsonObject jsonObject, String symbol) {
        JsonObject metaData = jsonObject.getAsJsonObject("Meta Data");
        if (metaData == null) {
            return null;
        }
        String name = metaData.get("2. Symbol").getAsString();

        JsonObject timeSeries = jsonObject.getAsJsonObject("Time Series (Daily)");
        if (timeSeries == null || timeSeries.keySet().isEmpty()) {
            return null;
        }
        String latestDate = timeSeries.keySet().iterator().next();
        JsonObject dayData = timeSeries.getAsJsonObject(latestDate);

        String open = dayData.get("1. open").getAsString();
        String high = dayData.get("2. high").getAsString();
        String low = dayData.get("3. low").getAsString();
        String close = dayData.get("4. close").getAsString();
        String volume = dayData.get("5. volume").getAsString();

        return new Stock(symbol, name, close, "0", true, open, high, low, volume);
    }

    private void checkThreshold(Stock stock) {
        double currentPrice = Double.parseDouble(stock.getCurrentPrice());
        double openPrice = Double.parseDouble(stock.getOpen());
        double changePercent = ((currentPrice - openPrice) / openPrice) * 100;

        if (Math.abs(changePercent) >= THRESHOLD) {
            showNotification(stock, changePercent);
        }
    }

    private void showNotification(Stock stock, double changePercent) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification) // Ensure this icon exists in res/drawable
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
}