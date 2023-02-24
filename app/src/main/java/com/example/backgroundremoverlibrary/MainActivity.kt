package com.example.backgroundremoverlibrary

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toBitmap
import com.example.backgroundremoverlibrary.blur.Blur
import com.example.backgroundremoverlibrary.blur.BlurFactor
import com.example.backgroundremoverlibrary.blur.Helper
import com.example.backgroundremoverlibrary.databinding.ActivityMainBinding
import com.slowmac.autobackgroundremover.BackgroundRemover
import jp.wasabeef.blurry.Blurry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val imageResult =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { uri ->
                binding.img.setImageURI(uri)
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageResult.launch("image/*")



        binding.removeBgBtn.setOnClickListener {
            removeBg()
        }


    }


    private fun removeBg() {
        binding.img.invalidate()
        val bitmap = binding.img.drawable.toBitmap()
        BackgroundRemover.removeTest(bitmap) {
//            binding.img.setImageBitmap(it)
//            Blurry.with(this@MainActivity).radius(25).from(it).into()
            Log.d("vvvetss", "removeBg: ${bitmap.width} ${bitmap.height} ${it.width} ${it.height} ${Helper.hasZero(bitmap.width,bitmap.height)}")
            CoroutineScope(Dispatchers.Default).launch {
                val mask = Blur.of(this@MainActivity, it, BlurFactor(it.width,it.height))
                Log.d("vveet", "removeBg: ${mask == null}")
                val remove = removeBackground(bitmap, mask)
                withContext(Dispatchers.Main){
                    binding.img.setImageBitmap(remove)
                    Toast.makeText(applicationContext,"xong",Toast.LENGTH_LONG).show()
                }
            }
        }
//        BackgroundRemover.bitmapForProcessing(
//            binding.img.drawable.toBitmap(),
//            true,
//            object : OnBackgroundChangeListener {
//                override fun onSuccess(bitmap: Bitmap) {
//                    binding.img.setImageBitmap(bitmap)
////                    Blurry.with(this@MainActivity).radius(25).from(bitmap).into(binding.img)
//
//
//                }
//
//                override fun onFailed(exception: Exception) {
//                    Toast.makeText(this@MainActivity, "Error Occur", Toast.LENGTH_SHORT).show()
//                }
//
//            })
    }

    private fun removeBackground(image: Bitmap, mask: Bitmap): Bitmap {
        val width = image.width
        val height = image.height
        val copyBitmap = image.copy(Bitmap.Config.ARGB_8888, true)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = image.getPixel(x, y)
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val maskAlpha = Color.alpha(mask.getPixel(x, y))
//                if (maskAlpha < )
                val newColor = Color.argb(
                    maskAlpha,
                    maskAlpha * r / 255,
                    maskAlpha * g / 255,
                    maskAlpha * b / 255
                )
                copyBitmap.setPixel(x, y, newColor)
            }
        }
        return copyBitmap
    }


}