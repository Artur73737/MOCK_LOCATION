package com.locationspoofer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.locationspoofer.domain.model.Coordinates
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Native Android map view backed by OpenStreetMap raster tiles.
 * It deliberately uses Canvas and HttpURLConnection instead of a WebView.
 */
class NativeMapView(context: Context) : View(context) {
    var onCoordinatesChanged: ((Coordinates) -> Unit)? = null

    private val tileSize = 256f
    private val tileCache = ConcurrentHashMap<String, Bitmap>()
    private val pendingTiles = ConcurrentHashMap.newKeySet<String>()
    private val tileExecutor = Executors.newFixedThreadPool(3)
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 35, 80, 95)
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(65, 91, 100)
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(211, 47, 47) }
    private val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private var center = Coordinates(41.9028, 12.4964)
    private var zoom = 4.0
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    init {
        setBackgroundColor(Color.rgb(225, 235, 238))
        isClickable = true
        contentDescription = "Interactive OpenStreetMap"
    }

    fun setCenter(coordinates: Coordinates) {
        if (!coordinates.isValid || coordinates == center) return
        center = coordinates
        invalidate()
    }

    fun zoomIn() {
        zoom = min(19.0, zoom + 1.0)
        invalidate()
    }

    fun zoomOut() {
        zoom = max(2.0, zoom - 1.0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val worldSize = tileSize * 2.0.pow(zoom)
        val centerWorld = worldPixel(center, worldSize)
        val left = centerWorld.first - width / 2f
        val top = centerWorld.second - height / 2f
        val firstTileX = floor(left / tileSize).toInt() - 1
        val lastTileX = ceil((left + width) / tileSize).toInt() + 1
        val firstTileY = floor(top / tileSize).toInt() - 1
        val lastTileY = ceil((top + height) / tileSize).toInt() + 1
        val tileCount = 2.0.pow(zoom).toInt()

        for (tileY in firstTileY..lastTileY) {
            if (tileY !in 0 until tileCount) continue
            for (tileX in firstTileX..lastTileX) {
                val wrappedX = ((tileX % tileCount) + tileCount) % tileCount
                val key = "$${zoom.toInt()}:$wrappedX:$tileY"
                val destination = Rect(
                    (tileX * tileSize - left).toInt(),
                    (tileY * tileSize - top).toInt(),
                    ((tileX + 1) * tileSize - left).toInt(),
                    ((tileY + 1) * tileSize - top).toInt(),
                )
                val bitmap = tileCache[key]
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, null, destination, tilePaint)
                } else {
                    drawTilePlaceholder(canvas, destination, wrappedX, tileY)
                    requestTile(key, zoom.toInt(), wrappedX, tileY)
                }
            }
        }

        drawCenterMarker(canvas)
        canvas.drawText("© OpenStreetMap contributors", 24f, height - 24f, labelPaint.apply { textSize = 18f; typeface = Typeface.DEFAULT })
    }

    private fun drawTilePlaceholder(canvas: Canvas, rect: Rect, tileX: Int, tileY: Int) {
        placeholderPaint.color = if ((tileX + tileY) % 2 == 0) Color.rgb(216, 231, 232) else Color.rgb(207, 225, 228)
        canvas.drawRect(rect, placeholderPaint)
        for (x in rect.left..rect.right step 64) canvas.drawLine(x.toFloat(), rect.top.toFloat(), x.toFloat(), rect.bottom.toFloat(), gridPaint)
        for (y in rect.top..rect.bottom step 64) canvas.drawLine(rect.left.toFloat(), y.toFloat(), rect.right.toFloat(), y.toFloat(), gridPaint)
    }

    private fun drawCenterMarker(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val pin = Path().apply {
            moveTo(cx, cy + 30f)
            cubicTo(cx - 42f, cy - 10f, cx - 34f, cy - 55f, cx, cy - 55f)
            cubicTo(cx + 34f, cy - 55f, cx + 42f, cy - 10f, cx, cy + 30f)
            close()
        }
        canvas.drawPath(pin, markerPaint)
        canvas.drawCircle(cx, cy - 25f, 15f, markerStrokePaint)
    }

    private fun requestTile(key: String, zoom: Int, x: Int, y: Int) {
        if (!pendingTiles.add(key)) return
        tileExecutor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://tile.openstreetmap.org/$zoom/$x/$y.png").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 7000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "LocationSpoofer/1.0 (Android)")
                }
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedInputStream(connection.inputStream).use { input ->
                        BitmapFactory.decodeStream(input)?.let { tileCache[key] = it }
                    }
                }
            } catch (_: Exception) {
                // The placeholder grid remains visible when the device is offline.
            } finally {
                connection?.disconnect()
                pendingTiles.remove(key)
                postInvalidate()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return true
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y
                panByPixels(-dx, -dy)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent.requestDisallowInterceptTouchEvent(false)
                onCoordinatesChanged?.invoke(center)
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun panByPixels(dx: Float, dy: Float) {
        val worldSize = tileSize * 2.0.pow(zoom)
        val world = worldPixel(center, worldSize)
        val nextX = (world.first + dx).mod(worldSize)
        val nextY = min(worldSize - 1.0, max(0.0, world.second + dy))
        center = coordinatesFromWorld(nextX, nextY, worldSize)
        invalidate()
        onCoordinatesChanged?.invoke(center)
    }

    private fun worldPixel(coordinates: Coordinates, worldSize: Double): Pair<Double, Double> {
        val x = (coordinates.longitude + 180.0) / 360.0 * worldSize
        val sinLatitude = sinLatitude(coordinates.latitude)
        val y = (0.5 - ln((1 + sinLatitude) / (1 - sinLatitude)) / (4 * Math.PI)) * worldSize
        return x to y
    }

    private fun coordinatesFromWorld(x: Double, y: Double, worldSize: Double): Coordinates {
        val longitude = (x / worldSize) * 360.0 - 180.0
        val n = Math.PI - (2.0 * Math.PI * y / worldSize)
        val latitude = Math.toDegrees(kotlin.math.atan(sinh(n))).coerceIn(-85.0511, 85.0511)
        return Coordinates(latitude, longitude)
    }

    private fun sinLatitude(latitude: Double): Double {
        val bounded = latitude.coerceIn(-85.0511, 85.0511)
        return kotlin.math.sin(Math.toRadians(bounded))
    }

    override fun onDetachedFromWindow() {
        tileExecutor.shutdownNow()
        tileCache.values.forEach { it.recycle() }
        tileCache.clear()
        super.onDetachedFromWindow()
    }
}
