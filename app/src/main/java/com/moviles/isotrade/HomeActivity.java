package com.moviles.isotrade;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.List;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setupSharePortfolioButton();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://www.alphavantage.co/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(ApiService.class);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

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

        // Cargar acción predeterminada
        fetchStockData("AAPL");
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
        String apiKey = "your_api_key"; // Reemplaza con tu clave API

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
                    } else {
                        Log.e("fetchStockData", "No se pudo parsear el stock: " + symbol);
                    }
                } else {
                    Log.e("fetchStockData", "Respuesta no exitosa: " + response.errorBody());
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Log.e("fetchStockData", "Error en la llamada: " + t.getMessage());
            }
        });
    }

    private Stock parseStockData(JsonObject jsonObject, String symbol) {
        if (jsonObject == null || !jsonObject.has("Time Series (Daily)")) {
            Log.e("parseStockData", "El JSON no contiene 'Time Series (Daily)'");
            return null;
        }

        JsonObject timeSeries = jsonObject.getAsJsonObject("Time Series (Daily)");
        String latestDate = timeSeries.keySet().iterator().next();
        JsonObject dayData = timeSeries.getAsJsonObject(latestDate);

        try {
            // Extraer datos del día más reciente
            String open = dayData.has("1. open") ? dayData.get("1. open").getAsString() : "0";
            String high = dayData.has("2. high") ? dayData.get("2. high").getAsString() : "0";
            String low = dayData.has("3. low") ? dayData.get("3. low").getAsString() : "0";
            String close = dayData.has("4. close") ? dayData.get("4. close").getAsString() : "0";

            return new Stock(symbol, symbol, close, "0", true, open, high, low, "0");
        } catch (Exception e) {
            Log.e("parseStockData", "Error al parsear datos: " + e.getMessage());
            return null;
        }
    }

    private void showStockDetails(Stock stock) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.stock_details, null);
        dialog.setContentView(dialogView);

        // Configurar los textos de los detalles del stock
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

    private void showDaysInputDialog(String symbol) {
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

        // Botón para cancelar
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
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
