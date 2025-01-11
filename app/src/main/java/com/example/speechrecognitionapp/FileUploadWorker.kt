package com.example.speechrecognitionapp

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import java.io.File
import java.io.FileInputStream

class FileUploadWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val ACCESS_TOKEN = "sl.CEXAfQ2jD1d1emmavuhLIJF5eiPkSQmFIzae8YwQWM-vhytxuEN1aGQaUG5q0acZxcZAU6mNtill-V-BRL_SmRv0-BNIXGB5ClBg_fn-nI61xnGq3corOs4E2lFrNaU7JA0-PdG09dzLXg6VvimP"

    companion object {
        private val TAG = FileUploadWorker::class.simpleName
    }

    override fun doWork(): Result {
        Log.d(TAG, "Uploading file via WorkManager...")

        if (ACCESS_TOKEN.isEmpty()) {
            Log.d(TAG, "no access token")
            return Result.failure()
        }

        val logFilePath = inputData.getString("logFilePath") ?: return Result.failure()

        val logFile = File(logFilePath)
        if (!logFile.exists()) {
            Log.d(TAG, "Log file does not exist: $logFilePath")
            return Result.failure()
        }

        try {
            val config = DbxRequestConfig.newBuilder("Heltil").build()
            val client = DbxClientV2(config, ACCESS_TOKEN)

            FileInputStream(logFile).use { inputStream ->
                client.files().uploadBuilder("/${logFile.name}")
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(inputStream)
            }
            Log.d(TAG, "Upload successful")
            return Result.success()
        } catch (e: Exception) {
            Log.d(TAG, "Error uploading file: ${e.message}", e)
            return Result.retry()
        }
    }
}
