package com.guardianlink.app.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guardianlink.app.R
import com.guardianlink.app.data.GeofenceWktHelper
import com.guardianlink.app.model.GeofenceZone
import com.guardianlink.app.ui.theme.*
import com.guardianlink.app.viewmodel.DeviceViewModel
import com.guardianlink.app.viewmodel.DrawingShape
import com.guardianlink.app.viewmodel.GeofenceViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import android.graphics.Point
import android.view.MotionEvent
import kotlin.math.abs

// High-Resolution Satellite & Aerial Tile Source
private val GoogleSatelliteHybridTileSource = object : OnlineTileSourceBase(
    "GoogleHybrid",
    0, 20, 256, "",
    arrayOf(
        "https://mt0.google.com/vt/lyrs=y&",
        "https://mt1.google.com/vt/lyrs=y&",
        "https://mt2.google.com/vt/lyrs=y&",
        "https://mt3.google.com/vt/lyrs=y&"
    ),
    "© Google Maps Imagery"
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "${getBaseUrl()}x=$x&y=$y&z=$z"
    }
}

// High-Resolution Modern CartoDB Map Tile Sources
private val CartoVoyagerTileSource = XYTileSource(
    "CartoVoyager",
    0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
    ),
    "© OpenStreetMap contributors, © CARTO"
)

private val CartoDarkTileSource = XYTileSource(
    "CartoDark",
    0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/"
    ),
    "© OpenStreetMap contributors, © CARTO"
)

