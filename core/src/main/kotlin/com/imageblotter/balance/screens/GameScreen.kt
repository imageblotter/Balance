package com.imageblotter.balance.screens

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.imageblotter.balance.BalanceGame
import com.imageblotter.balance.ai.AiPlayer
import com.imageblotter.balance.ai.Placement
import com.imageblotter.balance.audio.Sfx
import com.imageblotter.balance.model.GameSetup
import com.imageblotter.balance.model.GameState
import com.imageblotter.balance.model.Piece
import com.imageblotter.balance.physics.BalanceWorld
import com.imageblotter.balance.physics.Dimensions
import com.imageblotter.balance.render.HeldPiece
import com.imageblotter.balance.render.Scene3D
import com.imageblotter.balance.ui.Hud
import com.imageblotter.balance.ui.UiKit
import kotlin.math.atan2
import kotlin.math.hypot

/** Plays one match: drives the turn flow, the physics, the 3D view and the HUD. */
class GameScreen(private val app: BalanceGame, private val setup: GameSetup) : Screen {
    private enum class Phase { BEGIN_TURN, WAIT_ROLL, ROLLING, CHOOSING, PLACING, AI_AIMING, SETTLING, REPORT, GAME_OVER }

    private val game = GameState(setup)
    private val world = BalanceWorld()
    private val scene = Scene3D()
    private val ai = AiPlayer(setup.difficulty)
    private val hud = Hud(
        app.ui,
        onMenu = { app.showMenu() },
        onCameraTurn = { scene.orbitBy(it, 0f) },
        onSoundToggle = { app.soundEnabled = !app.soundEnabled; app.soundEnabled },
        soundOn = app.soundEnabled,
    )

    private var phase = Phase.BEGIN_TURN
    private var timer = 0f
    private var held: HeldPiece? = null
    private var restartCount = 0

    // AI aiming.
    private var aiPlacement: Placement? = null
    private val aiStart = Vector3()
    private var aiProgress = 0f

    // Settling.
    private var settleTime = 0f
    private var calmTime = 0f
    private var announcedFalls = 0

    private val tmp = Vector3()
    private val tmp2 = Vector3()

    init {
        Gdx.input.inputProcessor = InputMultiplexer(hud.stage, TouchInput())
        beginTurn()
    }

    // ---- turn flow --------------------------------------------------------------------------------------------

    private fun beginTurn() {
        phase = Phase.BEGIN_TURN
        timer = 0.7f
        hud.showScoreboard(game.players, game.currentIndex)
        hud.clearActions()
        val player = game.current
        if (player.isAi) {
            hud.message("${player.name} is up")
        } else {
            hud.message("${player.name}: roll the die")
            hud.showRoll { rollDie() }
            phase = Phase.WAIT_ROLL
        }
    }

    private fun rollDie() {
        if (phase != Phase.WAIT_ROLL && phase != Phase.BEGIN_TURN) return
        val color = game.rollDie()
        scene.rollDie(color, ROLL_SECONDS)
        app.sfx.play(Sfx.Effect.ROLL)
        hud.clearActions()
        hud.message("${game.current.name} rolls...")
        phase = Phase.ROLLING
    }

    private fun afterRoll() {
        val rolled = checkNotNull(game.roll)
        val player = game.current
        val eligible = game.eligiblePieces()
        val free = eligible.any { it.color != rolled }
        val name = rolled.label.lowercase()
        if (player.isAi) {
            hud.message(if (free) "${player.name} rolled $name: none left, free choice" else "${player.name} rolled $name")
            startAiAiming(eligible)
            return
        }
        val distinct = eligible.distinctBy { it.type }
        hud.message(if (free) "No $name left: place any piece" else "Rolled $name: pick a piece")
        if (distinct.size == 1) {
            pick(distinct.first(), allowBack = false)
        } else {
            phase = Phase.CHOOSING
            hud.showPieceChoices(eligible) { pick(it, allowBack = true) }
        }
    }

    private fun hoverY(piece: HeldPiece): Float = world.stackTopY() + piece.halfHeight + HOVER_GAP

    private fun pick(piece: Piece, allowBack: Boolean) {
        val h = HeldPiece(piece)
        world.plateToWorld(tmp.set(0f, 0f, 0f), h.position)
        h.position.y = hoverY(h)
        held = h
        phase = Phase.PLACING
        hud.message("Drag over the plate, let go to drop")
        showPlacementControls(allowBack)
        app.sfx.play(Sfx.Effect.CLICK)
    }

