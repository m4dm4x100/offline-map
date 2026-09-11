package com.m4dm4x100.indiaofflinemap

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), LocationListener {
    private lateinit var mapView: MapView
    private lateinit var locationManager: LocationManager
    private lateinit var status: TextView
    private var map: MapLibreMap? = null
    private var locationSource: GeoJsonSource? = null
    private var firstFix = true
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val indiaCenter = LatLng(22.5, 79.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        val root = FrameLayout(this)
        mapView = MapView(this)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))

        status = TextView(this).apply {
            text = "  OFFLINE • PREPARING MAP…  "
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(220, 16, 24, 32))
            textSize = 12f
            setPadding(16, 10, 16, 10)
        }
        root.addView(status, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 28
        })
        setContentView(root)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.setCameraPosition(
                CameraPosition.Builder().target(indiaCenter).zoom(4.2).build()
            )
            prepareMapInBackground()
        }
    }

    private fun prepareMapInBackground() {
        status.text = "  OFFLINE • LOADING LOCAL MAP…  "
        ioExecutor.execute {
            try {
                val mapFile = copyBundledMapIfNeeded()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    try {
                        map?.setStyle(buildStyle(mapFile)) { style ->
                            addGpsLayer(style)
                            startGps()
                        }
                    } catch (t: Throwable) {
                        showStartupError("Map renderer failed: ${t.message ?: "unknown error"}")
                    }
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        showStartupError("Offline map could not be prepared: ${t.message ?: "unknown error"}")
                    }
                }
            }
        }
    }

    private fun copyBundledMapIfNeeded(): File {
        val target = File(filesDir, "india.pmtiles")
        val parts = assets.list("")?.filter { it.startsWith("india.pmtiles.part") }?.sorted()
            ?: emptyList()
        require(parts.isNotEmpty()) { "Bundled India map data is missing" }

        val expectedSize = parts.sumOf { part ->
            assets.open(part).use { stream -> stream.available().toLong() }
        }
        if (target.exists() && target.length() == expectedSize) return target

        val temp = File(filesDir, "india.pmtiles.tmp")
        if (temp.exists()) temp.delete()

        try {
            temp.outputStream().buffered().use { output ->
                parts.forEachIndexed { index, part ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            status.text = "  OFFLINE • COPYING MAP ${index + 1}/${parts.size}…  "
                        }
                    }
                    assets.open(part).buffered().use { input ->
                        input.copyTo(output, 1024 * 1024)
                    }
                }
            }
            require(temp.length() == expectedSize) {
                "Map copy incomplete (${temp.length()} / $expectedSize bytes)"
            }
            if (target.exists()) target.delete()
            require(temp.renameTo(target)) { "Could not finalize offline map" }
            return target
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    private fun buildStyle(mapFile: File): String {
        val escaped = mapFile.absolutePath.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
        {
          "version": 8,
          "sources": {
            "india": {"type":"vector","url":"pmtiles://file://$escaped"}
          },
          "layers": [
            {"id":"background","type":"background","paint":{"background-color":"#e8edf1"}},
            {"id":"water","type":"fill","source":"india","source-layer":"water","paint":{"fill-color":"#b9d9ee"}},
            {"id":"landuse","type":"fill","source":"india","source-layer":"landuse","paint":{"fill-color":"#dfe8d9","fill-opacity":0.65}},
            {"id":"roads","type":"line","source":"india","source-layer":"transportation","paint":{"line-color":"#f8f8f8","line-width":["interpolate",["linear"],["zoom"],4,0.4,8,1.2,12,3.2,15,7]}},
            {"id":"roads-major","type":"line","source":"india","source-layer":"transportation","filter":["in","class","motorway","trunk","primary","secondary"],"paint":{"line-color":"#e49a4d","line-width":["interpolate",["linear"],["zoom"],4,0.8,8,2.0,12,4.0,15,8]}},
            {"id":"waterway","type":"line","source":"india","source-layer":"waterway","paint":{"line-color":"#8bbfdf","line-width":["interpolate",["linear"],["zoom"],5,0.5,12,2]}},
            {"id":"buildings","type":"fill","source":"india","source-layer":"building","minzoom":13,"paint":{"fill-color":"#d8d2c9","fill-opacity":0.75}}
          ]
        }
        """.trimIndent()
    }

    private fun addGpsLayer(style: Style) {
        locationSource = GeoJsonSource(
            "user-location",
            Feature.fromGeometry(Point.fromLngLat(indiaCenter.longitude, indiaCenter.latitude))
        )
        style.addSource(locationSource!!)
        style.addLayer(
            CircleLayer("user-location-layer", "user-location").withProperties(
                circleColor("#2563EB"),
                circleRadius(8f),
                circleOpacity(0.95f)
            )
        )
    }

    private fun startGps() {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 42)
            status.text = "  OFFLINE • GRANT GPS ACCESS  "
            return
        }
        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                status.text = "  OFFLINE • GPS IS OFF  "
                return
            }
            status.text = "  OFFLINE • WAITING FOR GPS  "
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this, mainLooper)
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onLocationChanged(it) }
        } catch (t: SecurityException) {
            status.text = "  OFFLINE • GPS PERMISSION REQUIRED  "
        } catch (t: RuntimeException) {
            status.text = "  OFFLINE • GPS UNAVAILABLE  "
        }
    }

    private fun showStartupError(message: String) {
        status.text = "  OFFLINE • MAP ERROR  "
        android.util.Log.e("IndiaOfflineMap", message)
    }

    override fun onLocationChanged(location: Location) {
        locationSource?.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude))
        )
        status.text = "  OFFLINE • GPS ${"%.5f".format(location.latitude)}, ${"%.5f".format(location.longitude)}  "
        if (firstFix) {
            firstFix = false
            map?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15.0),
                800
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startGps()
    }

    override fun onDestroy() {
        if (::locationManager.isInitialized) {
            try { locationManager.removeUpdates(this) } catch (_: Exception) { }
        }
        ioExecutor.shutdownNow()
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
