package com.moviles.isotrade;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ApiService {
    @GET("query")
    Call<JsonObject> getStockData(
            @Query("function") String function,
            @Query("symbol") String symbol,
            @Query("apikey") String apiKey
    );
}
