package com.slowmac.autobackgroundremover

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.annotation.ColorInt
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import kotlin.system.measureTimeMillis


object BackgroundRemover {

    private val segment: Segmenter
    private var buffer = ByteBuffer.allocate(0)
    private var width = 0
    private var height = 0


    init {
        val segmentOptions = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        segment = Segmentation.getClient(segmentOptions)
    }


    /**
     * Process the image to get buffer and image height and width
     * @param bitmap Bitmap which you want to remove background.
     * @param trimEmptyPart After removing the background if its true it will remove the empty part of bitmap. by default its false.
     * @param listener listener for success and failure callback.
     **/
    fun removeTest(bitmap: Bitmap, onSuccess: (Bitmap) -> Unit) {
        val copyBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val input = InputImage.fromBitmap(copyBitmap, 0)
        segment.process(input)
            .addOnSuccessListener { segmentationMask ->
                buffer = segmentationMask.buffer
                width = segmentationMask.width
                height = segmentationMask.height
                val pixel = maskColorsFromByteBuffer(buffer, width, height)
                val bitmapResult = Bitmap.createBitmap(pixel, width, height,Bitmap.Config.ARGB_8888)
                onSuccess(bitmapResult)
            }
    }
    @ColorInt
    private fun maskColorsFromByteBuffer(byteBuffer: ByteBuffer,maskWidth:Int,maskHeight:Int): IntArray {
        @ColorInt val colors = IntArray(maskWidth * maskHeight)
        for (i in 0 until maskWidth * maskHeight) {
//            val backgroundLikelihood = byteBuffer.float
            colors[i] = Color.argb(((byteBuffer.float)*255).toInt(), 255, 255, 255)
//            if (backgroundLikelihood > 0.9) {
//                colors[i] = Color.argb(128, 255, 0, 255)
//            } else if (backgroundLikelihood > 0.2) {
//                // Linear interpolation to make sure when backgroundLikelihood is 0.2, the alpha is 0 and
//                // when backgroundLikelihood is 0.9, the alpha is 128.
//                // +0.5 to round the float value to the nearest int.
//                val alpha = (182.9 * backgroundLikelihood - 36.6 + 0.5).toInt()
//                colors[i] = Color.argb(0, 255, 0, 255)
//            }
        }
        return colors
    }

    fun bitmapForProcessing(
        bitmap: Bitmap,
        trimEmptyPart: Boolean? = false,
        listener: OnBackgroundChangeListener
    ) {
        //Generate a copy of bitmap just in case the if the bitmap is immutable.
        val copyBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val input = InputImage.fromBitmap(copyBitmap, 0)
        segment.process(input)
            .addOnSuccessListener { segmentationMask ->
                buffer = segmentationMask.buffer
                width = segmentationMask.width
                height = segmentationMask.height

                CoroutineScope(Dispatchers.IO).launch {
                    val time = measureTimeMillis {
                        val resultBitmap = if (trimEmptyPart == true) {
                            val bgRemovedBitmap = removeBackgroundFromImage(copyBitmap)
                            trim(bgRemovedBitmap)
                        } else {
                            removeBackgroundFromImage(copyBitmap)
                        }
                        withContext(Dispatchers.Main) {
                            listener.onSuccess(resultBitmap)
                        }
                    }
                    Log.e("TAG", "bitmapForProcessingTime: $time")
                }

            }
            .addOnFailureListener { e ->
                println("Image processing failed: $e")
                listener.onFailed(e)

            }
    }


    /**
     * Change the background pixels color to transparent.
     * */
    private suspend fun removeBackgroundFromImage(
        image: Bitmap
    ): Bitmap {
        val bitmap = CoroutineScope(Dispatchers.IO).async {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val bgConfidence = ((1.0 - buffer.float) * 255).toInt()
                    if (bgConfidence >= 100) {
                        image.setPixel(x, y, 0)
                    }
                }
            }
            buffer.rewind()
            return@async image
        }
        return bitmap.await()
    }


    /**
     * trim the empty part of a bitmap.
     **/
    private suspend fun trim(
        bitmap: Bitmap
    ): Bitmap {
        val result = CoroutineScope(Dispatchers.Default).async {
            var firstX = 0
            var firstY = 0
            var lastX = bitmap.width
            var lastY = bitmap.height
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            loop@ for (x in 0 until bitmap.width) {
                for (y in 0 until bitmap.height) {
                    if (pixels[x + y * bitmap.width] != Color.TRANSPARENT) {
                        firstX = x
                        break@loop
                    }
                }
            }
            loop@ for (y in 0 until bitmap.height) {
                for (x in firstX until bitmap.width) {
                    if (pixels[x + y * bitmap.width] != Color.TRANSPARENT) {
                        firstY = y
                        break@loop
                    }
                }
            }
            loop@ for (x in bitmap.width - 1 downTo firstX) {
                for (y in bitmap.height - 1 downTo firstY) {
                    if (pixels[x + y * bitmap.width] != Color.TRANSPARENT) {
                        lastX = x
                        break@loop
                    }
                }
            }
            loop@ for (y in bitmap.height - 1 downTo firstY) {
                for (x in bitmap.width - 1 downTo firstX) {
                    if (pixels[x + y * bitmap.width] != Color.TRANSPARENT) {
                        lastY = y
                        break@loop
                    }
                }
            }
            return@async Bitmap.createBitmap(bitmap, firstX, firstY, lastX - firstX, lastY - firstY)
        }
        return result.await()
    }

}