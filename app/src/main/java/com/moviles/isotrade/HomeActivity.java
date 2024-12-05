package com.moviles.isotrade;



import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeActivity extends AppCompatActivity {
    private TextView priceTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home); // Asegúrate de que el layout esté en activity_home.xml

        priceTextView = findViewById(R.id.priceTextView);

        fetchStockPrice();
    }

    private void fetchStockPrice() {
        ApiService service = ApiClient.getRetrofitInstance().create(ApiService.class);
        Call<JsonObject> call = service.getStockData("GLOBAL_QUOTE", "AAPL", "TU_CLAVE_API");

        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject globalQuote = response.body().getAsJsonObject("Global Quote");
                    if (globalQuote != null) {
                        String price = globalQuote.get("05. price").getAsString();
                        priceTextView.setText("$" + price);
                    } else {
                        Toast.makeText(HomeActivity.this, "No se encontró información.", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(HomeActivity.this, "Error al obtener datos.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
