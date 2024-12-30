package com.moviles.isotrade;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HomeActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private StockAdapter stockAdapter;
    private List<Stock> stockList;
    private ApiService apiService;
    private EditText stockInput;
    private Button addStockButton;
    private Handler handler;
    private static final String CHANNEL_ID = "stock_notifications";
    private static final int CHECK_INTERVAL = 60000; // 1 minute
    private static final double THRESHOLD = 5.0; // Example threshold
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://www.alphavantage.co/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(ApiService.class);

        stockList = new ArrayList<>();
        stockAdapter = new StockAdapter(this, stockList, this::showStockDetails);
        recyclerView.setAdapter(stockAdapter);

        stockInput = findViewById(R.id.stockInput);
        addStockButton = findViewById(R.id.addStockButton);

        addStockButton.setOnClickListener(v -> {
            String symbol = stockInput.getText().toString().trim();
            if (!symbol.isEmpty() && !isStockInList(symbol)) {
                fetchStockData(symbol);
            }
        });

        // Initial fetch for default stock
        fetchStockData("AAPL");

        // Create notification channel
        createNotificationChannel();
        Button testWorkerButton = findViewById(R.id.testWorkerButton);
        testWorkerButton.setOnClickListener(v -> testStockPriceWorker());

        // Set up handler to check stock prices periodically
        handler = new Handler();
        handler.postDelayed(this::checkStockPrices, CHECK_INTERVAL);

        // Request notification permission for Android 13 or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_POST_NOTIFICATIONS);
            }
        }

        // Schedule periodic work to check stock prices
        WorkRequest stockPriceWorkRequest = new PeriodicWorkRequest.Builder(StockPriceWorker.class, 15, TimeUnit.MINUTES)
                .setInputData(new Data.Builder().putString("symbol", "AAPL").build())
                .build();
        WorkManager.getInstance(this).enqueue(stockPriceWorkRequest);
    }

    private boolean isStockInList(String symbol) {
        for (Stock stock : stockList) {
            if (stock.getSymbol().equalsIgnoreCase(symbol)) {
                return true;
            }
        }
        return false;
    }

    private void fetchStockData(String symbol) {
        String function = "TIME_SERIES_DAILY";
        String apiKey = "KYZBJMS6CTE4CGQ1"; // Replace with your actual API key

        Call<JsonObject> call = apiService.getStockData(function, symbol, apiKey);
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject jsonObject = response.body();
                    Stock stock = parseStockData(jsonObject, symbol);
                    if (stock != null) {
                        stockList.add(stock);
                        stockAdapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(HomeActivity.this, "Stock does not exist", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(HomeActivity.this, "Stock does not exist", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(HomeActivity.this, "Failed to fetch stock data", Toast.LENGTH_SHORT).show();
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

    private void showStockDetails(Stock stock) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.stock_details, null);
        dialog.setContentView(dialogView);

        ((TextView) dialogView.findViewById(R.id.stockNameTextView)).setText(stock.getName());
        ((TextView) dialogView.findViewById(R.id.openTextView)).setText("Open: $" + stock.getOpen());
        ((TextView) dialogView.findViewById(R.id.highTextView)).setText("High: $" + stock.getHigh());
        ((TextView) dialogView.findViewById(R.id.lowTextView)).setText("Low: $" + stock.getLow());
        ((TextView) dialogView.findViewById(R.id.volumeTextView)).setText("Volume: " + stock.getVolume());

        dialog.show();
    }

    private void checkStockPrices() {
        for (Stock stock : stockList) {
            fetchStockDataForNotification(stock.getSymbol());
        }
        handler.postDelayed(this::checkStockPrices, CHECK_INTERVAL);
    }

    private void fetchStockDataForNotification(String symbol) {
        String function = "TIME_SERIES_DAILY";
        String apiKey = "KYZBJMS6CTE4CGQ1"; // Replace with your actual API key

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

    private void checkThreshold(Stock stock) {
        double currentPrice = Double.parseDouble(stock.getCurrentPrice());
        double openPrice = Double.parseDouble(stock.getOpen());
        double changePercent = ((currentPrice - openPrice) / openPrice) * 100;

        if (Math.abs(changePercent) >= THRESHOLD) {
            showNotification(stock, changePercent);
        }
    }

    private void showNotification(Stock stock, double changePercent) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification) // Ensure this icon exists in res/drawable
                .setContentTitle("Stock Price Alert")
                .setContentText(stock.getName() + " price changed by " + String.format("%.2f", changePercent) + "%")
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(stock.getSymbol().hashCode(), builder.build());
            }
        } else {
            notificationManager.notify(stock.getSymbol().hashCode(), builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Stock Notifications";
            String description = "Channel for stock price alerts";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void testStockPriceWorker() {
        WorkRequest testWorkRequest = new OneTimeWorkRequest.Builder(StockPriceWorker.class)
                .setInputData(new Data.Builder().putString("symbol", "AAPL").build())
                .build();
        WorkManager.getInstance(this).enqueue(testWorkRequest);
    }
}