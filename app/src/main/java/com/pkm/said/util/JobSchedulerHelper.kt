package com.pkm.said.util

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.pkm.said.job.SensorJobService

object JobSchedulerHelper {
    fun scheduleSensorJob(context: Context) {
        val component = ComponentName(context, SensorJobService::class.java)
        val jobInfoBuilder = JobInfo.Builder(1, component)
            .setPersisted(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            jobInfoBuilder.setMinimumLatency(15 * 60 * 1000L)
        } else {
            jobInfoBuilder.setPeriodic(15 * 60 * 1000L)
        }

        val jobInfo = jobInfoBuilder.build()

        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        scheduler.schedule(jobInfo)
    }
}