enum class MapStyle(val title: String) {
    STANDARD("Classic OpenStreetMap"),
    SATELLITE("Satellite (Aerial & Road Labels)"),
    TERRAIN("Topographic Terrain"),
    VOYAGER("Voyager (CartoDB)"),
    DARK("Dark Matter (CartoDB)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onNavigateBack: () -> Unit = {},
    deviceViewModel: DeviceViewModel = viewModel(),
    geofenceViewModel: GeofenceViewModel = viewModel()
) {
    val status by deviceViewModel.status.collectAsState()
    val context = LocalContext.current

    var selectedStyle by remember { mutableStateOf(MapStyle.STANDARD) }
    var showLayerMenu by remember { mutableStateOf(false) }
    var showGeofenceSheet by remember { mutableStateOf(false) }
    var showShapeSelectorDialog by remember { mutableStateOf(false) }
    var showSaveZoneDialog by remember { mutableStateOf(false) }
    var newZoneName by remember { mutableStateOf("") }

    // Initialize osmdroid user agent & cache
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var deviceMarkerRef by remember { mutableStateOf<Marker?>(null) }

    fun getTileSource(style: MapStyle) = when (style) {
        MapStyle.STANDARD -> TileSourceFactory.MAPNIK
        MapStyle.SATELLITE -> GoogleSatelliteHybridTileSource
        MapStyle.TERRAIN -> TileSourceFactory.OpenTopo
        MapStyle.VOYAGER -> CartoVoyagerTileSource
        MapStyle.DARK -> CartoDarkTileSource
    }

    // References for dynamic in-flight drag updates
    var draftPolygonRef: Polygon? = null
    var draftPolylineRef: Polyline? = null
    val draftVertexMarkers = mutableListOf<Marker>()
    var draftCenterMarker: Marker? = null

    // Function to dynamically update preview geometry during active user drag
    fun refreshDraftGeometry(mapView: MapView) {
        val currentDraft = geofenceViewModel.draftPoints
        val shape = geofenceViewModel.selectedDrawingShape
        when (shape) {
            DrawingShape.CIRCLE -> {
                if (currentDraft.isNotEmpty()) {
                    val center = currentDraft.first()
                    val circlePts = GeofenceWktHelper.createCirclePoints(center, geofenceViewModel.circleRadiusMeters)
                    draftPolygonRef?.points = circlePts
                }
            }
            DrawingShape.RECTANGLE -> {
                if (currentDraft.size >= 2) {
                    val rectPts = GeofenceWktHelper.createRectanglePoints(currentDraft[0], currentDraft[1])
                    draftPolygonRef?.points = rectPts
                }
            }
            DrawingShape.TRIANGLE -> {
                if (currentDraft.size >= 3) {
                    val triPts = GeofenceWktHelper.createTrianglePoints(currentDraft[0], currentDraft[1], currentDraft[2])
                    draftPolygonRef?.points = triPts
                } else if (currentDraft.size == 2) {
                    draftPolylineRef?.setPoints(currentDraft.toList())
                }
            }
            DrawingShape.CUSTOM -> {
                if (currentDraft.size >= 3) {
                    val closedPts = currentDraft.toList() + currentDraft.first()
                    draftPolygonRef?.points = closedPts
                } else if (currentDraft.size == 2) {
                    draftPolylineRef?.setPoints(currentDraft.toList())
                }
            }
            null -> {}
        }
        for (i in draftVertexMarkers.indices) {
            if (i < currentDraft.size) {
                draftVertexMarkers[i].position = currentDraft[i]
            }
        }
        val c = geofenceViewModel.getDraftCenter()
        if (c != null) {
            draftCenterMarker?.position = c
        }
        mapView.invalidate()
    }

    // Function to rebuild and render all geofence polygons and draft shapes on map
    fun updateMapOverlays(mapView: MapView) {
        // Remove old polygons & polylines & draft markers, keep base overlays
        mapView.overlays.removeAll { it is Polygon || it is Polyline || (it is Marker && it != deviceMarkerRef) }
        draftVertexMarkers.clear()
        draftPolygonRef = null
        draftPolylineRef = null
        draftCenterMarker = null

        // 1. Render all saved active safe zones (hide currently edited zone if any)
        for (zone in geofenceViewModel.geofences) {
            if (geofenceViewModel.isDrawingMode && geofenceViewModel.editingZone?.id == zone.id) {
                continue // Suppress rendering static zone while actively editing it
            }

            val points = if (!zone.polygonWkt.isNullOrBlank()) {
                GeofenceWktHelper.fromPolygonWkt(zone.polygonWkt)
            } else {
                val center = GeoPoint(zone.centerLatitude, zone.centerLongitude)
                GeofenceWktHelper.createCirclePoints(center, zone.radiusMeters)
            }

            if (points.size >= 3) {
                val polygon = Polygon(mapView).apply {
                    this.points = points
                    fillPaint.color = android.graphics.Color.argb(55, 34, 197, 94) // 22% Translucent Safety Green
                    outlinePaint.color = android.graphics.Color.rgb(22, 163, 74)   // Vibrant Green Border
                    outlinePaint.strokeWidth = 5f
                    title = zone.name
                    snippet = "${zone.shapeType} Safe Zone"
                }
                mapView.overlays.add(polygon)

                // Add center label marker
                val centerGeo = GeoPoint(zone.centerLatitude, zone.centerLongitude)
                val labelMarker = Marker(mapView).apply {
                    position = centerGeo
                    title = zone.name
                    snippet = "Safe Zone (${zone.shapeType})"
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_zone_shield_pin)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                mapView.overlays.add(labelMarker)
            }
        }

        // 2. Render real-time draft shape while in drawing/edit mode
        if (geofenceViewModel.isDrawingMode) {
            val draft = geofenceViewModel.draftPoints
            val shape = geofenceViewModel.selectedDrawingShape
            val dragHandleDrawable = ContextCompat.getDrawable(context, R.drawable.ic_drag_handle)
            val moveAreaDrawable = ContextCompat.getDrawable(context, R.drawable.ic_move_area)

            // Create and add shape preview polygon or polyline first (so markers appear on top)
            when (shape) {
                DrawingShape.CIRCLE -> {
                    if (draft.isNotEmpty()) {
                        val center = draft.first()
                        val circlePts = GeofenceWktHelper.createCirclePoints(center, geofenceViewModel.circleRadiusMeters)
                        val circlePoly = Polygon(mapView).apply {
                            this.points = circlePts
                            fillPaint.color = android.graphics.Color.argb(60, 37, 99, 235) // Translucent Blue
                            outlinePaint.color = android.graphics.Color.rgb(37, 99, 235)
                            outlinePaint.strokeWidth = 5f
                            title = "Preview Circle (${geofenceViewModel.circleRadiusMeters.toInt()}m)"
                        }
                        draftPolygonRef = circlePoly
                        mapView.overlays.add(circlePoly)
                    }
                }
                DrawingShape.RECTANGLE -> {
                    if (draft.size >= 2) {
                        val rectPts = GeofenceWktHelper.createRectanglePoints(draft[0], draft[1])
                        val rectPoly = Polygon(mapView).apply {
                            this.points = rectPts
                            fillPaint.color = android.graphics.Color.argb(60, 37, 99, 235)
                            outlinePaint.color = android.graphics.Color.rgb(37, 99, 235)
                            outlinePaint.strokeWidth = 5f
                            title = "Preview Rectangle"
                        }
                        draftPolygonRef = rectPoly
                        mapView.overlays.add(rectPoly)
                    }
                }
                DrawingShape.TRIANGLE -> {
                    if (draft.size >= 3) {
                        val triPts = GeofenceWktHelper.createTrianglePoints(draft[0], draft[1], draft[2])
                        val triPoly = Polygon(mapView).apply {
                            this.points = triPts
                            fillPaint.color = android.graphics.Color.argb(60, 37, 99, 235)
                            outlinePaint.color = android.graphics.Color.rgb(37, 99, 235)
                            outlinePaint.strokeWidth = 5f
                            title = "Preview Triangle"
                        }
                        draftPolygonRef = triPoly
                        mapView.overlays.add(triPoly)
                    } else if (draft.size == 2) {
                        val line = Polyline(mapView).apply {
                            this.setPoints(draft.toList())
                            outlinePaint.color = android.graphics.Color.rgb(37, 99, 235)
                            outlinePaint.strokeWidth = 5f
                        }
                        draftPolylineRef = line
                        mapView.overlays.add(line)
                    }
                }
                DrawingShape.CUSTOM -> {
                    if (draft.size >= 3) {
                        val closedPts = draft.toList() + draft.first()
                        val customPoly = Polygon(mapView).apply {
                            this.points = closedPts
                            fillPaint.color = android.graphics.Color.argb(60, 37, 99, 235)
                            outlinePaint.color = android.graphics.Color.rgb(37, 99, 235)
                            outlinePaint.strokeWidth = 5f
                            title = "Custom Perimeter"
                        }
                        draftPolygonRef = customPoly
                        mapView.overlays.add(customPoly)
                    } else if (draft.size == 2) {
                        val line = Polyline(mapView).apply {
                            this.setPoints(draft.toList())
                            outlinePaint.color = android.graphics.Color.rgb(37, 99, 235)
                            outlinePaint.strokeWidth = 5f
                        }
                        draftPolylineRef = line
                        mapView.overlays.add(line)
                    }
                }
                null -> {}
            }

            // Render visual vertex handles (dragging is handled smoothly by TouchDragOverlay)
            for (idx in draft.indices) {
                val p = draft[idx]
                val vMarker = Marker(mapView).apply {
                    position = p
                    title = "Point ${idx + 1}"
                    snippet = "Drag to reposition"
                    icon = dragHandleDrawable
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                draftVertexMarkers.add(vMarker)
                mapView.overlays.add(vMarker)
            }

            // Render whole-area translation move handle at centroid
            val draftCenter = geofenceViewModel.getDraftCenter()
            val canMoveArea = when (shape) {
                DrawingShape.CIRCLE -> draft.isNotEmpty()
                DrawingShape.RECTANGLE -> draft.size >= 2
                DrawingShape.TRIANGLE -> draft.size >= 3
                DrawingShape.CUSTOM -> draft.size >= 3
                null -> false
            }

            if (canMoveArea && draftCenter != null) {
                val cMarker = Marker(mapView).apply {
                    position = draftCenter
                    title = if (shape == DrawingShape.CIRCLE) "Center (Drag to Move)" else "Move Entire Safe Zone"
                    snippet = "Drag to translate area"
                    icon = moveAreaDrawable
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                draftCenterMarker = cMarker
                mapView.overlays.add(cMarker)
            }
        }

        // Ensure wearable marker stays on top of polygons
        if (deviceMarkerRef != null && !mapView.overlays.contains(deviceMarkerRef)) {
            mapView.overlays.add(deviceMarkerRef)
        }

        mapView.invalidate()
    }

    // Effect for tile source, coordinates, overlays, and drawing changes
    LaunchedEffect(
        status.latitude,
        status.longitude,
        mapViewRef,
        selectedStyle,
        status.sos,
        geofenceViewModel.isDrawingMode,
        geofenceViewModel.draftPoints.size,
        geofenceViewModel.circleRadiusMeters,
        geofenceViewModel.geofences.size
    ) {
        val lat = status.latitude
        val lng = status.longitude
        val mapView = mapViewRef ?: return@LaunchedEffect

        // Switch tile source if needed
        val targetTileSource = getTileSource(selectedStyle)
        if (mapView.tileProvider.tileSource.name() != targetTileSource.name()) {
            mapView.setTileSource(targetTileSource)
        }

        // Update wearable live position marker
        if (lat != null && lng != null) {
            val geoPoint = GeoPoint(lat, lng)
            val beaconDrawable = ContextCompat.getDrawable(
                context,
                if (status.sos) R.drawable.ic_wearable_pin_sos else R.drawable.ic_wearable_pin
            )

            if (deviceMarkerRef == null) {
                val marker = Marker(mapView).apply {
                    position = geoPoint
                    title = "GuardianLink Wearable"
                    snippet = "Battery: ${status.battery} | ${status.network}"
                    icon = beaconDrawable
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                mapView.overlays.add(marker)
                deviceMarkerRef = marker
            } else {
                deviceMarkerRef?.apply {
                    position = geoPoint
                    snippet = "Battery: ${status.battery} | ${status.network}"
                    icon = beaconDrawable
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
            }

            if (!geofenceViewModel.isDrawingMode) {
                mapView.controller.animateTo(geoPoint)
            }
        }

        // Refresh all geofences & draft overlays
        updateMapOverlays(mapView)
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 1. High-Resolution Map Layer with Tap Interception
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(getTileSource(selectedStyle))
                    setMultiTouchControls(true)
                    setBuiltInZoomControls(false)
                    controller.setZoom(16.5)

                    // MapEventsOverlay intercepts taps for drawing custom safe zones
                    val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            if (geofenceViewModel.isDrawingMode) {
                                geofenceViewModel.onMapTap(p)
                                updateMapOverlays(this@apply)
                                return true
                            }
                            return false
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean {
                            return false
                        }
                    })
                    val touchDragOverlay = TouchDragOverlay(
                        viewModel = geofenceViewModel,
                        onGeometryChanged = { refreshDraftGeometry(this) },
                        onDragFinished = { updateMapOverlays(this) }
                    )
                    overlays.add(0, touchDragOverlay)
                    overlays.add(1, eventsOverlay)

                    val initialPoint = if (status.hasValidLocation) {
                        GeoPoint(status.latitude!!, status.longitude!!)
                    } else {
                        GeoPoint(28.6129, 77.2295)
                    }
                    controller.setCenter(initialPoint)

                    if (status.hasValidLocation) {
                        val beaconDrawable = ContextCompat.getDrawable(
                            ctx,
                            if (status.sos) R.drawable.ic_wearable_pin_sos else R.drawable.ic_wearable_pin
                        )
                        val marker = Marker(this).apply {
                            position = initialPoint
                            title = "GuardianLink Wearable"
                            snippet = "Battery: ${status.battery} | ${status.network}"
                            icon = beaconDrawable
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        }
                        overlays.add(marker)
                        deviceMarkerRef = marker
                    }

                    mapViewRef = this
                    updateMapOverlays(this)
                }
            },
            update = { view ->
                mapViewRef = view
                val targetTileSource = getTileSource(selectedStyle)
                if (view.tileProvider.tileSource.name() != targetTileSource.name()) {
                    view.setTileSource(targetTileSource)
                }
                updateMapOverlays(view)
            }
        )

        // 2. Top Header & Emergency Alerts / Drawing Status Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            // Drawing Mode Banner (when actively drawing a shape)
            AnimatedVisibility(visible = geofenceViewModel.isDrawingMode) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = if (geofenceViewModel.editingZone != null)
                                        "Editing '${geofenceViewModel.editingZone?.name}'"
                                    else
                                        "Drawing ${geofenceViewModel.selectedDrawingShape?.title ?: "Zone"}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = PrimaryBlue
                                )
                                val count = geofenceViewModel.draftPoints.size
                                val guide = when (geofenceViewModel.selectedDrawingShape) {
                                    DrawingShape.CIRCLE -> if (count == 0) "Tap map for center point" else "Drag center to move • Adjust radius slider"
                                    DrawingShape.RECTANGLE -> if (count == 0) "Tap 1st diagonal corner" else if (count == 1) "Tap 2nd opposite corner" else "Drag corners to adjust • Drag center to move"
                                    DrawingShape.TRIANGLE -> if (count < 3) "$count of 3 points placed" else "Drag corners to adjust • Drag center to move"
                                    DrawingShape.CUSTOM -> if (count < 3) "$count points placed (need 3+)" else "$count points • Drag any point or center to move"
                                    null -> ""
                                }
                                Text(
                                    text = guide,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (geofenceViewModel.draftPoints.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { geofenceViewModel.undoLastPoint() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(16.dp))
                                    }
                                }

