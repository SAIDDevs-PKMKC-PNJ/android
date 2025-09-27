package com.pkm.said.service

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import com.pkm.said.NewsResponse

interface NewsApiService {
    @GET("v2/everything")
    fun searchEverything(
        @Query("q") q: String? = null,                 // keyword
        @Query("from") from: String? = null,           // yyyy-MM-dd atau ISO
        @Query("to") to: String? = null,
        @Query("searchIn") searchIn: String? = null,
        @Query("domains") domains: String? = null,
        @Query("sortBy") sortBy: String? = "publishedAt", // relevancy|popularity|publishedAt
        @Query("language") language: String? = "en",   // "en","id",...
        @Query("page") page: Int? = 1,
        @Query("pageSize") pageSize: Int? = 20,
        @Query("apiKey") apiKey: String
    ): Call<NewsResponse>
}