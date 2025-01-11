package com.example.speechrecognitionapp

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

class Logger(private val context: Context, private val logFile: File) {

    private val ACCESS_TOKEN = "sl.CEXAfQ2jD1d1emmavuhLIJF5eiPkSQmFIzae8YwQWM-vhytxuEN1aGQaUG5q0acZxcZAU6mNtill-V-BRL_SmRv0-BNIXGB5ClBg_fn-nI61xnGq3corOs4E2lFrNaU7JA0-PdG09dzLXg6VvimP"

    companion object {
        private val TAG = Logger::class.simpleName
    }

    private var predictionCount = 0
    private val predictedKeywords = Collections.synchronizedList(mutableListOf<String>())
    private var logTimer: Timer? = null

    fun startLogging() {
        Log.d(TAG, "Logging started")
        logTimer = Timer()
        logTimer?.schedule(object : TimerTask() {
            override fun run() {
                writeLogsToFile()
            }
        }, 60000, 60000) // write to log file once per minute
    }

    fun stopLogging() {
        Log.d(TAG, "Logging stopped")
        if (predictionCount > 0 || predictedKeywords.isNotEmpty()) {
            writeLogsToFile()
        }
        logTimer?.cancel()
        logTimer = null
    }

    fun addPrediction(keyword: String) {
        predictionCount++
        predictedKeywords.add(keyword)
    }

    private fun writeLogsToFile() {
        Log.d(TAG, "Writing logs to file")
        try {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedTime = formatter.format(Date(System.currentTimeMillis()))

            FileWriter(logFile, true).use { writer ->
                PrintWriter(writer).use { pw ->
                    pw.println("Time: $formattedTime")
                    pw.println("Predictions in last interval: $predictionCount")
                    pw.println("Keywords: $predictedKeywords")
                    pw.println("---")
                }
            }
            resetCounters()
            Log.d(TAG, "Successfully update log file")
        } catch (e: Exception) {
            Log.d(TAG, "Error writing to log file: ${e.message}", e)
        }
    }

    private fun resetCounters() {
        predictionCount = 0
        predictedKeywords.clear()
    }

    // Upload the log file to Dropbox
    fun uploadFileToDropbox() {
        Log.d(TAG, "Uploading file...")

        if (ACCESS_TOKEN.isEmpty()) {
            Log.d(TAG, "no access token")
            return
        }

        Thread {
            try {
                // Configure the Dropbox client
                val config = DbxRequestConfig.newBuilder("Heltil").build()
                val client = DbxClientV2(config, ACCESS_TOKEN)

                // Upload the file
                FileInputStream(logFile).use { inputStream ->
                    client.files().uploadBuilder("/${logFile.name}")
                        .withMode(WriteMode.OVERWRITE)
                        .uploadAndFinish(inputStream)
                }

                Log.d(TAG, "File uploaded successfully")
            } catch (e: Exception) {
                Log.d(TAG, "Error uploading file: ${e.message}", e)
            }
        }.start()
    }

    // Start periodic file uploads using WorkManager
    fun startFileUploads() {
        Log.d(TAG, "Starting periodic uploads")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val workRequest = PeriodicWorkRequestBuilder<FileUploadWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.HOURS)
            .setInputData(workDataOf("logFilePath" to logFile.absolutePath))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "FileUploadToDropbox",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    // Stop periodic file uploads using WorkManager
    fun stopFileUploads() {
        Log.d(TAG, "Stopping periodic uploads")
        WorkManager.getInstance(context).cancelUniqueWork("FileUploadToDropbox")
    }
}