package com.imageblotter.balance.desktop

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.imageblotter.balance.BalanceGame
import com.imageblotter.balance.model.Difficulty
import com.imageblotter.balance.model.GameSetup
import com.imageblotter.balance.model.PlayerSetup

/**
 * Desktop build for development.
 *
 * Optional environment variables, handy for headless runs under Xvfb:
 *  - `BALANCE_SIZE=540x960`   window size
 *  - `BALANCE_DEMO=bots`      start straight into a game of computer players
 *  - `BALANCE_SHOTS=a.png@3,b.png@10`  save screenshots after the given seconds, then quit
 *  - `BALANCE_SHOT_TRIGGER=/dir`  save /dir/out.png whenever the file /dir/shot appears (for scripted UI tests)
 */
fun main() {
    val (width, height) = (System.getenv("BALANCE_SIZE") ?: "540x960").split("x").map { it.toInt() }
    val demo = when (System.getenv("BALANCE_DEMO")) {
        "bots" -> GameSetup(listOf(PlayerSetup("Bot 1", true), PlayerSetup("Bot 2", true)), Difficulty.NORMAL)
        else -> null
    }
    val shots = System.getenv("BALANCE_SHOTS")?.split(",")?.map {
        val (path, at) = it.split("@")
        path to at.toFloat()
    }.orEmpty()

    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Balance")
        setWindowedMode(width, height)
        setForegroundFPS(60)
        useVsync(true)
        setBackBufferConfig(8, 8, 8, 8, 24, 0, 4)
    }
    val game = BalanceGame(demo)
    val trigger = System.getenv("BALANCE_SHOT_TRIGGER")
    Lwjgl3Application(if (shots.isEmpty() && trigger == null) game else Screenshots(game, shots, trigger), config)
}

private class Screenshots(
    private val delegate: ApplicationListener,
    shots: List<Pair<String, Float>>,
    private val triggerDir: String?,
) : ApplicationListener by delegate {
    private val pending = shots.sortedBy { it.second }.toMutableList()
    private var elapsed = 0f

    private fun capture(path: String) {
        val pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        PixmapIO.writePNG(Gdx.files.absolute(path), pixmap, -1, true)
        pixmap.dispose()
    }

    override fun render() {
        delegate.render()
        elapsed += Gdx.graphics.deltaTime
        while (pending.isNotEmpty() && elapsed >= pending.first().second) capture(pending.removeAt(0).first)
        if (triggerDir != null) {
            val trigger = Gdx.files.absolute("$triggerDir/shot")
            if (trigger.exists()) {
                capture("$triggerDir/out.png")
                trigger.delete()
            }
        } else if (pending.isEmpty()) {
            Gdx.app.exit()
        }
    }
}
