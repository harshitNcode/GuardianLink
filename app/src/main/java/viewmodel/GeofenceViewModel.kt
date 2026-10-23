package com.guardianlink.app.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.guardianlink.app.data.GeofenceLocalStore
import com.guardianlink.app.data.GeofenceRepository
import com.guardianlink.app.data.GeofenceWktHelper
import com.guardianlink.app.model.GeofenceZone
import org.osmdroid.util.GeoPoint

enum class DrawingShape(val title: String, val minPoints: Int) {
    CIRCLE("Circle", 1),
    RECTANGLE("Rectangle", 2),
    TRIANGLE("Triangle", 3),
    CUSTOM("Custom Polygon", 3)
}

class GeofenceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GeofenceRepository()
    val geofences = mutableStateListOf<GeofenceZone>()

    var isDrawingMode by mutableStateOf(false)
    var selectedDrawingShape by mutableStateOf<DrawingShape?>(null)
    val draftPoints = mutableStateListOf<GeoPoint>()
    var circleRadiusMeters by mutableStateOf(500.0)

    // Tracks if an existing zone is being edited
    var editingZone by mutableStateOf<GeofenceZone?>(null)

    init {
        // 1. Immediately load all locally saved safe zones from SharedPreferences
        val cached = GeofenceLocalStore.loadZones(getApplication(), "demo_device")
        if (cached.isNotEmpty()) {
            geofences.clear()
            geofences.addAll(cached)
        }
        // 2. Refresh from backend
        loadGeofences()
    }

    fun loadGeofences(deviceId: String = "demo_device") {
        repository.fetchGeofences(deviceId) { remoteList ->
            if (remoteList.isNotEmpty()) {
                val merged = remoteList.toMutableList()
                for (local in geofences) {
                    if (merged.none { it.id == local.id || (it.name == local.name && it.centerLatitude == local.centerLatitude) }) {
                        merged.add(local)
                    }
                }
                geofences.clear()
                geofences.addAll(merged)
                GeofenceLocalStore.saveZones(getApplication(), deviceId, merged)
            } else {
                geofences.clear()
                GeofenceLocalStore.saveZones(getApplication(), deviceId, emptyList())
            }
        }
    }

    fun startDrawing(shape: DrawingShape) {
        isDrawingMode = true
        selectedDrawingShape = shape
        editingZone = null
        draftPoints.clear()
        circleRadiusMeters = 500.0
    }

    fun editGeofence(zone: GeofenceZone) {
        editingZone = zone
        isDrawingMode = true
        draftPoints.clear()
        circleRadiusMeters = if (zone.radiusMeters > 0) zone.radiusMeters else 500.0

        if (zone.polygonWkt.isNullOrBlank()) {
            selectedDrawingShape = DrawingShape.CIRCLE
            draftPoints.add(GeoPoint(zone.centerLatitude, zone.centerLongitude))
        } else {
            val parsedPoints = GeofenceWktHelper.fromPolygonWkt(zone.polygonWkt)
            val uniquePoints = if (parsedPoints.size > 1 && parsedPoints.first() == parsedPoints.last()) {
                parsedPoints.dropLast(1)
            } else {
                parsedPoints
            }

            when (zone.shapeType) {
                "Triangle" -> {
                    selectedDrawingShape = DrawingShape.TRIANGLE
                    draftPoints.addAll(uniquePoints.take(3))
                }
                "Rectangle" -> {
                    selectedDrawingShape = DrawingShape.RECTANGLE
                    if (uniquePoints.size >= 4) {
                        draftPoints.add(uniquePoints[0])
                        draftPoints.add(uniquePoints[2])
                    } else {
                        draftPoints.addAll(uniquePoints)
                    }
                }
                "Circle" -> {
                    selectedDrawingShape = DrawingShape.CIRCLE
                    draftPoints.add(GeoPoint(zone.centerLatitude, zone.centerLongitude))
                }
                else -> {
                    selectedDrawingShape = DrawingShape.CUSTOM
                    draftPoints.addAll(uniquePoints)
                }
            }
        }
    }

    fun onMapTap(point: GeoPoint) {
        if (!isDrawingMode) return
        val shape = selectedDrawingShape ?: return

        when (shape) {
            DrawingShape.CIRCLE -> {
                draftPoints.clear()
                draftPoints.add(point)
            }
            DrawingShape.RECTANGLE -> {
                if (draftPoints.size >= 2) {
                    draftPoints.clear()
                }
                draftPoints.add(point)
            }
            DrawingShape.TRIANGLE -> {
                if (draftPoints.size >= 3) {
                    draftPoints.clear()
                }
                draftPoints.add(point)
            }
            DrawingShape.CUSTOM -> {
                draftPoints.add(point)
            }
        }
    }

    /**
     * Updates an individual vertex coordinate when dragged by the user.
     */
    fun updateDraftPoint(index: Int, point: GeoPoint) {
        if (index in 0 until draftPoints.size) {
            draftPoints[index] = point
        }
    }

    /**
     * Translates all vertices simultaneously when user drags the center move handle.
     */
    fun translateArea(deltaLat: Double, deltaLon: Double) {
        if (deltaLat == 0.0 && deltaLon == 0.0) return
        for (i in draftPoints.indices) {
            val current = draftPoints[i]
            draftPoints[i] = GeoPoint(current.latitude + deltaLat, current.longitude + deltaLon)
        }
    }

    /**
     * Computes the centroid / center position for the whole-area drag handle.
     */
    fun getDraftCenter(): GeoPoint? {
        if (draftPoints.isEmpty()) return null
        val shape = selectedDrawingShape ?: return null
        return when (shape) {
            DrawingShape.CIRCLE -> draftPoints.first()
            DrawingShape.RECTANGLE -> {
                if (draftPoints.size >= 2) {
                    val rectPts = GeofenceWktHelper.createRectanglePoints(draftPoints[0], draftPoints[1])
                    GeofenceWktHelper.computeCenter(rectPts)
                } else draftPoints.first()
            }
            DrawingShape.TRIANGLE -> {
                if (draftPoints.size >= 3) {
                    GeofenceWktHelper.computeCenter(draftPoints)
                } else null
            }
            DrawingShape.CUSTOM -> {
                if (draftPoints.size >= 3) {
                    GeofenceWktHelper.computeCenter(draftPoints)
                } else null
            }
        }
    }

    fun undoLastPoint() {
        if (draftPoints.isNotEmpty()) {
            draftPoints.removeAt(draftPoints.size - 1)
        }
    }

    fun clearDraft() {
        draftPoints.clear()
    }

    fun cancelDrawing() {
        isDrawingMode = false
        selectedDrawingShape = null
        editingZone = null
        draftPoints.clear()
    }

    fun canSave(): Boolean {
        val shape = selectedDrawingShape ?: return false
        return draftPoints.size >= shape.minPoints
    }

    fun saveCurrentShape(name: String, deviceId: String = "demo_device", onComplete: (Boolean) -> Unit) {
        val shape = selectedDrawingShape ?: return onComplete(false)
        if (!canSave()) return onComplete(false)

        val zone: GeofenceZone = when (shape) {
            DrawingShape.CIRCLE -> {
                val center = draftPoints.first()
                val circlePolygon = GeofenceWktHelper.createCirclePoints(center, circleRadiusMeters)
                GeofenceZone(
                    id = editingZone?.id ?: 0,
                    deviceId = deviceId,
                    userId = "demo_user",
                    name = name.ifBlank { editingZone?.name ?: "Safe Zone" },
                    centerLatitude = center.latitude,
                    centerLongitude = center.longitude,
                    radiusMeters = circleRadiusMeters,
                    polygonWkt = GeofenceWktHelper.toPolygonWkt(circlePolygon)
                )
            }
            DrawingShape.RECTANGLE -> {
                val p1 = draftPoints[0]
                val p2 = draftPoints[1]
                val rectPoints = GeofenceWktHelper.createRectanglePoints(p1, p2)
                val center = GeofenceWktHelper.computeCenter(rectPoints)
                GeofenceZone(
                    id = editingZone?.id ?: 0,
                    deviceId = deviceId,
                    userId = "demo_user",
                    name = name.ifBlank { editingZone?.name ?: "Rectangular Zone" },
                    centerLatitude = center.latitude,
                    centerLongitude = center.longitude,
                    radiusMeters = 0.0,
                    polygonWkt = GeofenceWktHelper.toPolygonWkt(rectPoints)
                )
            }
            DrawingShape.TRIANGLE -> {
                val triPoints = GeofenceWktHelper.createTrianglePoints(
                    draftPoints[0], draftPoints[1], draftPoints[2]
                )
                val center = GeofenceWktHelper.computeCenter(triPoints)
                GeofenceZone(
                    id = editingZone?.id ?: 0,
                    deviceId = deviceId,
                    userId = "demo_user",
                    name = name.ifBlank { editingZone?.name ?: "Triangular Zone" },
                    centerLatitude = center.latitude,
                    centerLongitude = center.longitude,
                    radiusMeters = 0.0,
                    polygonWkt = GeofenceWktHelper.toPolygonWkt(triPoints)
                )
            }
            DrawingShape.CUSTOM -> {
                val customPoints = draftPoints.toList()
                val center = GeofenceWktHelper.computeCenter(customPoints)
                GeofenceZone(
                    id = editingZone?.id ?: 0,
                    deviceId = deviceId,
                    userId = "demo_user",
                    name = name.ifBlank { editingZone?.name ?: "Custom Safe Zone" },
                    centerLatitude = center.latitude,
                    centerLongitude = center.longitude,
                    radiusMeters = 0.0,
                    polygonWkt = GeofenceWktHelper.toPolygonWkt(customPoints)
                )
            }
        }

        val currentEditing = editingZone
        if (currentEditing != null) {
            val targetZone = zone.copy(id = currentEditing.id)
            val idx = geofences.indexOfFirst { it.id == targetZone.id }
            if (idx != -1) {
                geofences[idx] = targetZone
            }
            GeofenceLocalStore.saveZones(getApplication(), deviceId, geofences.toList())
            cancelDrawing()
            onComplete(true)

            repository.updateGeofence(targetZone) { success ->
                if (success) {
                    loadGeofences(deviceId)
                }
            }
        } else {
            val tempId = ((geofences.maxOfOrNull { it.id } ?: 0) + 1).coerceAtLeast(1)
            val targetZone = zone.copy(id = tempId)
            geofences.add(targetZone)
            GeofenceLocalStore.saveZones(getApplication(), deviceId, geofences.toList())
            cancelDrawing()
            onComplete(true)

            repository.createGeofence(targetZone) { createdZone ->
                if (createdZone != null) {
                    val idx = geofences.indexOfFirst { it.id == tempId }
                    if (idx != -1) {
                        geofences[idx] = createdZone
                        GeofenceLocalStore.saveZones(getApplication(), deviceId, geofences.toList())
                    }
                }
            }
        }
    }

    fun deleteGeofence(zone: GeofenceZone) {
        geofences.remove(zone)
        GeofenceLocalStore.saveZones(getApplication(), zone.deviceId, geofences.toList())
        repository.deleteGeofence(zone.id) { success ->
            if (!success) {
                // Keep local state
            }
        }
    }
}
