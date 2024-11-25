package com.ygwk.project_2

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.ygwk.project_2.databinding.ActivityMainBinding
import com.ygwk.project_2.ui.theme.Project2Theme
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        lateinit var googleMap: GoogleMap
        //lateinit var mapView: MapView
        lateinit var mapStyleJson: String
    }

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        mapView = binding.map
//        mapView.onCreate(savedInstanceState)
//        mapView.getMapAsync(this@MainActivity)

        mapStyleJson = getString(R.string.map_style)

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().permitAll().build()
        )

        setContent {
            Project2Theme {
                MainApp()
            }
        }

        // 데이터 로드 및 마커 설정
        thread {
            for (routeNos in allRouteNos)
                for (routeNo in routeNos)
                    if (!routeIds.containsKey(routeNo))
                        applyRoute(routeNo)

            for (x in routeIds) {
                applyStationRoute(x.value)
                applyRealtimeRoute(x.key, x.value)
            }

            stations = ArrayList(stations.distinct())
            stations.sort()

            Log.d("map", routeIds.map { "${it.key}: ${it.value}" }.joinToString(", "))

            runOnUiThread {
                Log.d("runOnUiThread", "Attempting to add markers")
                routeIds["502"] = "502052"
                gpsDatas["502052"] = GPSData(36.62941167, 127.491185)
                addMarkers()
            }
        }
    }

