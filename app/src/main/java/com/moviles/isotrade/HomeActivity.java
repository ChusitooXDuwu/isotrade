package com.moviles.isotrade;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Environment;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.github.mikephil.charting.charts.CandleStickChart;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HomeActivity extends AppCompatActivity {

    private ApiService apiService;
    private List<Stock> stockList;
    private StockAdapter stockAdapter;
    private RecyclerView recyclerView;
    private EditText stockInput;
    private Button addStockButton;
    private Handler handler;
    private static final String CHANNEL_ID = "stock_notifications";
    private static final int CHECK_INTERVAL = 3600000; // 1 hour
    private static final double THRESHOLD = 5.0; // Example threshold
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 1;
    private static final String PREFS_NAME = "StockPrefs";
    private static final String STOCKS_KEY = "stocks";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setupSharePortfolioButton();
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));


        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://www.alphavantage.co/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(ApiService.class);

        stockList = new ArrayList<>();
        stockAdapter = new StockAdapter(stockList, new StockAdapter.OnStockClickListener() {
            @Override
            public void onStockClick(Stock stock) {
                showStockDetails(stock);
            }

            @Override
            public void onStockLongClick(Stock stock) {
                showDeleteConfirmationDialog(stock);
            }
        });
        recyclerView.setAdapter(stockAdapter);

        stockInput = findViewById(R.id.stockInput);
        addStockButton = findViewById(R.id.addStockButton);

        addStockButton.setOnClickListener(v -> {
            String symbol = stockInput.getText().toString().trim();
            if (!symbol.isEmpty() && !isStockInList(symbol)) {
                fetchStockData(symbol);
            }
        });

        // Create notification channel
        createNotificationChannel();

        // Set up handler to check stock prices periodically
        handler = new Handler();
        handler.postDelayed(this::checkStockPrices, CHECK_INTERVAL);

        // Request notification permission for Android 13 or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_POST_NOTIFICATIONS);
            }
        }

        // Set up test notification button
        Button testNotificationButton = findViewById(R.id.testNotificationButton);
        testNotificationButton.setOnClickListener(v -> testNotification());

        // Set up logout button
        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {

            // Redirect to MainActivity
            Intent intent = new Intent(HomeActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // Close HomeActivity
        });
        loadSavedStocks();
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
                    Log.d("API Response", response.body().toString());
                    JsonObject jsonObject = response.body();

                    Stock stock = parseStockData(jsonObject, symbol);
                    if (stock != null) {
                        stockList.add(stock);
                        stockAdapter.notifyDataSetChanged();
                        scheduleStockPriceWork(symbol); // Schedule work for the new stock
                        saveStocks(); // Save the updated stock list
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

        // Configurar el gráfico de velas
        CandleStickChart candleStickChart = dialogView.findViewById(R.id.candleStickChart);
        fetchHistoricalData(stock.getSymbol(), candleStickChart);

        // Botón para descargar datos
        Button downloadDataButton = dialogView.findViewById(R.id.downloadDataButton);
        downloadDataButton.setOnClickListener(v -> showDaysInputDialog(stock.getSymbol()));

        // Mostrar el diálogo
        dialog.show();
    }

    private void checkStockPrices() {
        for (Stock stock : stockList) {
            fetchStockDataForNotification(stock.getSymbol());
        }
        handler.postDelayed(this::checkStockPrices, CHECK_INTERVAL);
    }

    private void showDaysInputDialog(String symbol) {
        // Crear el cuadro de diálogo
        androidx.appcompat.app.AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter number of days");

        // EditText para ingresar el número de días
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        // Botón para confirmar
        builder.setPositiveButton("Download", (dialog, which) -> {
            String daysText = input.getText().toString().trim();
            if (!daysText.isEmpty()) {
                int days;
                try {
                    days = Integer.parseInt(daysText);
                    if (days > 0) {
                        fetchAndSaveJsonData(symbol, days);
                    } else {
                        Toast.makeText(this, "Please enter a positive number", Toast.LENGTH_SHORT).show();
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Field cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
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

    private void fetchHistoricalData(String symbol, CandleStickChart candleStickChart) {
        String function = "TIME_SERIES_DAILY";
        String apiKey = "your_api_key"; // Reemplaza con tu clave API

        Call<JsonObject> call = apiService.getStockData(function, symbol, apiKey);
        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject jsonObject = response.body();

                    List<CandleEntry> candleEntries = parseCandleStickData(jsonObject);
                    if (candleEntries != null && !candleEntries.isEmpty()) {
                        setUpCandleStickChart(candleStickChart, candleEntries);
                    } else {
                        Log.e("fetchHistoricalData", "No se pudieron obtener los datos de velas para: " + symbol);
                    }
                } else {
                    Log.e("fetchHistoricalData", "Respuesta no exitosa para el símbolo: " + symbol);
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Log.e("fetchHistoricalData", "Error en la llamada: " + t.getMessage());
            }
        });
    }

    private List<CandleEntry> parseCandleStickData(JsonObject jsonObject) {
        if (jsonObject == null || !jsonObject.has("Time Series (Daily)")) {
            Log.e("parseCandleStickData", "El JSON no contiene 'Time Series (Daily)'");
            return Collections.emptyList();
        }

        JsonObject timeSeries = jsonObject.getAsJsonObject("Time Series (Daily)");
        List<CandleEntry> candleEntries = new ArrayList<>();

        int index = 0;
        for (String date : timeSeries.keySet()) {
            if (index >= 60) break; // Limitar a los últimos 60 días

            JsonObject dayData = timeSeries.getAsJsonObject(date);
            try {
                float open = Float.parseFloat(dayData.get("1. open").getAsString());
                float high = Float.parseFloat(dayData.get("2. high").getAsString());
                float low = Float.parseFloat(dayData.get("3. low").getAsString());
                float close = Float.parseFloat(dayData.get("4. close").getAsString());

                candleEntries.add(new CandleEntry(index, high, low, open, close));
                index++;
            } catch (Exception e) {
                Log.e("parseCandleStickData", "Error al parsear datos para la fecha " + date, e);
            }
        }
        return candleEntries;
        }
    
    private void checkThreshold(Stock stock) {
        double currentPrice = Double.parseDouble(stock.getCurrentPrice());
        double openPrice = Double.parseDouble(stock.getOpen());
        double changePercent = ((currentPrice - openPrice) / openPrice) * 100;

        if (Math.abs(changePercent) >= THRESHOLD) {
            showNotification(stock, changePercent);
        }
    }
        private void showDaysInputDialog() {
            // Crear el cuadro de diálogo
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Enter number of days");

            // EditText para ingresar el número de días
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            builder.setView(input);

            // Botón para confirmar
            builder.setPositiveButton("Download", (dialog, which) -> {
                String daysText = input.getText().toString().trim();
                if (!daysText.isEmpty()) {
                    int days;
                    try {
                        days = Integer.parseInt(daysText);
                        if (days > 0) {
                            fetchAndSaveJsonData("AAPL", days); // Cambiar "AAPL" por el símbolo deseado
                        } else {
                            Toast.makeText(this, "Please enter a positive number", Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Field cannot be empty", Toast.LENGTH_SHORT).show();
                }
            });

            // Botón para cancelar
            builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

            builder.show();
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
            private void setUpCandleStickChart(CandleStickChart candleStickChart, List<CandleEntry> candleEntries) {
                CandleDataSet dataSet = new CandleDataSet(candleEntries, "Datos de Velas");
                dataSet.setColor(Color.rgb(80, 80, 80));
                dataSet.setShadowColor(Color.DKGRAY);
                dataSet.setShadowWidth(0.8f);
                dataSet.setDecreasingColor(Color.RED);
                dataSet.setDecreasingPaintStyle(Paint.Style.FILL);
                dataSet.setIncreasingColor(Color.GREEN);
                dataSet.setIncreasingPaintStyle(Paint.Style.FILL);
                dataSet.setNeutralColor(Color.BLUE);
                dataSet.setDrawValues(false);

                CandleData candleData = new CandleData(dataSet);
                candleStickChart.setData(candleData);
                candleStickChart.invalidate(); // Refrescar el gráfico
            }


            private void fetchAndSaveJsonData(String symbol, int days) {
                String function = "TIME_SERIES_DAILY";
                String apiKey = "your_api_key"; // Reemplaza con tu clave API

                Call<JsonObject> call = apiService.getStockData(function, symbol, apiKey);
                call.enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            JsonObject jsonObject = response.body();
                            saveJsonToFile(symbol, jsonObject);
                        } else {
                            Toast.makeText(HomeActivity.this, "Failed to fetch data", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable t) {
                        Toast.makeText(HomeActivity.this, "API call failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            private void saveJsonToFile(String symbol, JsonObject jsonObject) {
                String fileName = symbol + "_data.json";

                try {
                    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(jsonObject.toString().getBytes());
                    fos.close();

                    Toast.makeText(this, "Data saved to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    Toast.makeText(this, "Error saving file", Toast.LENGTH_SHORT).show();
                    Log.e("saveJsonToFile", "Error writing file", e);
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

            private void testNotification() {
                // Create mock stock data
                Stock mockStock = new Stock("AAPL", "Apple Inc.", "150.00", "0", true, "145.00", "155.00", "140.00", "1000000");
                double mockChangePercent = 5.0; // Example change percent

                // Show the notification
                showNotification(mockStock, mockChangePercent);
            }

            private void showDeleteConfirmationDialog(Stock stock) {
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Delete Stock")
                        .setMessage("Are you sure you want to delete " + stock.getName() + "?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            deleteStock(stock);
                            saveStocks(); // Save the updated stock list
                        })
                        .setNegativeButton("No", null)
                        .show();
            }

            private void deleteStock(Stock stock) {
                stockList.remove(stock);
                stockAdapter.notifyDataSetChanged();
                Toast.makeText(this, stock.getName() + " deleted", Toast.LENGTH_SHORT).show();
            }

            private void scheduleStockPriceWork(String symbol) {
                WorkRequest stockPriceWorkRequest = new PeriodicWorkRequest.Builder(StockPriceWorker.class, 1, TimeUnit.HOURS)
                        .setInitialDelay(1, TimeUnit.HOURS) // Add an initial delay of 1 hour
                        .setInputData(new Data.Builder().putString("symbol", symbol).build())
                        .build();
                WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                        "StockPriceWorker_" + symbol,
                        ExistingPeriodicWorkPolicy.KEEP, // Keep the existing work if it exists
                        (PeriodicWorkRequest) stockPriceWorkRequest
                );
            }

            private void saveStocks() {
                SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                Set<String> stockSymbols = new HashSet<>();
                for (Stock stock : stockList) {
                    stockSymbols.add(stock.getSymbol());
                }
                editor.putStringSet(STOCKS_KEY, stockSymbols);
                editor.apply();
            }

            private void loadSavedStocks() {
                SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                Set<String> stockSymbols = sharedPreferences.getStringSet(STOCKS_KEY, new HashSet<>());
                for (String symbol : stockSymbols) {
                    fetchStockData(symbol);
                }
            }

            private void setupSharePortfolioButton() {
                Button sharePortfolioButton = findViewById(R.id.sharePortfolioButton);
                sharePortfolioButton.setOnClickListener(v -> sharePortfolio());
            }

            private void sharePortfolio() {
                if (stockList.isEmpty()) {
                    Toast.makeText(this, "No stocks to share", Toast.LENGTH_SHORT).show();
                    return;
                }

                StringBuilder portfolioText = new StringBuilder("My Stock Portfolio:\n");
                for (Stock stock : stockList) {
                    portfolioText.append(stock.getSymbol())
                            .append(": $")
                            .append(stock.getCurrentPrice())
                            .append("\n");
                }

                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, portfolioText.toString());

                startActivity(Intent.createChooser(shareIntent, "Share Portfolio via"));
            }
}