    private fun showPlacementControls(allowBack: Boolean) {
        val h = held ?: return
        hud.showPlacementControls(
            h.lying,
            onTurn = { h.yaw += it },
            onToggleLay = { h.lying = !h.lying; showPlacementControls(allowBack) },
            onBack = if (allowBack) ({ cancelPick() }) else null,
        )
    }

    private fun cancelPick() {
        held = null
        phase = Phase.CHOOSING
        hud.message("Pick a piece")
        hud.showPieceChoices(game.eligiblePieces()) { pick(it, allowBack = true) }
    }

    private fun startAiAiming(eligible: List<Piece>) {
        val placement = ai.choose(world.piecesOnPlate(), eligible)
        val h = HeldPiece(placement.piece)
        world.plateToWorld(tmp.set(0f, 0f, 0f), h.position)
        h.position.y = hoverY(h) + 1.5f
        aiStart.set(h.position)
        aiPlacement = placement
        aiProgress = 0f
        held = h
        phase = Phase.AI_AIMING
        hud.clearActions()
    }

    private fun drop() {
        val h = held ?: return
        game.place(h.piece)
        world.addPiece(h.piece, h.position, h.yaw, h.lying)
        held = null
        aiPlacement = null
        phase = Phase.SETTLING
        settleTime = 0f
        calmTime = 0f
        announcedFalls = 0
        hud.clearActions()
        hud.message(null)
        hud.showScoreboard(game.players, game.currentIndex)
        app.sfx.play(Sfx.Effect.DROP)
    }

    private fun finishSettling() {
        val fallen = world.removeFallen()
        val player = game.current
        val result = game.endTurn(fallen)
        hud.showScoreboard(game.players, game.currentIndex)
        when {
            result.winner != null -> {
                hud.message(null)
                phase = Phase.GAME_OVER
                app.sfx.play(Sfx.Effect.WIN)
                hud.showGameOver(result.winner.name, onAgain = { app.startGame(setup) }, onMenu = { app.showMenu() })
            }
            fallen.isNotEmpty() -> {
                val n = fallen.size
                hud.message("${player.name} takes back $n piece${if (n == 1) "" else "s"}")
                phase = Phase.REPORT
                timer = 1.8f
            }
            else -> {
                hud.message("Steady!")
                phase = Phase.REPORT
                timer = 0.7f
            }
        }
    }

    // ---- per-frame update -------------------------------------------------------------------------------------

    private fun update(delta: Float) {
        when (phase) {
            Phase.BEGIN_TURN -> {
                timer -= delta
                if (timer <= 0f) rollDie()
            }
            Phase.ROLLING -> if (!scene.isDieRolling) afterRoll()
            Phase.PLACING -> held?.let {
                val target = hoverY(it)
                it.position.y += (target - it.position.y) * (1f - kotlin.math.exp(-10f * delta))
            }
            Phase.AI_AIMING -> aimAi(delta)
            Phase.SETTLING -> settle(delta)
            Phase.REPORT -> {
                timer -= delta
                if (timer <= 0f) beginTurn()
            }
            Phase.WAIT_ROLL, Phase.CHOOSING, Phase.GAME_OVER -> {}
        }
    }

    private fun aimAi(delta: Float) {
        val h = held ?: return
        val target = aiPlacement ?: return
        aiProgress += delta / AI_AIM_SECONDS
        val u = Interpolation.smooth.apply(aiProgress.coerceIn(0f, 1f))
        world.plateToWorld(tmp.set(target.x, 0f, target.z), tmp2)
        val y = hoverY(h)
        h.position.set(
            MathUtils.lerp(aiStart.x, tmp2.x, u),
            MathUtils.lerp(aiStart.y, y, u),
            MathUtils.lerp(aiStart.z, tmp2.z, u),
        )
        if (aiProgress >= 1.25f) drop()
    }

    private fun settle(delta: Float) {
        settleTime += delta
        calmTime = if (world.isSettled()) calmTime + delta else 0f
        val fallen = world.detectFallen()
        if (fallen.isNotEmpty()) {
            announcedFalls += fallen.size
            app.sfx.play(Sfx.Effect.FALL)
        }
        if ((settleTime >= MIN_SETTLE && calmTime >= CALM_NEEDED) || settleTime >= MAX_SETTLE) finishSettling()
    }

    // ---- input ------------------------------------------------------------------------------------------------

