package com.moviles.isotrade;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

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
        String apiKey = "your_api_key"; // Replace with your actual API key

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
                    }
                } else {
                    // Handle the error
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
        String name = metaData.get("2. Symbol").getAsString();

        JsonObject timeSeries = jsonObject.getAsJsonObject("Time Series (Daily)");
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
}