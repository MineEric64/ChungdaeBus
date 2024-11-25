package com.ygwk.project_2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.ygwk.project_2.ui.theme.Project2Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Project2Theme {
                MainApp()
            }
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
        composable("schedule") {
            SimpleScreen(title = "버스 시간표 화면")
        }
        composable("bus_list") {
            SimpleScreen(title = "버스 목록 화면")
        }
        composable("stations") {
            SimpleScreen(title = "정류장 목록 화면")
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
            Box(modifier = Modifier.fillMaxSize()) {
                // Google Maps
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(LatLng(36.6285, 127.4561), 15f)
                }

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState
                )
            }
        }
    )
}

@Composable
fun DrawerScreen(navController: NavHostController) {
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
            Column(modifier = Modifier.padding(16.dp)) {
                // 햄버거 메뉴 항목
                MenuItem("즐겨찾기", R.drawable.ic_star) {
                    navController.navigate("favorites")
                }
                MenuItem("버스 시간표", R.drawable.ic_schedule) {
                    navController.navigate("schedule")
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

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    Project2Theme {
        MainApp()
    }
}