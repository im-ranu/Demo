package com.artivatic.classifier_offline

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.github.florent37.runtimepermission.kotlin.askPermission
import com.google.firebase.ml.common.modeldownload.FirebaseLocalModel
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager
import com.google.firebase.ml.custom.*
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
import kotlinx.android.synthetic.main.activity_camera.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.io.File.separator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*

class ClassifierCameraActivity: AppCompatActivity(){

    val docArray: Array<String> = arrayOf(
        "DL",
        "Voter",
        "aadhar_back",
        "aadhar_front",
        "aadhar_full",
        "others",
        "pan",
        "passport_back",
        "passport_front",
        "voter_back"
    )

    companion object {
        /** Dimensions of inputs.  */
        const val DIM_IMG_SIZE_X = 224
        const val DIM_IMG_SIZE_Y = 224
        const val DIM_BATCH_SIZE = 1
        const val DIM_PIXEL_SIZE = 3
        const val IMAGE_MEAN = 128
        private const val IMAGE_STD = 255.0f
    }


    var rootObject= JSONObject()
    private val TAG = "Classifier_Offline"
    lateinit var imgURI : Uri
    var inputData: ByteArray? = null
    var isImageTaken : Boolean = false
    var isResult : Boolean = false

    private lateinit var currentBitmap: Bitmap

    private val intValues = IntArray(DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y)
    private var imgData: ByteBuffer = ByteBuffer.allocateDirect(
        4 * DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE
    )
    private lateinit var fireBaseInterpreter: FirebaseModelInterpreter
    private lateinit var inputOutputOptions: FirebaseModelInputOutputOptions
    lateinit var imagePath : String
    var isPath = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStart() {
        super.onStart()
        if (!isImageTaken)
        checkpermission();

        if(isResult){
            Log.v(TAG,"got image");
            imgData.order(ByteOrder.nativeOrder())


//            //Load a local model using the FirebaseLocalModelSource Builder class
            val fireBaseLocalModelSource = FirebaseLocalModel.Builder("kyc")
                .setAssetFilePath("kyc_offline_12_AUG_includes_aadhar_full.tflite")
                .build()
//
//            //Registering the model loaded above with the ModelManager Singleton
            FirebaseModelManager.getInstance().registerLocalModel(fireBaseLocalModelSource)
//
            val firebaseModelOptions = FirebaseModelOptions.Builder()
                .setLocalModelName("kyc")
                .build()
//
//
            fireBaseInterpreter = FirebaseModelInterpreter.getInstance(firebaseModelOptions)!!
//
//            //Specify the input and outputs of the model
            inputOutputOptions = FirebaseModelInputOutputOptions.Builder()
                .setInputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 224, 224, 3))
                .setOutputFormat(0, FirebaseModelDataType.FLOAT32, intArrayOf(1, 10))
                .build()
//

