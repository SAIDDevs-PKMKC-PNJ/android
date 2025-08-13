package com.pkm.said.job

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import android.os.Build
import com.pkm.said.service.SensorService

class SensorJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val intent = Intent(this, SensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        return false // No more work to do
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true // Retry if job is stopped
    }
}