//    override fun onStart() {
//        super.onStart()
//        mapView.onStart()
//    }
//
//    override fun onStop() {
//        super.onStop()
//        mapView.onStop()
//    }
//
//    override fun onResume() {
//        super.onResume()
//        mapView.onResume()
//    }
//
//    override fun onPause() {
//        super.onPause()
//        mapView.onPause()
//    }
//
//    override fun onLowMemory() {
//        super.onLowMemory()
//        mapView.onLowMemory()
//    }
//
//    override fun onDestroy() {
//        mapView.onDestroy()
//        super.onDestroy()
//    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = true
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                100
            )
        }

        try {
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)
            )
            if (!success) {
                Log.e("MapStyle", "Style parsing failed.")
            } else {
                Log.d("MapStyle", "Style applied successfully.")
            }
        } catch (e: Exception) {
            Log.e("MapStyle", "Can't find style. Error: \${e.message}")
        }

        // Call addMarkers if data is already available
        if (gpsDatas.isNotEmpty()) {
            addMarkers()
        }
    }


    private fun addMarkers() {
        Log.d("addMarkers", "Adding markers with gpsDatas: $gpsDatas")
        for ((routeId, gpsData) in gpsDatas) {
            val latlng = LatLng(gpsData.gpslati, gpsData.gpslong)
            val routeNo = routeIds.entries.find { it.value == routeId }?.key

            googleMap.addMarker(
                MarkerOptions()
                    .position(latlng)
                    .title(routeNo)
                    .snippet("${routeNo}번 버스")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )

            Log.d("wow", "${routeNo}: ${gpsData.gpslati}, ${gpsData.gpslong}")

            // Move camera to the first bus stop location
            gpsDatas.values.firstOrNull()?.let { firstGpsData ->
                val initialLatLng = LatLng(firstGpsData.gpslati, firstGpsData.gpslong)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, 15f))
            }
        }
    }
        

        @Composable
        fun MainApp() {
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = "map_screen") {
                composable("map_screen") {
                    MapScreen(navController)
                }
                composable("drawer") {
                    DrawerScreen(navController)
                }
                composable("favorites") {
                    SimpleScreen(title = "즐겨찾기 화면")
                }
                composable("bus_list") {
                    SimpleScreen(title = "버스 목록 화면")
                }
                composable("stations") {
                    StationScreen(navController)
                }
                composable("notifications") {
                    SimpleScreen(title = "공지사항 화면")
                }
                composable("settings") {
                    SimpleScreen(title = "설정 화면")
                }
            }
        }

    @Composable
    fun MapScreen(navController: NavHostController) {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle

        // MapView 초기화 및 생명 주기 관리
        val mapView = remember { MapView(context) }

        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> {}
                }
            }

            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("CBNU BUS") },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigate("drawer") }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    }
                )
            },
            content = {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        view.getMapAsync { googleMap ->
                            // GoogleMap 객체 초기화
                            MainActivity.googleMap = googleMap
                            googleMap.uiSettings.isZoomControlsEnabled = true

                            // 사용자 위치 활성화
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                googleMap.isMyLocationEnabled = true
                            }

                            // 스타일 적용
                            try {
                                val success = googleMap.setMapStyle(
                                    MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style)
                                )
                                if (!success) {
                                    Log.e("MapStyle", "Style parsing failed.")
                                }
                            } catch (e: Exception) {
                                Log.e("MapStyle", "Style loading failed: ${e.message}")
                            }

                            // 초기 위치 및 줌 설정
                            val initialLocation = LatLng(36.6284, 127.4561) // 충북대 예시 좌표
                            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLocation, 15f))
                        }
                    }
                )
            }
        )
    }


    @Composable
        fun DrawerScreen(navController: NavHostController) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("정류장 목록") },
                        navigationIcon = {
                            IconButton(onClick = { navController.navigate("drawer") }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        }
                    )
                },
                content = {
                    Column(modifier = Modifier.padding(16.dp)) {
                        MenuItem("즐겨찾기", R.drawable.ic_star) {
                            navController.navigate("favorites")
                        }
                        MenuItem("버스 목록", R.drawable.ic_bus) {
                            navController.navigate("bus_list")
                        }
                        MenuItem("정류장 목록", R.drawable.ic_station) {
                            navController.navigate("stations")
                        }
                        MenuItem("공지사항", R.drawable.ic_notifications) {
                            navController.navigate("notifications")
                        }
                        MenuItem("설정", R.drawable.ic_settings) {
                            navController.navigate("settings")
                        }
                    }
                }
            )
        }

    @Composable
    fun StationScreen(navController: NavHostController) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("CBNU BUS") },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigate("map_screen") }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    }
                )
            },
            content = {
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    itemsIndexed(stations) { index, station ->
                        MenuItem(station, R.drawable.ic_station) {
                            //위치
                        }
                    }
                }
            }
        )
    }

        @Composable
        fun MenuItem(text: String, iconRes: Int, onClick: () -> Unit) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onClick() },
                horizontalArrangement = Arrangement.Start
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = text,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 16.dp)
                )
                Text(text, fontSize = 18.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        @Composable
        fun SimpleScreen(title: String) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) }
                    )
                },
                content = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = title, fontSize = 24.sp)
                    }
                }
            )
        }

        fun applyRoute(routeNo: String) {
            try {
                val postData = mapOf(
                    "serviceKey" to SERVICE_KEY,
                    "pageNo" to "1",
                    "numOfRows" to "100",
                    "_type" to "json",
                    "cityCode" to CITY_CODE,
                    "routeNo" to routeNo
                ).map { "${URLEncoder.encode(it.key, StandardCharsets.UTF_8.name())}=${it.value}" }
                    .joinToString("&")
                val json_str = fetchRouteData(
                    "http://apis.data.go.kr/1613000/BusRouteInfoInqireService/getRouteNoList",
                    postData
                )

                val json = JSONObject(json_str)
                val respo = json.optJSONObject("response") ?: throw Exception("Missing 'response'")
                val body = respo.optJSONObject("body") ?: throw Exception("Missing 'body'")
                val items = body.optJSONObject("items") ?: throw Exception("Missing 'items'")
                val bus = items.optJSONObject("item") ?: throw Exception("Missing 'item'")
                val routeId = bus.optString("routeid", "Unknown")


                routeIds[routeNo] = routeId
            } catch (e: Exception) {
                Log.e("Route23", "Failed to process routeNo $routeNo: ${e.message}")
            }
        }

        fun applyRealtimeRoute(routeNo: String, routeId: String) {
            try {
                val postData = mapOf(
                    "serviceKey" to SERVICE_KEY,
                    "pageNo" to "1",
                    "numOfRows" to "100",
                    "_type" to "json",
                    "cityCode" to CITY_CODE,
                    "routeId" to routeId
                ).map { "${URLEncoder.encode(it.key, "UTF-8")}=${it.value}" }
                    .joinToString("&")
                val json_str = fetchRouteData(
                    "https://apis.data.go.kr/1613000/BusLcInfoInqireService/getRouteAcctoBusLcList",
                    postData
                )

                val json = JSONObject(json_str)
                val respo = json.optJSONObject("response") ?: throw Exception("Missing 'response'")
                val body = respo.optJSONObject("body") ?: throw Exception("Missing 'body'")
                val items = body.optJSONObject("items") ?: return
                val bus = items.optJSONArray("item") ?: throw Exception("Missing 'item'")

                for (i in 0 until bus.length()) {
                    val route: JSONObject = bus.getJSONObject(i)
                    val gpslati = route.getString("gpslati")
                    val gpslong = route.getString("gpslong")

                    gpsDatas[routeId] = GPSData(gpslati.toDouble(), gpslong.toDouble())
                }
            } catch (e: Exception) {
                Log.e(
                    "Route23RT Error",
                    "Failed to process routeNo $routeNo: ${e.message}"
                )
            }
        }

        fun applyStationRoute(routeId: String) {
            try {
                val postData = mapOf(
                    "serviceKey" to SERVICE_KEY,
                    "pageNo" to "1",
                    "numOfRows" to "100",
                    "_type" to "json",
                    "cityCode" to CITY_CODE,
                    "routeId" to routeId
                ).map { "${URLEncoder.encode(it.key, StandardCharsets.UTF_8.name())}=${it.value}" }
                    .joinToString("&")
                val json_str = fetchRouteData(
                    "https://apis.data.go.kr/1613000/BusRouteInfoInqireService/getRouteAcctoThrghSttnList",
                    postData
                )

                val json = JSONObject(json_str)
                val respo = json.optJSONObject("response") ?: throw Exception("Missing 'response'")
                val body = respo.optJSONObject("body") ?: throw Exception("Missing 'body'")
                val items = body.optJSONObject("items") ?: return
                val bus = items.optJSONArray("item") ?: return

                for (i in 0 until bus.length()) {
                    val route: JSONObject = bus.getJSONObject(i)
                    val station = route.optString("nodenm", "Unknown")

                    stations.add(station)
                }

            } catch (e: Exception) {
                Log.e("Route23ST", "Failed to process routeId $routeId: ${e.message}")
            }
        }

        fun fetchRouteData(baseUrl: String, postData: String): String {
            val url = URL("$baseUrl?$postData")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0"
            )

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream =
                    BufferedReader(InputStreamReader(conn.errorStream)).use { it.readText() }
                Log.e("API Error", "Code: ${conn.responseCode}, Message: $errorStream")
                throw Exception("Failed to fetch data. Code: ${conn.responseCode}")
            }

            val response = StringBuilder()
            BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
            }

            return response.toString()
        }

        @Preview(showBackground = true)
        @Composable
        fun AppPreview() {
            Project2Theme {
                MainApp()
            }
        }
    }

    // 충북대학교 버스 정류장 데이터
    private var entrance1 = arrayOf(
        "20-1",
        "40-2",
        "101",
        "105",
        "114",
        "311",
        "502",
        "511",
        "513",
        "514",
        "516",
        "611",
        "618",
        "711",
        "811-1",
        "814",
        "823",
        "833",
        "911",
        "916-2",
        "917"
    )
    private var entrance2 = arrayOf(
        "20-2",
        "101",
        "105",
        "114",
        "311",
        "502",
        "511",
        "513",
        "514",
        "516",
        "611",
        "618",
        "710",
        "711",
        "811-2",
        "812",
        "831",
        "911",
        "916-1",
        "917"
    )

    private var jeongmoon = arrayOf("814", "823")
    private var jugong1 = arrayOf("20-1")
    private var jugong2 = arrayOf("20-2")
    private var jungmoon1 = arrayOf("20-1", "30-2", "823", "851")
    private var jungmoon2 = arrayOf("30-1", "851")
    private var humoon1 = arrayOf("20-1", "30-2", "823", "851")
    private var humoon2 = arrayOf("20-2", "30-1", "823", "851")
    private var hospital1 = arrayOf("710", "823", "844", "851")
    private var hospital2 = arrayOf("20-2", "30-1", "823", "851")
    private var hospital3 = arrayOf("20-1", "416", "512", "811-1")
    private var chungdae = arrayOf("710")

    private var allRouteNos = arrayOf(
        entrance1,
        entrance2,
        jeongmoon,
        jugong1,
        jugong2,
        jungmoon1,
        jungmoon2,
        humoon1,
        humoon2,
        hospital1,
        hospital2,
        hospital3,
        chungdae
    )
    private var routeIds = HashMap<String, String>()
    private var gpsDatas = HashMap<String, GPSData>() // key: routeId
    private var stations = ArrayList<String>()

    private val CITY_CODE = 33010
    private val SERVICE_KEY =
        "kvossTUQ%2BPOKNlfHCm9pZVGBQsYdTMc%2FoPeOtMrbxz9%2F3gouVW2liVULC4S26OU7VCo%2FulJN0NOb7WsVWu40Lg%3D%3D"