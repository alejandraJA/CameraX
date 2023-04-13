package com.example.camerax


import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.camerax.databinding.ActivityCameraBinding
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class CameraActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var binding: ActivityCameraBinding
    private lateinit var bindToLifecycle: Camera

    private var pictureName = "nombre"

    private var cameraMode = CameraSelector.DEFAULT_BACK_CAMERA
    private var flashMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request camera permissions
        if (allPermissionsGranted()) startCamera(cameraMode)
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)


        // Set up the listeners for take photo and video capture buttons
        binding.btnCapture.setOnClickListener { takePicture() }
        binding.btnOk.setOnClickListener { onOk() }
        binding.btnTakeOther.setOnClickListener { takeOtherPicture() }
        binding.btnFlash.setOnClickListener { changeFlashMode() }
        binding.btnSwitch.setOnClickListener { changeCamera() }

        cameraExecutor = Executors.newSingleThreadExecutor()

    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera(cameraMode)
            } else {
                Toast.makeText(
                    this, "Permissions not granted by the user.", Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun startCamera(cameraSelector: CameraSelector) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            imageCapture = ImageCapture.Builder().build()
            imageCapture!!.flashMode = ImageCapture.FLASH_MODE_OFF
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.preview.surfaceProvider)
            }

            try {
                cameraProvider.unbindAll()
                bindToLifecycle = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
                bindToLifecycle.cameraControl.enableTorch(false)

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))

    }

    @SuppressLint("RestrictedApi")
    private fun takePicture() {
        binding.progressCircular.visibility = View.VISIBLE
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, pictureName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Pictures")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ).build()

        imageCapture.takePicture(outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    binding.progressCircular.visibility = View.GONE
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    binding.apply {
                        btnCapture.visibility = View.GONE
                        btnFlash.visibility = View.GONE
                        btnSwitch.visibility = View.GONE
                        preview.visibility = View.GONE
                        btnOk.visibility = View.VISIBLE
                        btnTakeOther.visibility = View.VISIBLE
                        imgPreview.visibility = View.VISIBLE
                        imgPreview.setImageURI(output.savedUri!!)
                        progressCircular.visibility = View.GONE
                    }
                    Log.d(TAG, msg)
                }
            })
    }

    private fun takeOtherPicture() {
        binding.apply {
            btnCapture.visibility = View.VISIBLE
            btnFlash.visibility = View.VISIBLE
            btnSwitch.visibility = View.VISIBLE
            preview.visibility = View.VISIBLE
            btnOk.visibility = View.GONE
            btnTakeOther.visibility = View.GONE
            imgPreview.visibility = View.GONE
        }
    }

    private fun onOk() {
        Toast.makeText(
            this,
            if (binding.imgPreview.drawable.saveImage()) "Imagen guardada."
            else "Imagen no guardada.",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun changeCamera() {
        cameraMode =
            if (cameraMode == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA
        startCamera(cameraMode)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun changeFlashMode() {
        flashMode = !flashMode
        bindToLifecycle.cameraControl.enableTorch(flashMode)
        binding.btnFlash.setImageDrawable(
            resources.getDrawable(
                if (flashMode) R.drawable.baseline_flash_off_24
                else R.drawable.baseline_flash_on_24,
                null
            )
        )
    }

    private fun Drawable.saveImage(): Boolean {
        val file = getDisc()
        var result = false

        if (!file.exists() && !file.mkdirs()) file.mkdir()

        val name = "$pictureName.$FILE_TYPE"
        val fileName = "${file.absolutePath}/$name"
        val newFile = File(fileName)

        try {
            val draw = this as BitmapDrawable
            val bitmap = draw.bitmap
            val fileOutPutStream = FileOutputStream(newFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutPutStream)
            result = true
            fileOutPutStream.flush()
            fileOutPutStream.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return result
    }

    private fun getDisc(): File {
        val file = this.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(file, APP_NAME)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val APP_NAME = "CameraX"
        private const val FILE_TYPE = "jpeg"
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

}