package com.moviles.isotrade;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class StockAdapter extends RecyclerView.Adapter<StockAdapter.StockViewHolder> {
    private List<Stock> stockList;
    private OnStockClickListener listener;

    public interface OnStockClickListener {
        void onStockClick(Stock stock);
        void onStockLongClick(Stock stock);
    }

    public StockAdapter(List<Stock> stockList, OnStockClickListener listener) {
        this.stockList = stockList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StockViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.stock_item, parent, false);
        return new StockViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StockViewHolder holder, int position) {
        Stock stock = stockList.get(position);
        stock.calculateChangePercent();
        holder.stockNameTextView.setText(stock.getName());
        holder.currentPriceTextView.setText("$" + stock.getCurrentPrice());
        holder.changePercentTextView.setText("(" + stock.getChangePercent() + "%)");


        if (stock.isPriceUp()) {
            holder.arrowImageView.setImageResource(R.drawable.ic_arrow_up);
        } else {
            holder.arrowImageView.setImageResource(R.drawable.ic_arrow_down);
        }


        holder.itemView.setOnClickListener(v -> listener.onStockClick(stock));


        holder.itemView.setOnLongClickListener(v -> {
            listener.onStockLongClick(stock);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return stockList.size();
    }

    public static class StockViewHolder extends RecyclerView.ViewHolder {
        TextView stockNameTextView;
        TextView currentPriceTextView;
        TextView changePercentTextView;
        ImageView arrowImageView;

        public StockViewHolder(@NonNull View itemView) {
            super(itemView);
            stockNameTextView = itemView.findViewById(R.id.stockNameTextView);
            currentPriceTextView = itemView.findViewById(R.id.currentPriceTextView);
            changePercentTextView = itemView.findViewById(R.id.changePercentTextView);
            arrowImageView = itemView.findViewById(R.id.arrowImageView);
        }
    }
}