            inputData?.let { it1 -> convertByteArrayToBitmap(it1) }


        }
    }



    override fun onResume() {
        super.onResume()
        setContentView(R.layout.activity_camera)

        supportActionBar?.hide()
        homeprogress.visibility = View.VISIBLE

    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun convertByteArrayToBitmap(byteArray: ByteArray) {
        //Handle this shit in bg
        doAsync {


            val exifInterface = androidx.exifinterface.media.ExifInterface(ByteArrayInputStream(byteArray))
            val orientation =
                exifInterface.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)
            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
//            imgSaver.saveImageToExternal("KYC" + currentDateandTime, bitmap)
            //to fix images coming out to be rotated
            //https://github.com/google/cameraview/issues/22#issuecomment-269321811
            saveImage(bitmap,this@ClassifierCameraActivity,"KYC-Offline")
            val m = Matrix()
            when (orientation) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90F)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180F)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270F)
            }
            //Create a new bitmap with fixed rotation
            //Crop a part of image that's inside viewfinder and perform detection on that image
            //https://stackoverflow.com/a/8180576/5471095
            //TODO : Need to find a better way to do this than creating a new bitmap
            val cropX = (bitmap.width * 0.2).toInt()
            val cropY = (bitmap.height * 0.25).toInt()

            currentBitmap =
                Bitmap.createBitmap(bitmap, cropX, cropY, bitmap.width - 2 * cropX, bitmap.height - 2 * cropY, m, true)
            //free up the original bitmap
            bitmap.recycle()

            //create a scaled bitmap for Tensorflow
            val scaledBitmap = Bitmap.createScaledBitmap(currentBitmap, 224, 224, false)
            uiThread {
        //View view = getLayoutInflater().inflate(R.layout.progress);

                setImageClassifier(scaledBitmap)
            }
        }
    }


    fun checkpermission(){

        askPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE){
            Log.v(TAG,"Permission granted");
//            imgSaver = ImageSaver(this)

            startCropActivity();
            //all of your permissions have been accepted by the user
        }.onDeclined { e ->
            //at least one permission have been declined by the user
        }

    }


    fun startCropActivity(){
        CropImage.activity()
            .setGuidelines(CropImageView.Guidelines.ON)
            .start(this);
    }

    @Throws(IOException::class)
    private fun readBytes(context: Context, uri: Uri): ByteArray? =
        context.contentResolver.openInputStream(uri)?.buffered()?.use { it.readBytes() }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            val result = CropImage.getActivityResult(data)
            if (resultCode == Activity.RESULT_OK) {
                val resultUri = result.uri

                imgURI = resultUri
                isImageTaken = true
                isResult = true
                Log.e("Saved", resultUri.path)

                try {
                    inputData = readBytes(this, resultUri)
                } catch (e: IOException) {
                    e.printStackTrace()
                }


            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                val error = result.error
            }
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap?): ByteBuffer {
        //Clear the ByteBuffer for a new image
        imgData.rewind()
        bitmap?.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        // Convert the image to floating point.
        var pixel = 0
        for (i in 0 until DIM_IMG_SIZE_X) {
            for (j in 0 until
                    DIM_IMG_SIZE_Y) {
                val currPixel = intValues[pixel++]
                imgData.putFloat(((currPixel shr 16 and 0xFF) - 0) / IMAGE_STD)
                imgData.putFloat(((currPixel shr 8 and 0xFF) - 0) / IMAGE_STD)
                imgData.putFloat(((currPixel and 0xFF) - 0) / IMAGE_STD)
            }
        }
        return imgData
    }



    @RequiresApi(Build.VERSION_CODES.O)
    private fun setImageClassifier(bitmap: Bitmap) {

        val inputs = FirebaseModelInputs.Builder()
            .add(convertBitmapToByteBuffer(bitmap)) // add() as many input arrays as your model requires
            .build()
        fireBaseInterpreter.run(inputs, inputOutputOptions)
            ?.addOnSuccessListener { result ->


                val output = result.getOutput<Array<FloatArray>>(0)
                val probabilities = output[0]

                val reader = BufferedReader(
                    InputStreamReader(assets.open("retrained_labels.txt")) as Reader?
                )
                for (i in probabilities.indices) {
                    val label = reader.readLine()
                    Log.i("MLKit", String.format("%s: %1.4f", docArray[i], probabilities[i]))
                }

                val docAccuracy = probabilities.max();
                var docType = docAccuracy?.let { probabilities.indexOf(it) }
//                progressBar.visibility = View.INVISIBLE








//               ImageSaver(this).setExternal(true).setFileName("KYC"+currentDateandTime).save(bitmap);

//                    val imgPath = imgSaver.getImgPath();
//
//
//                    val base64ImageString = encoder(imgPath)
//
//                    Log.e("Base64 :****",base64ImageString);


//                    Log.e("MainActivity",imgPath)

                var imgBase64 = getEncoded64ImageStringFromBitmap(bitmap)
//
                Log.e("Successs",imgBase64)
                generateJSON(docArray[docType!!],imgBase64)


//                detectImg(imgURI,docArray[docType!!]);


            }
            ?.addOnFailureListener {
                it.printStackTrace()
            }
            ?.addOnCompleteListener {
            }






//               ImageSaver(this).setExternal(true).setFileName("KYC"+currentDateandTime).save(bitmap);

//                    val imgPath = imgSaver.getImgPath();
//
//
//                    val base64ImageString = encoder(imgPath)
//
//                    Log.e("Base64 :****",base64ImageString);


//                    Log.e("MainActivity",imgPath)

//                var imgBase64 = imgSaver.getEncoded64ImageStringFromBitmap(bitmap)
//
//                Log.e("Successs",imgBase64)


//                detectImg(imgURI,docArray[docType!!]);



    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun generateJSON(docName: String,imgBase : String) {





        var detectDocument: JSONObject = JSONObject();
        try {
            if (isPath)
            detectDocument.put("imagePath", imagePath)
            detectDocument.put("documentType", docName)
            detectDocument.put("base64", imgBase)

            Log.e("JSON Created", detectDocument.toString())
            homeprogress.visibility = View.GONE

            val returnIntent = Intent()
            returnIntent.putExtra("result", detectDocument.toString())
            setResult(Activity.RESULT_OK, returnIntent)
            finish()
//    rv_json.bindJson(detectDocument)
//    rv_json.setTextSize(18f)

        } catch (e: JSONException) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


    }
    private fun saveImage(bitmap: Bitmap, context: Context, folderName: String) {

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss")
        val currentDateandTime = sdf.format(Date())
        Log.e("DateTime", currentDateandTime);

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = contentValues()
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/" + folderName)
            values.put(MediaStore.Images.Media.IS_PENDING, true)
            // RELATIVE_PATH and IS_PENDING are introduced in API 29.


            val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                saveImageToStream(bitmap, context.contentResolver.openOutputStream(uri))
                values.put(MediaStore.Images.Media.IS_PENDING, false)
                context.contentResolver.update(uri, values, null, null)
            }
        } else {
            val directory = File(Environment.getExternalStorageDirectory().toString() + separator + folderName)
            // getExternalStorageDirectory is deprecated in API 29

            if (!directory.exists()) {
                directory.mkdirs()
            }
            val fileName = currentDateandTime + ".png"
            val file = File(directory, fileName)
            saveImageToStream(bitmap, FileOutputStream(file))
            if (file.absolutePath != null) {
                val values = contentValues()
                values.put(MediaStore.Images.Media.DATA, file.absolutePath)
                Log.e("Path",file.absolutePath);
                imagePath = file.absolutePath
                isPath = true
                // .DATA is deprecated in API 29
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }
        }

    }

    private fun contentValues() : ContentValues {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        return values
    }

    private fun saveImageToStream(bitmap: Bitmap, outputStream: OutputStream?) {
        if (outputStream != null) {
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    fun getEncoded64ImageStringFromBitmap(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        val byteFormat = stream.toByteArray()
        // get the base 64 string

        return Base64.encodeToString(byteFormat, Base64.NO_WRAP)
    }



}