package com.imageblotter.balance

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.physics.bullet.Bullet
import com.imageblotter.balance.audio.Sfx
import com.imageblotter.balance.model.Difficulty
import com.imageblotter.balance.model.GameSetup
import com.imageblotter.balance.model.PlayerSetup
import com.imageblotter.balance.screens.GameScreen
import com.imageblotter.balance.screens.MenuScreen
import com.imageblotter.balance.ui.UiKit

/** Who sits at a table seat. */
enum class Seat { HUMAN, COMPUTER, EMPTY }

/**
 * Entry point shared by every platform. [autoStart] skips the menu, which the desktop launcher uses for
 * demos and screenshots.
 */
class BalanceGame(private val autoStart: GameSetup? = null) : Game() {
    lateinit var ui: UiKit
        private set
    lateinit var sfx: Sfx
        private set
    private lateinit var prefs: Preferences

    override fun create() {
        Bullet.init()
        prefs = Gdx.app.getPreferences("balance")
        ui = UiKit()
        sfx = Sfx().also { it.enabled = prefs.getBoolean(KEY_SOUND, true) }
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        if (autoStart != null) startGame(autoStart) else showMenu()
    }

    fun showMenu() = switchTo(MenuScreen(this))

    fun startGame(setup: GameSetup) = switchTo(GameScreen(this, setup))

    private fun switchTo(next: com.badlogic.gdx.Screen) {
        val previous = screen
        setScreen(next)
        previous?.dispose()
    }

    var soundEnabled: Boolean
        get() = sfx.enabled
        set(value) {
            sfx.enabled = value
            prefs.putBoolean(KEY_SOUND, value).flush()
        }

    fun loadSeats(): List<Seat> = List(MAX_SEATS) { i ->
        val default = when (i) { 0 -> Seat.HUMAN; 1 -> Seat.COMPUTER; else -> Seat.EMPTY }
        Seat.entries.getOrElse(prefs.getInteger("seat$i", default.ordinal)) { default }
    }

    fun loadDifficulty(): Difficulty =
        Difficulty.entries.getOrElse(prefs.getInteger(KEY_DIFFICULTY, Difficulty.NORMAL.ordinal)) { Difficulty.NORMAL }

    fun saveMenu(seats: List<Seat>, difficulty: Difficulty) {
        seats.forEachIndexed { i, seat -> prefs.putInteger("seat$i", seat.ordinal) }
        prefs.putInteger(KEY_DIFFICULTY, difficulty.ordinal).flush()
    }

    override fun dispose() {
        screen?.dispose()
        sfx.dispose()
        ui.dispose()
    }

    companion object {
        const val MAX_SEATS = 4
        private const val KEY_SOUND = "sound"
        private const val KEY_DIFFICULTY = "difficulty"

        fun setupFrom(seats: List<Seat>, difficulty: Difficulty): GameSetup = GameSetup(
            seats.mapIndexedNotNull { i, seat ->
                when (seat) {
                    Seat.HUMAN -> PlayerSetup("Player ${i + 1}", false)
                    Seat.COMPUTER -> PlayerSetup("Bot ${i + 1}", true)
                    Seat.EMPTY -> null
                }
            },
            difficulty,
        )
    }
}