                                Button(
                                    onClick = {
                                        newZoneName = geofenceViewModel.editingZone?.name ?: ""
                                        showSaveZoneDialog = true
                                    },
                                    enabled = geofenceViewModel.canSave(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueAccent),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        text = if (geofenceViewModel.editingZone != null) "Update" else "Save Zone",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                IconButton(
                                    onClick = { geofenceViewModel.cancelDrawing() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = AlertRed)
                                }
                            }
                        }

                        // Radius Slider for Circular Geofences
                        if (geofenceViewModel.selectedDrawingShape == DrawingShape.CIRCLE && geofenceViewModel.draftPoints.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Radius: ${geofenceViewModel.circleRadiusMeters.toInt()}m",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(100.dp)
                                )
                                Slider(
                                    value = geofenceViewModel.circleRadiusMeters.toFloat(),
                                    onValueChange = { geofenceViewModel.circleRadiusMeters = it.toDouble() },
                                    valueRange = 100f..3000f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Normal Top Header (Live Location, Layer Name, GPS Badge)
            AnimatedVisibility(visible = !geofenceViewModel.isDrawingMode) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = PorcelainWhite
                    ),
                    border = BorderStroke(1.dp, Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Home",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Live Location",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = PrimaryBlue.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = selectedStyle.title.split(" ").first(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = PrimaryBlue,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (status.hasValidLocation) "GPS: ${status.displayGps}" else "Acquiring GPS signal...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))

                        // GPS Status Badge
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (status.hasValidLocation)
                                        OnlineGreen.copy(alpha = 0.15f)
                                    else
                                        AlertRed.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (status.hasValidLocation)
                                        Icons.Default.MyLocation
                                    else
                                        Icons.Default.LocationOff,
                                    contentDescription = null,
                                    tint = if (status.hasValidLocation) OnlineGreen else AlertRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (status.hasValidLocation) "GPS Lock" else "No GPS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (status.hasValidLocation) OnlineGreen else AlertRed
                                )
                            }
                        }
                    }
                }
            }

            // Animated SOS Warning Banner
            AnimatedVisibility(
                visible = status.sos,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AlertRed),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "SOS Warning",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "CRITICAL: SOS TRIGGERED",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Wearer requested immediate assistance",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Floating Action Controls
        // Left Floating FAB: Safe Zones Management
        if (!geofenceViewModel.isDrawingMode) {
            FloatingActionButton(
                onClick = { showGeofenceSheet = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 110.dp, start = 16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PrimaryBlue,
                shape = CircleShape
            ) {
                BadgedBox(
                    badge = {
                        if (geofenceViewModel.geofences.isNotEmpty()) {
                            Badge { Text("${geofenceViewModel.geofences.size}") }
                        }
                    }
                ) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = "Safe Zones")
                }
            }
        }

        // Right Floating Controls Stack (Layer Switcher, Zoom +, Zoom -, Recenter)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 110.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Layer Switcher FAB & Dropdown
            Box {
                SmallFloatingActionButton(
                    onClick = { showLayerMenu = !showLayerMenu },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape
                ) {
                    Icon(imageVector = Icons.Default.Layers, contentDescription = "Change Map Style")
                }

                DropdownMenu(
                    expanded = showLayerMenu,
                    onDismissRequest = { showLayerMenu = false }
                ) {
                    MapStyle.values().forEach { style ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = style.title,
                                    fontWeight = if (selectedStyle == style) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                selectedStyle = style
                                showLayerMenu = false
                            },
                            leadingIcon = {
                                if (selectedStyle == style) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = PrimaryBlue
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Zoom In FAB (+)
            SmallFloatingActionButton(
                onClick = { mapViewRef?.controller?.zoomIn() },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Zoom In")
            }

            // Zoom Out FAB (-)
            SmallFloatingActionButton(
                onClick = { mapViewRef?.controller?.zoomOut() },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Zoom Out")
            }

            // Recenter FAB
            if (status.hasValidLocation) {
                FloatingActionButton(
                    onClick = {
                        val lat = status.latitude
                        val lng = status.longitude
                        if (lat != null && lng != null) {
                            mapViewRef?.controller?.animateTo(GeoPoint(lat, lng))
                        }
                    },
                    containerColor = PrimaryBlue,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Recenter Map")
                }
            }
        }

        // 4. Bottom Info Panel (Battery, Network, Device State)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                InfoChip(
                    label = "Battery",
                    value = status.battery,
                    color = if (status.battery.replace("%", "").toIntOrNull() ?: 100 > 20)
                        OnlineGreen else AlertRed
                )
                InfoChip(
                    label = "Network",
                    value = status.network,
                    color = PrimaryBlue
                )
                InfoChip(
                    label = "Device",
                    value = status.device,
                    color = if (status.device == "Online") OnlineGreen else AlertRed
                )
            }
        }

        // 5. Safe Zones Bottom Sheet Drawer
        if (showGeofenceSheet) {
            ModalBottomSheet(
                onDismissRequest = { showGeofenceSheet = false },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Safe Zones & Geofences",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Button(
                            onClick = {
                                showGeofenceSheet = false
                                showShapeSelectorDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Zone")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (geofenceViewModel.geofences.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No safe zones created yet",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Tap '+ New Zone' to define circles, rectangles,\ntriangles, or custom boundaries",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)
                        ) {
                            items(geofenceViewModel.geofences, key = { it.id }) { zone ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = zone.name,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 15.sp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = OnlineGreen.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = zone.shapeType,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = OnlineGreen,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (zone.shapeType == "Circle")
                                                    "Radius: ${zone.radiusMeters.toInt()}m"
                                                else
                                                    "Center: ${"%.4f".format(zone.centerLatitude)}, ${"%.4f".format(zone.centerLongitude)}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = {
                                                showGeofenceSheet = false
                                                val centerGeo = GeoPoint(zone.centerLatitude, zone.centerLongitude)
                                                mapViewRef?.controller?.animateTo(centerGeo)
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.MyLocation,
                                                    contentDescription = "Focus on Map",
                                                    tint = OnlineGreen
                                                )
                                            }
                                            IconButton(onClick = {
                                                showGeofenceSheet = false
                                                geofenceViewModel.editGeofence(zone)
                                                newZoneName = zone.name
                                                val centerGeo = GeoPoint(zone.centerLatitude, zone.centerLongitude)
                                                mapViewRef?.controller?.animateTo(centerGeo)
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Safe Zone",
                                                    tint = PrimaryBlue
                                                )
                                            }
                                            IconButton(onClick = { geofenceViewModel.deleteGeofence(zone) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Safe Zone",
                                                    tint = AlertRed.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // 6. Shape Selector Dialog
        if (showShapeSelectorDialog) {
            AlertDialog(
                onDismissRequest = { showShapeSelectorDialog = false },
                title = { Text("Choose Safe Zone Shape", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Select how you would like to define the safe zone boundary on the map:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        ShapeOptionItem(
                            icon = Icons.Default.RadioButtonChecked,
                            title = "Circle",
                            description = "Tap center point + set radius slider"
                        ) {
                            showShapeSelectorDialog = false
                            geofenceViewModel.startDrawing(DrawingShape.CIRCLE)
                        }

                        ShapeOptionItem(
                            icon = Icons.Default.CropSquare,
                            title = "Rectangle",
                            description = "Tap 2 opposite diagonal corners"
                        ) {
                            showShapeSelectorDialog = false
                            geofenceViewModel.startDrawing(DrawingShape.RECTANGLE)
                        }

                        ShapeOptionItem(
                            icon = Icons.Default.ChangeHistory,
                            title = "Triangle",
                            description = "Tap 3 boundary points"
                        ) {
                            showShapeSelectorDialog = false
                            geofenceViewModel.startDrawing(DrawingShape.TRIANGLE)
                        }

                        ShapeOptionItem(
                            icon = Icons.Default.Gesture,
                            title = "Custom Polygon",
                            description = "Tap perimeter points around any custom area"
                        ) {
                            showShapeSelectorDialog = false
                            geofenceViewModel.startDrawing(DrawingShape.CUSTOM)
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showShapeSelectorDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 7. Save Zone Dialog (Name input)
        if (showSaveZoneDialog) {
            AlertDialog(
                onDismissRequest = { showSaveZoneDialog = false },
                title = {
                    Text(
                        text = if (geofenceViewModel.editingZone != null) "Update Safe Zone" else "Name Your Safe Zone",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = if (geofenceViewModel.editingZone != null)
                                "Modify the name or keep current name for this safe zone:"
                            else
                                "Give this safe zone a recognizable name (e.g. Home, College, Workplace):",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newZoneName,
                            onValueChange = { newZoneName = it },
                            label = { Text("Zone Name") },
                            placeholder = { Text("e.g. Home Campus") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val name = newZoneName.ifBlank { geofenceViewModel.editingZone?.name ?: "Safe Zone" }
                            geofenceViewModel.saveCurrentShape(name) { success ->
                                showSaveZoneDialog = false
                                newZoneName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text(if (geofenceViewModel.editingZone != null) "Update & Activate" else "Save & Activate")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveZoneDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ShapeOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(text = description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

/**
 * High-performance touch interceptor overlay that provides instant, buttery-smooth
 * dragging for safe-zone vertices and whole-area translation without map-pan interference.
 */
private class TouchDragOverlay(
    private val viewModel: GeofenceViewModel,
    private val onGeometryChanged: () -> Unit,
    private val onDragFinished: () -> Unit
) : Overlay() {
    private var activeVertexIndex = -1
    private var isDraggingArea = false
    private var lastTouchGeo: GeoPoint? = null

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        if (!viewModel.isDrawingMode) return false
        val projection = mapView.projection ?: return false

        val touchX = event.x.toInt()
        val touchY = event.y.toInt()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val density = mapView.context.resources.displayMetrics.density
                val thresholdPx = (38 * density).toInt()
                val screenPoint = Point()

                // 1. Check if user touched any existing vertex dot
                for (i in viewModel.draftPoints.indices.reversed()) {
                    projection.toPixels(viewModel.draftPoints[i], screenPoint)
                    val dx = screenPoint.x - touchX
                    val dy = screenPoint.y - touchY
                    if (dx * dx + dy * dy <= thresholdPx * thresholdPx) {
                        activeVertexIndex = i
                        isDraggingArea = false
                        mapView.parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }

                // 2. Check if user touched the centroid move handle
                val center = viewModel.getDraftCenter()
                if (center != null) {
                    val canMoveArea = when (viewModel.selectedDrawingShape) {
                        DrawingShape.CIRCLE -> viewModel.draftPoints.isNotEmpty()
                        DrawingShape.RECTANGLE -> viewModel.draftPoints.size >= 2
                        DrawingShape.TRIANGLE -> viewModel.draftPoints.size >= 3
                        DrawingShape.CUSTOM -> viewModel.draftPoints.size >= 3
                        null -> false
                    }
                    if (canMoveArea) {
                        projection.toPixels(center, screenPoint)
                        val dx = screenPoint.x - touchX
                        val dy = screenPoint.y - touchY
                        val centerThresholdPx = (46 * density).toInt()
                        if (dx * dx + dy * dy <= centerThresholdPx * centerThresholdPx) {
                            isDraggingArea = true
                            activeVertexIndex = -1
                            lastTouchGeo = projection.fromPixels(touchX, touchY) as GeoPoint
                            mapView.parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeVertexIndex != -1 && activeVertexIndex < viewModel.draftPoints.size) {
                    val newGeo = projection.fromPixels(touchX, touchY) as GeoPoint
                    viewModel.updateDraftPoint(activeVertexIndex, newGeo)
                    onGeometryChanged()
                    return true
                } else if (isDraggingArea) {
                    val currentTouchGeo = projection.fromPixels(touchX, touchY) as GeoPoint
                    val last = lastTouchGeo
                    if (last != null) {
                        val dLat = currentTouchGeo.latitude - last.latitude
                        val dLon = currentTouchGeo.longitude - last.longitude
                        if (dLat != 0.0 || dLon != 0.0) {
                            viewModel.translateArea(dLat, dLon)
                            onGeometryChanged()
                        }
                    }
                    lastTouchGeo = currentTouchGeo
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeVertexIndex != -1 || isDraggingArea) {
                    activeVertexIndex = -1
                    isDraggingArea = false
                    lastTouchGeo = null
                    mapView.parent?.requestDisallowInterceptTouchEvent(false)
                    onDragFinished()
                    return true
                }
            }
        }
        return false
    }
}