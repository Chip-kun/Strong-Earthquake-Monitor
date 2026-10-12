package com.example.network

import com.example.model.EarthquakeItem
import com.example.model.PrefectureIntensity
import com.example.model.parseShindoScaleToText
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class P2PQuakeResponse(
    @Json(name = "id") val id: String? = null,
    @Json(name = "code") val code: Int? = null,
    @Json(name = "time") val time: String? = null,
    @Json(name = "earthquake") val earthquake: P2PEarthquakeInfo? = null,
    @Json(name = "points") val points: List<P2PPoint>? = null
)

@JsonClass(generateAdapter = true)
data class P2PEarthquakeInfo(
    @Json(name = "time") val time: String? = null,
    @Json(name = "hypocenter") val hypocenter: P2PHypocenter? = null,
    @Json(name = "maxScale") val maxScale: Int? = null,
    @Json(name = "domesticTsunami") val domesticTsunami: String? = null
)

@JsonClass(generateAdapter = true)
data class P2PHypocenter(
    @Json(name = "name") val name: String? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "depth") val depth: Int? = null,
    @Json(name = "magnitude") val magnitude: Double? = null
)

@JsonClass(generateAdapter = true)
data class P2PPoint(
    @Json(name = "pref") val pref: String? = null,
    @Json(name = "addr") val addr: String? = null,
    @Json(name = "scale") val scale: Int? = null
)

interface P2PQuakeService {
    @GET("v2/history")
    suspend fun getEarthquakeHistory(
        @Query("codes") codes: String = "551",
        @Query("limit") limit: Int = 30
    ): List<P2PQuakeResponse>
}

object NetworkClient {
    private const val BASE_URL = "https://api.p2pquake.net/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val p2pService: P2PQuakeService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(P2PQuakeService::class.java)
    }
}

fun P2PQuakeResponse.toEarthquakeItem(): EarthquakeItem? {
    val eq = earthquake ?: return null
    val hypo = eq.hypocenter ?: return null
    val rawScale = eq.maxScale ?: 10
    val scaleText = parseShindoScaleToText(rawScale)

    val tsunamiDesc = when (eq.domesticTsunami) {
        "Warning" -> "大津波警報・津波警報発表中"
        "Watch" -> "津波注意報発表中"
        "Checking" -> "津波の有無を調査中"
        "None" -> "津波の心配なし"
        else -> "津波の心配なし"
    }

    val pointList = points?.groupBy { it.pref ?: "" }
        ?.map { (pref, list) ->
            val maxPointScale = list.maxOfOrNull { it.scale ?: 0 } ?: 0
            val cityList = list.mapNotNull { it.addr }.distinct().take(6)
            PrefectureIntensity(
                prefecture = pref,
                scale = parseShindoScaleToText(maxPointScale),
                cities = cityList
            )
        } ?: emptyList()

    val detailedCityRegions = points?.filter { (it.scale ?: 0) >= rawScale - 10 }
        ?.mapNotNull { pt -> pt.addr ?: pt.pref }
        ?.distinct()
        ?.take(8) ?: emptyList()

    return EarthquakeItem(
        id = id ?: "${System.currentTimeMillis()}",
        time = eq.time ?: time ?: "不明",
        hypocenter = hypo.name ?: "不明",
        maxScale = scaleText,
        magnitude = hypo.magnitude ?: 0.0,
        depthKm = hypo.depth ?: 0,
        tsunamiInfo = tsunamiDesc,
        lat = hypo.latitude ?: 35.0,
        lon = hypo.longitude ?: 138.0,
        intensityPoints = pointList,
        detailedRegions = detailedCityRegions
    )
}
