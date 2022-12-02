package com.my.example.cosmoarcanoid.models

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.my.example.cosmoarcanoid.R
import com.my.example.cosmoarcanoid.models.ArcPanel.Companion.LEFT
import com.my.example.cosmoarcanoid.models.ArcPanel.Companion.RIGHT
import com.my.example.cosmoarcanoid.models.ArcPanel.Companion.STOPPED

class ArcGameView(context: Context, attrs: AttributeSet) : SurfaceView(context, attrs),
    SurfaceHolder.Callback {

    private var sharedPreferences =
        context.getSharedPreferences("arcSharedP", AppCompatActivity.MODE_PRIVATE)

    private lateinit var thread: ArcGameThread
    private var canvas: Canvas? = null

    @Volatile
    private var touched: Boolean = false

    @Volatile
    private var touched_x: Int = 0

    @Volatile
    private var touched_y: Int = 0
    private var screenX: Int = 0
    private var screenY: Int = 0
    private lateinit var panel: ArcPanel
    private lateinit var ball: ArcBall
    private lateinit var bricks: ArrayList<ArcBrick>
    private var numBricks: Int = 0
    private var levelScore: Int = 0
    private var totalScore: Int = 0
    private var highScore: Int = 0
    private var lives: Int = 0
    private var won: Boolean = false
    private var lost: Boolean = false
    private var invisibles: String = ""

    init {
        holder.addCallback(this)
    }


    override fun surfaceCreated(p0: SurfaceHolder) {
        highScore = sharedPreferences.getInt("hs", 0)
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        val cont = sharedPreferences.getBoolean("continue", false)
        if (cont) {
            lives = sharedPreferences.getInt("lives", 0)
            totalScore = sharedPreferences.getInt("ts", 0)
            levelScore = sharedPreferences.getInt("ls", 0)
            invisibles = sharedPreferences.getString("invisible", "").toString()
        }
        this.isFocusable = true
        screenX = width
        screenY = height
        panel = ArcPanel(screenX, screenY)
        ball = ArcBall(screenX, screenY)
        thread = ArcGameThread(holder, this)
        thread.setRunning(true)
        thread.start()
        createBricksAndRestart(true)
    }

    override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
        screenX = p2
        screenY = p3
        surfaceCreated(p0)
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        saveState()
        thread.setRunning(false)
        thread.join()
    }

    fun saveState() {
        sharedPreferences.edit().putInt("hs", highScore).apply()
        sharedPreferences.edit().putInt("ts", totalScore).apply()
        sharedPreferences.edit().putInt("ls", levelScore).apply()
        sharedPreferences.edit().putInt("lives", lives).apply()
        var tmp = ""
        for (i: Int in 0 until numBricks) {
            tmp += if (bricks[i].getVisibility()) {
                '0'
            } else {
                '1'
            }
        }
        sharedPreferences.edit().putString("invisible", tmp).apply()
    }

    fun update(fps: Long) {
        panel.update(fps)
        ball.update(fps)
        //update score and visibility if any visible block was hit
        for (i in 0 until numBricks) {
            if (bricks[i].getVisibility()) {
                if (RectF.intersects(bricks[i].getRectangle(), ball.getRectangle())) {
                    bricks[i].setInvisible()
                    ball.reverseYVelocity()
                    levelScore += 10
                    totalScore += 10
                    if (highScore < totalScore) {
                        highScore = totalScore
                    }
                }
            }
        }

        //bounce the ball off the paddle
        if (RectF.intersects(panel.getRectangle(), ball.getRectangle())) {
            ball.setRandomXVelocity()
            ball.reverseYVelocity()
            ball.clearObstacleY(panel.getRectangle().top - 2f)
        }

        //ball missed the paddle and fell
        if (ball.getRectangle().bottom > screenY) {
            ball.reverseYVelocity()
            ball.clearObstacleY(screenY - 2f)
            lives--
            if (lives == 0) {
                lost = true
                thread.setPaused(true)
                createBricksAndRestart(false)
            }
        }

        //bounce off top
        if (ball.getRectangle().top < 0) {
            ball.reverseYVelocity()
            ball.clearObstacleY(22f)
        }

        //bounce off left
        if (ball.getRectangle().left < 0) {
            ball.reverseXVelocity()
            ball.clearObstacleX(2f)
        }

        //bounce off right
        if (ball.getRectangle().right > screenX - 10) {
            ball.reverseXVelocity()
            ball.clearObstacleX(screenX - 32f)
        }

        // Pause if cleared screen
        if (levelScore == numBricks * 10) {
            won = true
            thread.setPaused(true)
            createBricksAndRestart(false)
        }
    }

    //draw current state of game (all visible blocks plus paddle, ball and scores)
    override fun draw(canvas: Canvas?) {
        super.draw(canvas)
        val paint1 = Paint()
        val paint2 = Paint()
        paint1.color = resources.getColor(R.color.purple, resources.newTheme())
        paint2.color = resources.getColor(R.color.purple_200, resources.newTheme())
        try {
            canvas!!.drawColor(resources.getColor(R.color.purple_500, resources.newTheme()))
            canvas.drawRect(panel.getRectangle(), paint2)
            canvas.drawRect(ball.getRectangle(), paint2)
            for (i in 0 until numBricks) {
                if (bricks[i].getVisibility()) {
                    canvas.drawRect(bricks[i].getRectangle(), paint1)
                }
            }
            paint2.textSize = 40f
            canvas.drawText("Score: $totalScore   Lives: $lives   Highscore: $highScore", 10f, 50f, paint2)

            if (won) {
                paint2.textSize = 90f
                canvas.drawText("YOU HAVE WON!", 10f, screenY / 2f, paint2)
            }

            if (lost) {
                paint2.textSize = 90f
                canvas.drawText("YOU HAVE LOST!", 10f, screenY / 2f, paint2)
            }
        } catch (e: Exception) {
//            e.printStackTrace()
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        touched_x = event!!.x.toInt()
        touched_y = event.y.toInt()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touched = true
                if (won) {
                    won = false
                }
                if (lost) {
                    lost = false
                }
                thread.setPaused(false)
                if (event.x > screenX / 2) {
                    panel.setMovementState(RIGHT)
                } else {
                    panel.setMovementState(LEFT)
                }
            }
            MotionEvent.ACTION_MOVE -> touched = false
            MotionEvent.ACTION_UP -> {
                touched = true
                panel.setMovementState(STOPPED)
            }
            MotionEvent.ACTION_CANCEL -> touched = false
            MotionEvent.ACTION_OUTSIDE -> touched = false
        }
        return true
    }

    fun pause() {
        thread.setRunning(false)
        try {
            thread.join()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resume() {
        thread = ArcGameThread(holder, this)
        thread.setRunning(true)
        thread.start()
    }

    fun createBricksAndRestart(isRestore : Boolean) {
        ball.reset(screenX, screenY)
        val brickWidth = screenX / 10
        val brickHeight = screenY / 10
        bricks = ArrayList(200)
        numBricks = 0
        if(!isRestore){
            levelScore = 0
        }

        for (column in 0..9) {
            for (row in 0..4) {
                bricks.add(numBricks, ArcBrick(row, column, brickWidth, brickHeight))
                numBricks++
                if(isRestore && invisibles.length >= numBricks){
                    if (invisibles[numBricks-1] == '1'){
                        bricks[numBricks-1].setInvisible()
                    }
                }
            }
        }
        if (lives == 0) {
            totalScore = 0
            lives = 3
        }
    }
}