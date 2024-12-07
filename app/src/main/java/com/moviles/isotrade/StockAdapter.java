package com.moviles.isotrade;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class StockAdapter extends RecyclerView.Adapter<StockAdapter.StockViewHolder> {
    private final Context context;
    private final List<Stock> stockList;
    private final OnStockClickListener listener;

    public StockAdapter(Context context, List<Stock> stockList, OnStockClickListener listener) {
        this.context = context;
        this.stockList = stockList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StockViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.stock_item, parent, false);
        return new StockViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StockViewHolder holder, int position) {
        Stock stock = stockList.get(position);

        holder.stockNameTextView.setText(stock.getName());
        holder.currentPriceTextView.setText("$" + stock.getCurrentPrice());
        holder.changePercentTextView.setText("(" + stock.getChangePercent() + "%)");

        // Set arrow based on price change
        if (stock.isPriceUp()) {
            holder.arrowImageView.setImageResource(R.drawable.ic_arrow_up); // Arrow up icon
        } else {
            holder.arrowImageView.setImageResource(R.drawable.ic_arrow_down); // Arrow down icon
        }

        // Handle item click
        holder.itemView.setOnClickListener(v -> listener.onStockClick(stock));
    }

    @Override
    public int getItemCount() {
        return stockList.size();
    }

    public static class StockViewHolder extends RecyclerView.ViewHolder {
        TextView stockNameTextView, currentPriceTextView, changePercentTextView;
        ImageView arrowImageView;

        public StockViewHolder(@NonNull View itemView) {
            super(itemView);
            stockNameTextView = itemView.findViewById(R.id.stockNameTextView);
            currentPriceTextView = itemView.findViewById(R.id.currentPriceTextView);
            changePercentTextView = itemView.findViewById(R.id.changePercentTextView);
            arrowImageView = itemView.findViewById(R.id.arrowImageView);
        }
    }

    public interface OnStockClickListener {
        void onStockClick(Stock stock);
    }
}

