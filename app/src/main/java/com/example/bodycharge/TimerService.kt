package com.example.bodycharge

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import java.util.*
import kotlin.concurrent.timerTask
open class TimerService : Service() {
    var timer: Timer? = null
    var timing: Long = 0 // temps en millisecondes
    private val binder = LocalBinder()


    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
        override fun toString(): String {
            return ""
        }


    }


    override fun onBind(intent: Intent): IBinder {
        return binder
    }


    fun startService() {

        timer = Timer()

    }

    fun stopService() {
            timer?.cancel()
            timer = null
    }



    @JvmName("setTiming1")
    fun setTiming(time : Long){
        timing = time
    }

    fun fonctionTime(code :()-> Unit = {}){
        val onCode = code
        val task = timerTask {
            onCode()
        }
        timer?.scheduleAtFixedRate(task, 0, timing)
    }
}
