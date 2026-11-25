package com.progressone.nativeapp.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await
import com.progressone.nativeapp.data.LocalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.hypot
import kotlin.math.ceil

/** Bounded-memory photo editor. All processing runs off the UI thread. */
object ImageProcessor {
    const val MAX_SIDE = 1280

    suspend fun decode(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bitmap = if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val size = info.size
                val factor = min(1f, MAX_SIDE.toFloat() / max(size.width, size.height).toFloat())
                decoder.setTargetSize(max(1, (size.width * factor).toInt()), max(1, (size.height * factor).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image" }
            var sample = 1
            while (max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: error("Photo could not be decoded")
            val rotation = resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (rotation != 0f) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(rotation) }, true) else decoded
        }
        bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    suspend fun crop(src: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap =
        withContext(Dispatchers.Default) {
            require(left + right < .98f && top + bottom < .98f) { "Crop too narrow" }
            val x = (src.width * left).toInt().coerceIn(0, src.width - 1)
            val y = (src.height * top).toInt().coerceIn(0, src.height - 1)
            val w = (src.width * (1f - left - right)).toInt().coerceIn(1, src.width - x)
            val h = (src.height * (1f - top - bottom)).toInt().coerceIn(1, src.height - y)
            Bitmap.createBitmap(src, x, y, w, h).copy(Bitmap.Config.ARGB_8888, true)
        }

    /**
     * ML Kit Subject Segmentation: the model runs on the Android device.
     * Google Play Services must fetch this model once before it works offline.
     * Unlike the color-based fallback, this generates an actual subject matte.
     */
    suspend fun aiCutout(source: Bitmap): Bitmap {
        val options = SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        val segmenter = SubjectSegmentation.getClient(options)
        try {
            val input = InputImage.fromBitmap(source, 0)
            val result = segmenter.process(input).await()
            val foreground = result.foregroundBitmap
                ?: error("No foreground detected. Try another photo or the manual tools.")
            val sized = if (foreground.width == source.width && foreground.height == source.height) foreground
                else Bitmap.createScaledBitmap(foreground, source.width, source.height, true)
            val rendered = sized.copy(Bitmap.Config.ARGB_8888, true)
            // Detect a model that returned an unsegmented image instead of presenting a fake PNG.
            val pixels = IntArray(rendered.width * rendered.height)
            rendered.getPixels(pixels, 0, rendered.width, 0, 0, rendered.width, rendered.height)
            require(pixels.any { Color.alpha(it) < 240 }) {
                "No transparent background detected; choose a clearer garment photo and retry"
            }
            return rendered
        } finally {
            segmenter.close()
        }
    }

    /**
     * Adaptive flood-fill from the border. Protects pre-existing alpha. Does NOT claim
     * ML-quality object segmentation; detailed backgrounds may need brush corrections.
     */
    suspend fun backgroundCutout(source: Bitmap, tolerance: Float): Bitmap = withContext(Dispatchers.Default) {
        val w = source.width
        val h = source.height
        require(w > 0 && h > 0 && w.toLong() * h < 3_000_000) { "Image too large" }
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val alreadyTransparent = pixels.count { Color.alpha(it) < 220 }
        if (alreadyTransparent >= pixels.size / 20) return@withContext source.copy(Bitmap.Config.ARGB_8888, true)

        // Choose the median boundary color. Robust against one darker corner.
        val boundary = ArrayList<Int>(200)
        val stepX = max(1, w / 40)
        val stepY = max(1, h / 40)
        for (x in 0 until w step stepX) { boundary.add(pixels[x]); boundary.add(pixels[(h - 1) * w + x]) }
        for (y in 0 until h step stepY) { boundary.add(pixels[y * w]); boundary.add(pixels[y * w + (w - 1)]) }
        fun median(selector: (Int) -> Int): Int = boundary.map(selector).sorted()[boundary.size / 2]
        val r = median { Color.red(it) }
        val g = median { Color.green(it) }
        val b = median { Color.blue(it) }
        fun distance(p: Int): Float {
            // YCbCr-like lightness/chroma balance; tolerant of lighting gradients.
            val dr = Color.red(p) - r
            val dg = Color.green(p) - g
            val db = Color.blue(p) - b
            val l = (dr * .299f + dg * .587f + db * .114f)
            return sqrt((l * l * .38f + (dr - dg) * (dr - dg) * .34f +
                (db - dg) * (db - dg) * .28f).toDouble()).toFloat()
        }
        val visited = BooleanArray(w * h)
        val mask = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        val limit = tolerance.coerceIn(8f, 110f)
        fun add(position: Int) {
            if (visited[position]) return
            visited[position] = true
            if (distance(pixels[position]) > limit) return
            queue[tail++] = position
        }
        for (x in 0 until w) { add(x); add((h - 1) * w + x) }
        for (y in 0 until h) { add(y * w); add(y * w + w - 1) }
        while (head < tail) {
            val p = queue[head++]
            mask[p] = true
            val x = p % w
            val y = p / w
            if (x > 0) add(p - 1)
            if (x + 1 < w) add(p + 1)
            if (y > 0) add(p - w)
            if (y + 1 < h) add(p + w)
        }
        // If the image has no sufficiently uniform edge region, preserve it unchanged.
        if (tail < min(24, pixels.size / 100)) return@withContext source.copy(Bitmap.Config.ARGB_8888, true)
        for (i in pixels.indices) {
            if (mask[i]) {
                pixels[i] = pixels[i] and 0x00ffffff
            } else {
                // Slight feather to prevent jagged halos at the cut boundary.
                val x = i % w
                val y = i / w
                val touchesBackground = (x > 0 && mask[i - 1]) || (x + 1 < w && mask[i + 1]) ||
                    (y > 0 && mask[i - w]) || (y + 1 < h && mask[i + w])
                if (touchesBackground) pixels[i] = (pixels[i] and 0x00ffffff) or (210 shl 24)
            }
        }
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Crop transparent padding consistently from both the original and processed layers. */
    suspend fun trimTransparentPair(original: Bitmap, processed: Bitmap, margin: Int = 12): Pair<Bitmap, Bitmap> =
        withContext(Dispatchers.Default) {
            require(original.width == processed.width && original.height == processed.height) {
                "Original and mask dimensions differ"
            }
            val w = processed.width
            val h = processed.height
            val px = IntArray(w * h)
            processed.getPixels(px, 0, w, 0, 0, w, h)
            var left = w
            var right = -1
            var top = h
            var bottom = -1
            for (y in 0 until h) for (x in 0 until w) {
                if (Color.alpha(px[y * w + x]) >= 12) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
            if (right < left || bottom < top) return@withContext original to processed
            val padding = margin.coerceAtLeast(0)
            left = (left - padding).coerceAtLeast(0)
            top = (top - padding).coerceAtLeast(0)
            right = (right + padding).coerceAtMost(w - 1)
            bottom = (bottom + padding).coerceAtMost(h - 1)
            val width = right - left + 1
            val height = bottom - top + 1
            if (width == w && height == h) return@withContext original to processed
            Bitmap.createBitmap(original, left, top, width, height).copy(Bitmap.Config.ARGB_8888, true) to
                Bitmap.createBitmap(processed, left, top, width, height).copy(Bitmap.Config.ARGB_8888, true)
        }

    /** Pixel brush, in original image coordinates; erase OR restore from the source. */
    suspend fun brush(processed: Bitmap, original: Bitmap, strokes: List<Pair<Float, Float>>,
                      brushRadius: Int, restore: Boolean): Bitmap = withContext(Dispatchers.Default) {
        require(processed.width == original.width && processed.height == original.height)
        val w = processed.width
        val h = processed.height
        val px = IntArray(w * h)
        val orig = IntArray(w * h)
        processed.getPixels(px, 0, w, 0, 0, w, h)
        original.getPixels(orig, 0, w, 0, 0, w, h)
        // Interpolate finger positions so quick strokes do not leave broken dotted gaps.
        val radius = brushRadius.coerceIn(3, 100)
        val dabs = ArrayList<Pair<Float, Float>>()
        if (strokes.isNotEmpty()) {
            dabs.add(strokes.first())
            for (i in 1 until strokes.size) {
                val from = strokes[i - 1]
                val to = strokes[i]
                val distance = hypot(to.first - from.first, to.second - from.second)
                val steps = ceil(distance / (radius * .45f).coerceAtLeast(1f)).toInt().coerceIn(1, 150)
                for (step in 1..steps) {
                    val t = step.toFloat() / steps
                    dabs.add((from.first + (to.first - from.first) * t) to
                        (from.second + (to.second - from.second) * t))
                }
            }
        }
        for ((xf, yf) in dabs) {
            val cx = xf.coerceIn(0f, (w - 1).toFloat())
            val cy = yf.coerceIn(0f, (h - 1).toFloat())
            for (y in max(0, (cy - radius).toInt())..min(h - 1, (cy + radius).toInt())) {
                for (x in max(0, (cx - radius).toInt())..min(w - 1, (cx + radius).toInt())) {
                    val distance = hypot(x - cx, y - cy)
                    if (distance >= radius) continue
                    val coverage = ((radius - distance) / 2.5f).coerceIn(0f, 1f)
                    val idx = y * w + x
                    val old = px[idx]
                    val initial = orig[idx]
                    val oldAlpha = Color.alpha(old)
                    if (restore) {
                        val alpha = (oldAlpha + (Color.alpha(initial) - oldAlpha) * coverage)
                            .toInt().coerceIn(0, 255)
                        // The initial image supplies the color at restored pixels.
                        px[idx] = (alpha shl 24) or (initial and 0x00ffffff)
                    } else {
                        val alpha = (oldAlpha * (1f - coverage)).toInt().coerceIn(0, 255)
                        px[idx] = (alpha shl 24) or (old and 0x00ffffff)
                    }
                }
            }
        }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Preserve the uncropped/restoration color source alongside the processed PNG.
     * For opaque photos use a compact JPEG to limit backup size; transparent imported
     * sources retain lossless PNG to avoid flattening already transparent areas.
     */
    suspend fun saveRestorationSource(store: LocalStore, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val transparent = pixels.any { Color.alpha(it) < 255 }
        val format = if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val extension = if (transparent) "png" else "jpg"
        val name = "${LocalStore.freshId()}_source.$extension"
        store.withMediaLock {
            val target = store.photoFile(name) ?: error("Unsafe photo name")
            val temp = File(store.photoDirectory, "$name.tmp")
            try {
                FileOutputStream(temp).use { stream ->
                    check(bitmap.compress(format, if (transparent) 100 else 88, stream)) {
                        "Unable to save restoration source"
                    }
                    stream.fd.sync()
                }
                check(temp.renameTo(target)) { "Could not finalize restoration source" }
                name
            } finally { temp.delete() }
        }
    }

    suspend fun savePng(store: LocalStore, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val filename = "${LocalStore.freshId()}.png"
        store.withMediaLock {
            val target = store.photoFile(filename) ?: error("Unsafe path")
            val temporary = File(store.photoDirectory, "$filename.tmp")
            try {
                FileOutputStream(temporary).use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG encoding failed" }
                    stream.fd.sync()
                }
                check(temporary.renameTo(target)) { "Failed to save image" }
                filename
            } finally { temporary.delete() }
        }
    }
}
