package com.vitorarrais.tunerun.rest;

import com.vitorarrais.tunerun.data.model.HistoryModel;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * Created by User on 02/12/2016.
 */

public interface HistoryRestInterface {

    @GET("/{userId}/history/{id}.json")
    Call<HistoryModel> historyItem(@Path("userId") String userId ,@Path("id") String id);

    public static final Retrofit retrofit = new Retrofit.Builder()
            .baseUrl("https://tunerun-151319.firebaseio.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build();
}