    private inner class TouchInput : InputAdapter() {
        private val pointers = HashMap<Int, Vector2>()
        private var twoFingerGesture = false
        private var dragged = false
        private var moved = 0f
        private var lastAngle = 0f
        private var lastDistance = 0f

        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            pointers[pointer] = Vector2(screenX.toFloat(), screenY.toFloat())
            if (pointers.size == 1) {
                twoFingerGesture = false
                dragged = false
                moved = 0f
            }
            if (pointers.size == 2) {
                twoFingerGesture = true
                val (a, b) = pointers.values.toList()
                lastAngle = angle(a, b)
                lastDistance = a.dst(b)
            } else if (phase == Phase.PLACING) {
                moveHeld(screenX, screenY)
            }
            return true
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            val previous = pointers[pointer] ?: return false
            val dx = screenX - previous.x
            val dy = screenY - previous.y
            previous.set(screenX.toFloat(), screenY.toFloat())
            moved += hypot(dx, dy)
            if (moved > DRAG_THRESHOLD_PX) dragged = true

            if (pointers.size >= 2) {
                val (a, b) = pointers.values.toList()
                val angle = angle(a, b)
                val distance = a.dst(b)
                if (phase == Phase.PLACING) {
                    held?.let { it.yaw -= MathUtils.radiansToDegrees * wrap(angle - lastAngle) }
                } else if (lastDistance > 1f && distance > 1f) {
                    scene.zoomBy(lastDistance / distance)
                }
                lastAngle = angle
                lastDistance = distance
            } else if (phase == Phase.PLACING) {
                moveHeld(screenX, screenY)
            } else {
                scene.orbitBy(-dx * 0.35f, dy * 0.25f)
            }
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            val wasSingle = pointers.size == 1 && !twoFingerGesture
            pointers.remove(pointer)
            if (wasSingle && dragged && phase == Phase.PLACING) drop()
            return true
        }

        override fun scrolled(amountX: Float, amountY: Float): Boolean {
            scene.zoomBy(1f + amountY * 0.06f)
            return true
        }

        override fun keyDown(keycode: Int): Boolean {
            if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                app.showMenu()
                return true
            }
            return false
        }

        private fun angle(a: Vector2, b: Vector2) = atan2(b.y - a.y, b.x - a.x)

        private fun wrap(radians: Float): Float {
            var r = radians
            while (r > Math.PI) r -= (2 * Math.PI).toFloat()
            while (r < -Math.PI) r += (2 * Math.PI).toFloat()
            return r
        }
    }

    /** Puts the held piece above the point under the finger, which is lifted so the hand does not hide it. */
    private fun moveHeld(screenX: Int, screenY: Int) {
        val h = held ?: return
        val lift = if (Gdx.app.type == Application.ApplicationType.Android) FINGER_LIFT_FRACTION * Gdx.graphics.height else 0f
        val hit = scene.pickOnPlane(screenX.toFloat(), screenY - lift, h.position.y, tmp) ?: return
        val center = world.plateToWorld(tmp2.set(0f, 0f, 0f), tmp2)
        val dx = hit.x - center.x
        val dz = hit.z - center.z
        val limit = Dimensions.PLATE_RADIUS - 0.4f
        val d = hypot(dx, dz)
        val k = if (d > limit) limit / d else 1f
        h.position.x = center.x + dx * k
        h.position.z = center.z + dz * k
    }

    // ---- Screen -----------------------------------------------------------------------------------------------

    override fun render(delta: Float) {
        val dt = delta.coerceAtMost(1f / 30f)
        update(dt)
        world.step(dt)
        scene.update(dt)

        Gdx.gl.glClearColor(0.62f, 0.76f, 0.88f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        scene.render(world, held)
        hud.act(dt)
        hud.draw()
    }

    override fun resize(width: Int, height: Int) {
        scene.resize(width, height)
        hud.resize(width, height)
    }

    override fun show() {}
    override fun hide() {}
    override fun pause() {}
    override fun resume() {}

    override fun dispose() {
        hud.dispose()
        scene.dispose()
        world.dispose()
    }

    private companion object {
        const val ROLL_SECONDS = 1.4f
        const val AI_AIM_SECONDS = 1.0f
        const val HOVER_GAP = 1.2f
        const val MIN_SETTLE = 1.0f
        const val CALM_NEEDED = 0.5f
        const val MAX_SETTLE = 8f
        const val FINGER_LIFT_FRACTION = 0.07f

        /** A tap that moves less than this (in pixels) does not release the piece. */
        const val DRAG_THRESHOLD_PX = 12f
    }
}
