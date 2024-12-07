package com.moviles.isotrade;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

public class HomeActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private StockAdapter stockAdapter;
    private List<Stock> stockList;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Fetch stock data (mock or real API)
        stockList = new ArrayList<>(); // Populate with data from the API
        stockAdapter = new StockAdapter(this, stockList, this::showStockDetails);
        recyclerView.setAdapter(stockAdapter);

        fetchStockData(); // Fetch data from API
    }

    private void fetchStockData() {
        stockList.add(new Stock("AAPL", "150.00", "+1.2", true, "149.00", "152.00", "148.00", "50M"));
        stockList.add(new Stock("GOOG", "2800.00", "-0.5", false, "2820.00", "2850.00", "2780.00", "1.5M"));
        stockList.add(new Stock("AMZN", "3500.00", "+0.8", true, "3480.00", "3550.00", "3450.00", "3M"));
        stockAdapter.notifyDataSetChanged();
    }

    private void showStockDetails(Stock stock) {
        // Show BottomSheetDialog with detailed stock info
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

