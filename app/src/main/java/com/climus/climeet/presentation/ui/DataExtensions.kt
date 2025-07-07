package com.climus.climeet.presentation.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.climus.climeet.presentation.util.Constants.TAG
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal fun Uri.toMultiPart(context: Context): MultipartBody.Part? {
    getRealPathFromUri(this, context)?.let {
        val file = File(it)
        val requestFile = file.asRequestBody("image/jpg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", file.name, requestFile)
    } ?: run {
        return null
    }
}

internal fun generateFileName(): String {
    val timestamp = System.currentTimeMillis()
    return "image_$timestamp.jpg"
}

// 절대경로 변환
private fun getRealPathFromUri(
    uri: Uri,
    context: Context,
): String? =
    try {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                cursor.getString(columnIndex)
            } else {
                null
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "이미지 경로 조회에 실패했습니다", e)
        null
    }

// 프로필 이미지 수정용
internal fun Uri.toMultiPartImage(context: Context): MultipartBody.Part? {
    getRealPathFromUri(this, context)?.let {
        val file = File(it)
        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("image", file.name, requestFile)
    } ?: run {
        Log.e("toMultiPartImage", "Error creating multipart image")
        return null
    }
}

fun Uri.toVideoThumbnail(context: Context): MultipartBody.Part? =
    try {
        val mediaMetadataRetriever = MediaMetadataRetriever()
        try {
            mediaMetadataRetriever.setDataSource(context, this)
            mediaMetadataRetriever.frameAtTime?.toMultiPart(context)
        } finally {
            mediaMetadataRetriever.release()
        }
    } catch (e: Exception) {
        Log.w(TAG, "동영상 처리에 실패했습니다", e)
        null
    }

fun Bitmap.toMultiPart(context: Context): MultipartBody.Part? =
    try {
        val filesDir = context.filesDir
        val imageFile = File(filesDir, "image.jpg")
        FileOutputStream(imageFile).use { outputStream ->
            compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        }
        val requestFile = imageFile.asRequestBody("image/jpg".toMediaTypeOrNull())
        MultipartBody.Part.createFormData("file", imageFile.name, requestFile)
    } catch (e: Exception) {
        Log.w(TAG, "이미지 파일 저장에 실패했습니다", e)
        null
    }

// 프로필 이미지 수정용
fun Bitmap.toMultiPartImage(context: Context): MultipartBody.Part? =
    try {
        val filesDir = context.filesDir
        val imageFile = File(filesDir, "image.jpg")
        FileOutputStream(imageFile).use { outputStream ->
            compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        }
        val requestFile = imageFile.asRequestBody("image/jpg".toMediaTypeOrNull())
        MultipartBody.Part.createFormData("image", imageFile.name, requestFile)
    } catch (e: Exception) {
        Log.w(TAG, "프로필 이미지 파일 저장에 실패했습니다", e)
        null
    }

// 프로필 이미지 수정용
fun Bitmap.saveCameraImage(context: Context): Uri? {
    val filename = "${System.currentTimeMillis()}.jpg"
    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    val file = File(storageDir, filename)
    return try {
        FileOutputStream(file).use { outputStream ->
            compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
        }
        Log.d("saveCameraImage", "카메라 이미지 저장: ${file.absolutePath}")
        Uri.fromFile(file)
    } catch (e: IOException) {
        Log.e("saveCameraImage", "카메라 이미지 저장 실패", e)
        null
    }
}
