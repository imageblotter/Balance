package com.imageblotter.balance.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.imageblotter.balance.BalanceGame
import com.imageblotter.balance.Seat
import com.imageblotter.balance.model.Difficulty
import com.imageblotter.balance.model.GameState
import com.imageblotter.balance.model.Piece
import com.imageblotter.balance.model.PieceColor
import com.imageblotter.balance.model.PieceShape
import com.imageblotter.balance.model.PieceType
import com.imageblotter.balance.physics.BalanceWorld
import com.imageblotter.balance.render.Scene3D
import com.imageblotter.balance.ui.UiKit

/** Seat setup in front of a slowly turning view of the plate. */
class MenuScreen(private val app: BalanceGame) : Screen {
    private val stage = Stage(ExtendViewport(480f, 720f))
    private val scene = Scene3D()
    private val world = BalanceWorld()
    private val seats = app.loadSeats().toMutableList()
    private var difficulty = app.loadDifficulty()
    private val seatButtons = mutableListOf<TextButton>()
    private lateinit var difficultyButton: TextButton
    private lateinit var startButton: TextButton
    private lateinit var soundButton: TextButton
    private var time = 0f
    private var dropTimer = 0.5f
    private var nextPieceId = 1000

    init {
        build()
        Gdx.input.inputProcessor = InputMultiplexer(stage, object : InputAdapter() {
            override fun keyDown(keycode: Int): Boolean {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) Gdx.app.exit()
                return true
            }
        })
    }

    private fun seatText(i: Int) = when (seats[i]) {
        Seat.HUMAN -> "Human"
        Seat.COMPUTER -> "Computer"
        Seat.EMPTY -> "Empty"
    }

    private fun activeSeats() = seats.count { it != Seat.EMPTY }

    private fun cycleSeat(i: Int) {
        val order = if (i < 2) listOf(Seat.HUMAN, Seat.COMPUTER) else Seat.entries
        seats[i] = order[(order.indexOf(seats[i]) + 1) % order.size]
        seatButtons[i].setText(seatText(i))
        app.sfx.play(com.imageblotter.balance.audio.Sfx.Effect.CLICK)
    }

    private fun build() {
        val ui = app.ui
        val root = Table().apply {
            setFillParent(true)
            touchable = Touchable.childrenOnly
        }

        root.add(ui.label("BALANCE", 1.25f, UiKit.GOLD)).padTop(26f).row()
        root.add(ui.label("Place every piece. Don't tip the plate.", 0.3f)).padBottom(14f).row()
        root.add().expandY().row()

        val card = Table().apply {
            background = ui.panel(Color(0.05f, 0.07f, 0.1f, 0.78f))
            pad(18f)
        }
        for (i in 0 until BalanceGame.MAX_SEATS) {
            card.add(ui.label("Seat ${i + 1}", 0.36f, UiKit.SEATS[i])).left().padRight(30f).minWidth(110f)
            val button = ui.button(seatText(i), UiKit.NEUTRAL, scale = 0.34f) { cycleSeat(i); refresh() }
            seatButtons += button
            card.add(button).size(170f, 46f).padBottom(8f).row()
        }
        card.add(ui.label("Computer skill", 0.3f)).left().padTop(6f)
        difficultyButton = ui.button(difficulty.label, UiKit.NEUTRAL, scale = 0.34f) {
            difficulty = Difficulty.entries[(difficulty.ordinal + 1) % Difficulty.entries.size]
            difficultyButton.setText(difficulty.label)
        }
        card.add(difficultyButton).size(170f, 46f).padTop(6f).row()
        root.add(card).padBottom(14f).row()

        startButton = ui.button("Start game", UiKit.PRIMARY, scale = 0.5f) { start() }
        root.add(startButton).size(270f, 68f).padBottom(8f).row()

        val extras = Table().apply {
            add(ui.button("How to play", UiKit.NEUTRAL, scale = 0.3f) { showHelp() }).size(140f, 42f).padRight(8f)
            soundButton = ui.button(soundText(), UiKit.NEUTRAL, scale = 0.3f) {
                app.soundEnabled = !app.soundEnabled
                soundButton.setText(soundText())
            }
            add(soundButton).size(140f, 42f)
        }
        root.add(extras).padBottom(20f)
        stage.addActor(root)
        refresh()
    }

    private fun soundText() = if (app.soundEnabled) "Sound: on" else "Sound: off"

    private fun refresh() {
        startButton.isDisabled = activeSeats() < GameState.MIN_PLAYERS
        startButton.touchable = if (startButton.isDisabled) Touchable.disabled else Touchable.enabled
    }

    private fun start() {
        app.saveMenu(seats, difficulty)
        app.startGame(BalanceGame.setupFrom(seats, difficulty))
    }

    private fun showHelp() {
        val ui = app.ui
        val dialog = Table().apply {
            setFillParent(true)
            touchable = Touchable.enabled
            background = ui.panel(Color(0f, 0f, 0f, 0.55f))
        }
        val card = Table().apply {
            background = ui.panel(Color(0.08f, 0.1f, 0.15f, 0.97f))
            pad(22f)
        }
        card.add(ui.label("How to play", 0.5f, UiKit.GOLD)).padBottom(12f).row()
        val text = ui.label(HELP, 0.3f).apply { setWrap(true); setAlignment(Align.left) }
        card.add(text).width(380f).padBottom(14f).row()
        card.add(ui.button("Got it", UiKit.PRIMARY, scale = 0.4f) { dialog.remove() }).size(160f, 50f)
        dialog.add(card)
        stage.addActor(dialog)
    }

    private fun updateBackdrop(delta: Float) {
        time += delta
        scene.setOrbit(time * 6f, 30f, 30f)
        dropTimer -= delta
        if (dropTimer <= 0f && world.pieces.size < 9) {
            dropTimer = 0.7f
            val colors = PieceColor.entries
            val piece = Piece(nextPieceId++, PieceType(colors[(nextPieceId * 5) % colors.size], PieceShape.entries[nextPieceId % 2]))
            val angle = nextPieceId * 2.4f
            val r = 1f + (nextPieceId % 4) * 1.4f
            world.addPiece(
                piece,
                Vector3(kotlin.math.cos(angle) * r, world.stackTopY() + 1.5f, kotlin.math.sin(angle) * r),
                angle * 30f,
                false,
            )
        }
        world.step(delta)
        world.detectFallen()
        world.removeFallen()
        scene.update(delta)
    }

    override fun render(delta: Float) {
        val dt = delta.coerceAtMost(1f / 30f)
        updateBackdrop(dt)
        Gdx.gl.glClearColor(0.55f, 0.72f, 0.85f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        scene.render(world, null)
        stage.act(dt)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        scene.resize(width, height)
    }

    override fun show() {}
    override fun hide() {}
    override fun pause() {}
    override fun resume() {}

    override fun dispose() {
        stage.dispose()
        scene.dispose()
        world.dispose()
    }

    private companion object {
        const val HELP =
            "Every player starts with 12 pieces: a tall and a wide one in each of the six die colours.\n\n" +
                "On your turn roll the die and place a piece of the rolled colour on the hanging plate. " +
                "If you have none of that colour left, place any piece.\n\n" +
                "Drag the piece over the plate, turn it with two fingers or the buttons, and let go to drop it.\n\n" +
                "Every piece that falls off goes into the inventory of the player who just placed. " +
                "The first player without pieces wins."
    }
}
