package com.ygwk.project_2

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.ui.graphics.Color
import androidx.compose.material.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.location.Location
import android.widget.Toast

import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val Shapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(0.dp)
)

private val Typography = Typography(
    defaultFontFamily = FontFamily.Default
)

private val DarkColorPalette = darkColors(
    primary = Color(0xFF1EB980),
    primaryVariant = Color(0xFF045D56),
    secondary = Color(0xFF03DAC5)
)

private val LightColorPalette = lightColors(
    primary = Color(0xFF6200EE),
    primaryVariant = Color(0xFF3700B3),
    secondary = Color(0xFF03DAC5)
)

private const val PREFS_NAME = "user_prefs"
private const val DARK_MODE_KEY = "dark_mode"

fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0) // 기존 프로세스 종료
}

fun saveDarkModePreference(context: Context, isDarkMode: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(DARK_MODE_KEY, isDarkMode).apply()
}

fun loadDarkModePreference(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(DARK_MODE_KEY, false) // 기본값은 false (라이트 모드)
}

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    companion object {
        var googleMap: GoogleMap? = null
        //lateinit var mapView: MapView
        lateinit var mapStyleJson: String
        lateinit var fusedLocationClient: FusedLocationProviderClient
        lateinit var current: Context
    }

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        current = this
        supportActionBar?.hide()

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
                    if (!routeIds.containsKey(routeNo)) {
                        applyRoute(routeNo)
                        allRouteNos2.add(routeNo)
                    }

            for (x in routeIds) {
                applyStationRoute(x.value)
            }

            stations = ArrayList(stations.distinctBy({ T -> T.name}))
            stations.sortBy({ T -> T.name})
            allRouteNos2.sort()

            Log.d("map", routeIds.map { "${it.key}: ${it.value}" }.joinToString(", "))

            runOnUiThread {
                Log.d("runOnUiThread", "Attempting to add markers")
//                routeIds["502"] = "502052"
//                gpsDatas["502052"] = GPSData(36.62941167, 127.491185)
                addMarkers()
            }
        }
        lifecycleScope.launch {
            while (true) {
                synchronized(routeIds) {
                    for (x in routeIds) {
                        applyRealtimeRoute(x.key, x.value)
                    }
                }
                runOnUiThread {
                    Log.d("runOnUiThread", "Attempting to add markers")
//                routeIds["502"] = "502052"
//                gpsDatas["502052"] = GPSData(36.62941167, 127.491185)
                    if (googleMap != null) googleMap?.clear()
                    addMarkers()
                }

                val latLng = getMyLocation()

                if (latLng != null) {
                    applyStarRoute(latLng.latitude, latLng.longitude)
                }

                delay(10000)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1000) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    googleMap?.isMyLocationEnabled = true
                }
            }
        }
    }

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
            googleMap?.isMyLocationEnabled = true
            googleMap?.uiSettings!!.isMyLocationButtonEnabled = true
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
            val success = googleMap?.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)
            )
            if (!(success!!)) {
                Log.e("MapStyle", "Style parsing failed.")
            } else {
                Log.d("MapStyle", "Style applied successfully.")
            }
        } catch (e: Exception) {
            Log.e("MapStyle", "Can't find style. Error: \${e.message}")
        }

        for (station in stations) {
            val latlng = LatLng(station.latLng.gpslati, station.latLng.gpslong)

            googleMap?.addMarker(
                MarkerOptions()
                    .position(latlng)
                    .title(station.name)
                    .snippet("정류장")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )
        }
    }


    private fun addMarkers() {
        Log.d("addMarkers", "Adding markers with gpsDatas: $gpsDatas")
        for ((routeId, gpsData) in gpsDatas) {
            val latlng = LatLng(gpsData.gpslati, gpsData.gpslong)
            val routeNo = routeIds.entries.find { it.value == routeId }?.key

            googleMap?.addMarker(
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
                //googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, 15f))
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
                StarScreen(navController)
            }
            composable("bus_list") {
                BusItemScreen(navController)
            }
            composable("stations") {
                StationScreen(navController)
            }
            composable("notifications") {
                SimpleScreen(title = "공지사항", isSettings = false, "1.1 업데이트 안내: 지도 맵 구현\n\n1.1 업데이트!: 버스 실시간 위치 추적 및 UI 개선\n\n2.0 업데이트 (최신버전): 근처 버스 도착 토스트 알림 기능 추가")
            }
            composable("settings") {
                SimpleScreen(title = "설정", isSettings = true)
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
                    title = { Text("충대버스: CBNU BUS") },
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
                            googleMap?.uiSettings?.isZoomControlsEnabled = true

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
                                googleMap?.isMyLocationEnabled = true
                            }

                            // 스타일 적용
                            try {
                                val success = googleMap?.setMapStyle(
                                    MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style)
                                )
                                if (!(success!!)) {
                                    Log.e("MapStyle", "Style parsing failed.")
                                }
                            } catch (e: Exception) {
                                Log.e("MapStyle", "Style loading failed: ${e.message}")
                            }

                            // 초기 위치 및 줌 설정
                            val initialLocation = LatLng(36.6284, 127.4561) // 충북대 예시 좌표
                            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLocation, 14f))
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
                    title = { Text("목록") },
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
                    title = { Text("정류장 목록") },
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
                        MenuItem(station.name, R.drawable.ic_station) {
                            navController.navigate("map_screen")
                            runOnUiThread {
                                moveToStation(applicationContext, station.name)
                            }
                        }
                    }
                }
            }
        )
    }

    @Composable
    fun BusItemScreen(navController: NavHostController) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("버스 목록") },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigate("map_screen") }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    }
                )
            },
            content = {
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    itemsIndexed(allRouteNos2) { index, station ->
                        MenuItem(station, R.drawable.ic_bus) {
                            if (!stars.contains(station)) {
                                stars.add(station)
                                Toast.makeText(this@MainActivity, "${station}번 버스가 즐겨찾기로 설정되었습니다.", Toast.LENGTH_SHORT).show()
                            }
                            else {
                                stars.remove(station)
                                Toast.makeText(this@MainActivity, "${station}번 버스의 즐겨찾기가 해제되었습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        )
    }

    @Composable
    fun StarScreen(navController: NavHostController) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("즐겨찾기") },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigate("map_screen") }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    }
                )
            },
            content = {
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    itemsIndexed(stars) { index, station ->
                        MenuItem(station, R.drawable.ic_bus) {
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
                .padding(vertical = 12.dp)
                .clickable { onClick() },
            horizontalArrangement = Arrangement.Start
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = text,
                modifier = Modifier
                    .size(48.dp)
                    .padding(end = 16.dp)
            )
            Text(text, fontSize = 18.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
    }

    @Composable
    fun SimpleScreen(title: String, isSettings: Boolean, content: String = "") {
        val context = LocalContext.current
        var isDarkMode by remember { mutableStateOf(loadDarkModePreference(context)) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) }
                )
            },
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = content, fontSize = 24.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isSettings) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(text = "다크 모드", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { checked ->
                                    isDarkMode = checked
                                    saveDarkModePreference(context, checked)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // 재부팅 버튼
                        Button(onClick = { restartApp(context) }) {
                            Text(text = "앱 재부팅")
                        }
                    }
                }
            }
        )
    }

    fun moveToStation(context: Context, stationName: String) {
        thread {
            try {
                for (station in stations) {
                    if (station.name != stationName) continue

                        val latLng = LatLng(station.latLng.gpslati, station.latLng.gpslong)
                        MainActivity.googleMap?.addMarker(
                            MarkerOptions()
                                .position(latLng)
                                .title(stationName)
                                .snippet("정류장 위치")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                        )
                        MainActivity.googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
            } catch (e: Exception) {
                Log.e("StationMoveError", "Failed to move to station: ${e.message}")
            }
        }
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
                val gpslati = route.optString("gpslati", "Unknown")
                val gpslong = route.optString("gpslong", "Unknown")

                stations.add(StationClass(station, GPSData(gpslati.toDouble(), gpslong.toDouble())))
            }

        } catch (e: Exception) {
            Log.e("Route23ST", "Failed to process routeId $routeId: ${e.message}")
        }
    }

    fun applyStarRoute(gpslati: Double, gpslong: Double) {
        var star2 = ArrayList<String>()

        try {
            val postData = mapOf(
                "serviceKey" to SERVICE_KEY,
                "pageNo" to "1",
                "numOfRows" to "10",
                "_type" to "json",
                "gpslati" to gpslati.toString(),
                "gpslong" to gpslong.toString()
            ).map { "${URLEncoder.encode(it.key, StandardCharsets.UTF_8.name())}=${it.value}" }
                .joinToString("&")
            val json_str = fetchRouteData("https://apis.data.go.kr/1613000/BusSttnInfoInqireService/getCrdntPrxmtSttnList",
                postData)

            val json = JSONObject(json_str)
            val respo = json.optJSONObject("response") ?: throw Exception("Missing 'response'")
            val body = respo.optJSONObject("body") ?: throw Exception("Missing 'body'")
            val items = body.optJSONObject("items") ?: return
            val bus = items.optJSONArray("item") ?: return

            for (i in 0 until bus.length()) {
                val route: JSONObject = bus.getJSONObject(i)
                val station = route.optString("nodeid", "Unknown")

                for (star in stars) {
                    var routeId = routeIds[star]

                    val postData2 = mapOf(
                        "serviceKey" to SERVICE_KEY,
                        "pageNo" to "1",
                        "numOfRows" to "10",
                        "_type" to "json",
                        "nodeId" to station,
                        "routeId" to routeId
                    ).map { "${URLEncoder.encode(it.key, StandardCharsets.UTF_8.name())}=${it.value}" }
                        .joinToString("&")
                    val json_str2 = fetchRouteData(
                        "https://apis.data.go.kr/1613000/ArvlInfoInqireService/getSttnAcctoSpcifyRouteBusArvlPrearngeInfoList",
                        postData2
                    )

                    val json2 = JSONObject(json_str2)
                    val respo2 =
                        json2.optJSONObject("response") ?: throw Exception("Missing 'response'")
                    val body2 = respo2.optJSONObject("body") ?: throw Exception("Missing 'body'")
                    val items2 = body2.optJSONObject("items") ?: return
                    val bus2 = items2.optJSONObject("item") ?: throw Exception("Missing 'item'")
                    val arrTime = bus2.optString("arrtime", "Unknown")

                    if (arrTime != "Unknown") {
                        var toArr = arrTime.toInt() / 60

                        if (toArr <= 5)
                            star2.add("${star}번 버스가 ${toArr}분 뒤 도착 예정입니다.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Route23STAR", "Failed to process: ${e.message}")
        }

        for (star in star2) {
            Log.d("STAR", star)
            runOnUiThread {
                Toast.makeText(this@MainActivity, star, Toast.LENGTH_SHORT).show()
            }
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

    fun getMyLocation(): LatLng? {
        var cur: LatLng? = null

        if (ActivityCompat.checkSelfPermission(
                MainActivity.current,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                MainActivity.current,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        MainActivity.fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                cur = LatLng(it.latitude, it.longitude)
            }
        }

        return cur
    }

    @Preview(showBackground = true)
    @Composable
    fun Project2Theme(content: @Composable () -> Unit) {
        val context = LocalContext.current
        val isDarkMode = remember { loadDarkModePreference(context) }

        MaterialTheme(
            colors = if (isDarkMode) DarkColorPalette else LightColorPalette,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
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
private var allRouteNos2 = ArrayList<String>()
private var routeIds = ConcurrentHashMap<String, String>()
private var gpsDatas = ConcurrentHashMap<String, GPSData>() // key: routeId
private var stations = ArrayList<StationClass>()
private var stations_id = ConcurrentHashMap<String, String>() //key: nodenm
private var stars = ArrayList<String>() //routeNo

private val CITY_CODE = 33010
private val SERVICE_KEY =
    "kvossTUQ%2BPOKNlfHCm9pZVGBQsYdTMc%2FoPeOtMrbxz9%2F3gouVW2liVULC4S26OU7VCo%2FulJN0NOb7WsVWu40Lg%3D%3D"