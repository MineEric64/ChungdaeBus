package com.ygwk.project_2

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.LatLng

class GoogleMapWrapper(
    private val context: Context,
    private val initialLocation: LatLng,
    private val zoomLevel: Float
) {
    @Composable
    fun MapView(modifier: Modifier = Modifier) {
        // 카메라 상태 초기화
        val cameraPositionState = rememberCameraPositionState {
            position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
                initialLocation, zoomLevel
            )
        }

        // GoogleMap 구성
        GoogleMap(
            modifier = modifier,
            cameraPositionState = cameraPositionState
        )
    }
}