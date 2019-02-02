package com.vitorarrais.tunerun.rest;

import com.vitorarrais.tunerun.data.model.HistoryModel;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface NodeServerResource {

    @GET("/speed")
    Call<HistoryModel> historyItem(
            @Query("val") int speed
    );

    public static final Retrofit retrofit = new Retrofit.Builder()
            .baseUrl("http://192.168.0.3:3000")
            .addConverterFactory(GsonConverterFactory.create())
            .build();
}

