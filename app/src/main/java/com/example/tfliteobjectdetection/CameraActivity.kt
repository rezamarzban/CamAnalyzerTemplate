package com.example.tfliteobjectdetection

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.tfliteobjectdetection.databinding.ActivityCameraBinding
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding

    private lateinit var inputBuffer: Bitmap

    private val executor = Executors.newSingleThreadExecutor()
    private val permissions = listOf(Manifest.permission.CAMERA)
    private val permissionsRequestCode = Random.nextInt(0, 10000)

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)


        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /** Declare and bind preview and analysis use cases */
    @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
    private fun bindCameraUseCases() = binding.viewFinder.post {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Camera provider is now guaranteed to be available
            val cameraProvider = cameraProviderFuture.get()
            // Set up the view finder use case to display camera preview
            val rotation = binding.viewFinder.display.rotation
            val preview = Preview.Builder()
                .setTargetAspectRatio(ASPECT_RATIO)
                .setTargetRotation(rotation)
                .build()
            // Set up the image analysis use case which will process frames in real time
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(ASPECT_RATIO)
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()


            imageAnalysis.setAnalyzer(executor) { input ->
                if (!::inputBuffer.isInitialized) {
                    inputBuffer = Bitmap.createBitmap(
                        input.width, input.height,
                        Bitmap.Config.ARGB_8888
                    )
                    Log.i(TAG, "Input buffer size ${input.width}x${input.height}")
                }

                val rotationDegrees = input.imageInfo.rotationDegrees
                input.use {
                    inputBuffer.copyPixelsFromBuffer(input.planes[0].buffer)
                }

                //your code: detect(inputBuffer, rotationDeg...
            }

            // Create a new camera selector each time, enforcing lens facing
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

            // Apply declared configs to CameraX using the same lifecycle owner
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this as LifecycleOwner, cameraSelector, preview, imageAnalysis
            )

            // Use the camera object to link our preview use case with the view
            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)

        }, ContextCompat.getMainExecutor(this))
    }


    override fun onResume() {
        super.onResume()

        // Request permissions each time the app resumes, since they can be revoked at any time
        if (!hasPermissions(this)) {
            ActivityCompat.requestPermissions(
                this, permissions.toTypedArray(), permissionsRequestCode
            )
        } else {
            bindCameraUseCases()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionsRequestCode && hasPermissions(this)) {
            bindCameraUseCases()
        } else {
            finish() // If we don't have the required permissions, we can't run
        }
    }

    override fun onDestroy() {
        executor.apply {
            shutdown()
            awaitTermination(1000, TimeUnit.MILLISECONDS)
        }
        
        super.onDestroy()
    }

    /** Convenience method used to check if all permissions required by this app are granted */
    private fun hasPermissions(context: Context) = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val TAG = CameraActivity::class.java.simpleName
        private const val ASPECT_RATIO = AspectRatio.RATIO_16_9
    }
}
