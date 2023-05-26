package com.example.automobile

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.InterpreterFactory
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


class MainActivity : AppCompatActivity() {
    val CAMERA_PERMISSION = arrayOf(android.Manifest.permission.CAMERA)
    val STORAGE_PERMISSION = arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

//    권한 플래그 값 정의
    val FLAG_PERM_CAMERA = 98
    val FLAG_PERM_STORAGE = 99

//    카메라와 갤러리를 호출하는 플래그
    val FLAG_REQ_CAMERA = 101
    val FLAG_REA_STORAGE = 102

    private val labels = mutableListOf<String>()
//

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if(checkPermission(STORAGE_PERMISSION, FLAG_PERM_STORAGE)){
            setViews()
        }

    }
    private fun ObjectDetection(bitmap: Bitmap){
        val imageProcessor: ImageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(640, 640, ResizeOp.ResizeMethod.BILINEAR))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val tflite = Interpreter(loadModelFile(this, "yolov5s-fp16.tflite"))

        val ASSOCIATED_AXIS_LABELS = "labels.txt"
        labels.addAll(FileUtil.loadLabels(this, ASSOCIATED_AXIS_LABELS))

        Log.d("txt", labels.toString())

        val outputTensorIndex = 0
        val outputTensorShape = tflite.getOutputTensor(outputTensorIndex).shape()
        val outputDataType = tflite.getOutputTensor(outputTensorIndex).dataType()
        val outputTensor = TensorBuffer.createFixedSize(outputTensorShape, outputDataType)

        tflite.run(tensorImage.getBuffer(), outputTensor.buffer)
        tflite.close()
        // 객체 탐지 결과 처리
        val detectionThreshold = 0.3 // 탐지 신뢰도 임계값

        val numDetection = outputTensor.shape[1]
        val numAttributes = outputTensor.shape[2]
        val outputBuffer = outputTensor.buffer
        for (i in 0 until numDetection) {
            val confidence = outputBuffer.getFloat(i * numAttributes + 4)
            if (confidence >= detectionThreshold) {
                val classIndex = outputBuffer.getInt(i * numAttributes + 0)
                val className = classIndex
                val startX = outputBuffer.getFloat(i * numAttributes + 1)
                val startY = outputBuffer.getFloat(i * numAttributes + 2)
                val endX = outputBuffer.getFloat(i * numAttributes + 3)
                val endY = outputBuffer.getFloat(i * numAttributes + 4)


                // 객체 정보 로그로 출력
//                Log.d("Object Detection", "Class: $className")
//                Log.d("Object Detection", "Confidence: $confidence")
//                Log.d("Object Detection", "Bounding Box: ($startX, $startY), ($endX, $endY)")
            }
        }
        Log.d("결과", outputTensor.buffer.toString())

//        출력 객체 생성
//        val probabilityBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 1001), DataType.UINT8)
//
//        try {
//            val tfliteModel: MappedByteBuffer = FileUtil.loadMappedFile(
//                this,
//                "yolov5s-fp16.tflite"
//            )
//            val tflite: InterpreterApi = InterpreterFactory().create(
//                tfliteModel, InterpreterApi.Options()
//            )
//            if(null != tflite) {
//                tflite.run(tensorImage.getBuffer(), probabilityBuffer.getBuffer());
//            }
//            val ASSOCIATED_AXIS_LABELS = "labels.txt"
//            List<String> associatedAxisLabels = null
//
//            try {
//                associatedAxisLabels = FileUtil.loadLabels(this, ASSOCIATED_AXIS_LABELS);
//            } catch (e: IOException) {
//                Log.e("tfliteSupport", "Error reading label file", e);
//
//        } catch (e: IOException) {
//            Log.e("tfliteSupport", "Error reading model", e)
//        }
    }
    fun loadModelFile(activity: Activity, modelPath:String):MappedByteBuffer{
        val fileDescriptor = activity.assets.openFd(modelPath)
        val inputStream:FileInputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel:FileChannel = inputStream.channel
        var startOffSet = fileDescriptor.startOffset
        var declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffSet, declaredLength)
    }
    private fun setViews(){
//        변수 선언 필요한 곳에서 사용하는게 좋음(전역변수로 하니 앱이 실행되지 않음)
        val btn_camera = findViewById<Button>(R.id.btn_camera)
        val btn_gallery = findViewById<Button>(R.id.btn_gallery)
        btn_camera.setOnClickListener{
            openCamera()
        }
        btn_gallery.setOnClickListener{
            openGallery()
        }
    }
    private fun openGallery(){
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = MediaStore.Images.Media.CONTENT_TYPE
        startActivityForResult(intent, FLAG_PERM_STORAGE)
    }
    private fun openCamera(){
        if(checkPermission(CAMERA_PERMISSION, FLAG_PERM_CAMERA)){
            val intent:Intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(intent, FLAG_PERM_CAMERA)
        }
    }
    fun checkPermission(permissions:Array<out String>, flag:Int):Boolean{
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            for(permission in permissions){
                if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(this, permissions, flag)
                    return false
                }
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode){
            FLAG_PERM_STORAGE ->{
                for(grant in grantResults){
                    if(grant != PackageManager.PERMISSION_GRANTED){
//                        권한이 승인되지 않은 경우, return을 사용하여 메소드를 종료시킴
                        Toast.makeText(this, "저장소 권한을 승인해야지만 앱을 사용하실 수 있습니다.", Toast.LENGTH_SHORT).show()
                        finish()
                        return
                    }
                }
//                카메라 호출
                setViews()
            }
            FLAG_PERM_CAMERA ->{
                for(grant in grantResults){
                    if(grant != PackageManager.PERMISSION_GRANTED){
                        Toast.makeText(this, "저장소 권한을 승인해야지만 앱을 사용하실 수 있습니다.", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
                openCamera()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val image_View = findViewById<ImageView>(R.id.image_View)
        if(resultCode == Activity.RESULT_OK){
            when(requestCode){
                FLAG_PERM_CAMERA ->{
                    if(data?.extras?.get("data") != null){
                        //                    카메라로 방금 촬영한 이미지를 미리 만들어 높은 이미지뷰로 전달
                        val bitmap = data?.extras?.get("data") as Bitmap

                        image_View.setImageBitmap(bitmap)
                        ObjectDetection(bitmap)
                    }
                }
                FLAG_PERM_STORAGE -> {
                    var uri = data?.data
                    image_View.setImageURI(uri)
                }
            }
        }
    }
}