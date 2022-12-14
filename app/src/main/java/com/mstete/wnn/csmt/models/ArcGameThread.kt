package com.mstete.wnn.csmt.models

import android.graphics.Canvas
import android.view.SurfaceHolder
import java.lang.Exception

class ArcGameThread(private val surfaceHolder: SurfaceHolder, private val gameView: ArcGameView) :
    Thread() {

    private var running: Boolean = false
    private val targetFPS = 60
    private var canvas: Canvas? = null
    private var paused : Boolean = false

    fun setRunning(isRunning: Boolean) {
        running = isRunning
    }

    fun setPaused(isPaused: Boolean) {
        paused = isPaused
    }

    override fun run() {
        var startTime : Long
        var timeMillis : Long
        var waitTime : Long
        val targetTime = (1000/targetFPS).toLong()

        while (running){
            startTime = System.nanoTime()
            canvas = null
            try {
                canvas = surfaceHolder.lockCanvas()
                synchronized(surfaceHolder){
                    if(!paused){
                        gameView.update(targetFPS.toLong())
                    }
                    gameView.draw(canvas!!)
                }
            } catch (_: Exception){
            } finally {
                if (canvas != null){
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception){
                    }
                }
            }
            timeMillis = (System.nanoTime() - startTime)/1000000
            waitTime = targetTime - timeMillis
            try {
                sleep(waitTime)
            } catch (_: Exception){
            }
        }
    }

}