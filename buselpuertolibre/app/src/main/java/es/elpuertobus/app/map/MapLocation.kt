package es.elpuertobus.app.map

import android.content.Context
import android.location.Location
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

object MapLocation {
    fun setEnabled(
        map: MapLibreMap,
        style: Style,
        context: Context,
        enabled: Boolean,
        moveCamera: Boolean = false,
    ) {
        val component = map.locationComponent
        if (!enabled) {
            if (component.isLocationComponentActivated) {
                component.isLocationComponentEnabled = false
            }
            return
        }
        if (!component.isLocationComponentActivated) {
            component.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style)
                    .useDefaultLocationEngine(true)
                    .build(),
            )
        }
        component.renderMode = RenderMode.NORMAL
        component.isLocationComponentEnabled = true
        if (moveCamera) {
            recenter(map, component.lastKnownLocation)
        }
    }

    private fun recenter(map: MapLibreMap, location: Location?) {
        if (location == null) return
        map.animateCamera(
            org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                org.maplibre.android.geometry.LatLng(location.latitude, location.longitude),
                15.0,
            ),
        )
    }
}
