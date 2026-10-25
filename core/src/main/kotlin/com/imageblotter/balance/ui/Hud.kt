package com.imageblotter.balance.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.imageblotter.balance.model.Piece
import com.imageblotter.balance.model.PieceType
import com.imageblotter.balance.model.Player

/** The 2D overlay of the game screen: scoreboard, messages and the buttons of the current step. */
class Hud(
    private val ui: UiKit,
    private val onMenu: () -> Unit,
    private val onCameraTurn: (Float) -> Unit,
    private val onSoundToggle: () -> Boolean,
    soundOn: Boolean,
) : Disposable {
    val stage = Stage(ScreenViewport())

    private val scoreboard = Table()
    private val messageBox = Table()
    private val actionBar = Table()
    private val overlay = Table()
    private val soundButton: TextButton

    init {
        soundButton = ui.button(soundText(soundOn), UiKit.NEUTRAL, scale = 0.28f) {
            soundButton.setText(soundText(onSoundToggle()))
        }

        val cameraRow = Table().apply {
            add(ui.button("<", UiKit.NEUTRAL, scale = 0.36f) { onCameraTurn(-20f) }).size(52f, 44f).padRight(6f)
            add(ui.button(">", UiKit.NEUTRAL, scale = 0.36f) { onCameraTurn(20f) }).size(52f, 44f)
        }
        val topRight = Table().apply {
            add(cameraRow).right().padBottom(6f).row()
            add(soundButton).right().size(110f, 40f).padBottom(6f).row()
            add(ui.button("Menu", UiKit.NEUTRAL, scale = 0.28f) { onMenu() }).right().size(110f, 40f)
        }

        messageBox.background = ui.panel(UiKit.PANEL)
        messageBox.touchable = Touchable.disabled

        val root = Table().apply {
            setFillParent(true)
            touchable = Touchable.childrenOnly
            top()
            add(scoreboard).left().top().pad(10f).expandX()
            add(topRight).right().top().pad(10f)
            row()
            add(messageBox).colspan(2).padTop(2f).padLeft(10f).padRight(10f)
            row()
            add().expand()
            row()
            add(actionBar).colspan(2).padBottom(14f).bottom()
        }
        stage.addActor(root)

        overlay.setFillParent(true)
        overlay.touchable = Touchable.childrenOnly
        stage.addActor(overlay)
    }

    private companion object {
        const val MESSAGE_MAX_WIDTH = 400f
    }

    private fun soundText(on: Boolean) = if (on) "Sound: on" else "Sound: off"

    fun resize(width: Int, height: Int) {
        (stage.viewport as ScreenViewport).unitsPerPixel = UiKit.unitsPerPixel(width, height)
        stage.viewport.update(width, height, true)
    }

    fun showScoreboard(players: List<Player>, current: Int) {
        scoreboard.clear()
        scoreboard.background = ui.panel(UiKit.PANEL)
        scoreboard.pad(8f, 12f, 8f, 12f)
        for (p in players) {
            val isCurrent = p.index == current
            val color = if (isCurrent) UiKit.GOLD else UiKit.SEATS[p.index % UiKit.SEATS.size]
            scoreboard.add(ui.label((if (isCurrent) "• " else "  ") + p.name, 0.3f, color)).left().padRight(14f)
            scoreboard.add(ui.label("${p.piecesLeft}", 0.3f, color)).right()
            scoreboard.row()
        }
    }

    fun message(text: String?) {
        messageBox.clear()
        messageBox.isVisible = !text.isNullOrEmpty()
        if (!text.isNullOrEmpty()) {
            messageBox.pad(8f, 18f, 8f, 18f)
            val label = ui.label(text, 0.32f).apply { setAlignment(Align.center) }
            val wide = label.prefWidth > MESSAGE_MAX_WIDTH
            label.setWrap(wide)
            messageBox.add(label).apply { if (wide) width(MESSAGE_MAX_WIDTH) }
        }
    }

    fun clearActions() {
        actionBar.clear()
    }

    fun showRoll(onRoll: () -> Unit) {
        actionBar.clear()
        actionBar.add(ui.button("Roll the die", UiKit.PRIMARY, scale = 0.5f) { onRoll() }).size(260f, 72f)
    }

    /** One button per kind of piece the player may place; [pieces] may hold several of the same kind. */
    fun showPieceChoices(pieces: List<Piece>, onPick: (Piece) -> Unit) {
        actionBar.clear()
        val groups = pieces.groupBy { it.type }.toList().sortedBy { (type, _) -> type.color.ordinal * 2 + type.shape.ordinal }
        var inRow = 0
        for ((type, group) in groups) {
            val fill = Color(type.color.r, type.color.g, type.color.b, 1f)
            val text = type.shape.label + if (group.size > 1) " ×${group.size}" else ""
            actionBar.add(ui.button(text, fill, UiKit.textOn(fill), scale = 0.3f) { onPick(group.first()) })
                .size(104f, 50f).pad(3f)
            if (++inRow % 4 == 0) actionBar.row()
        }
    }

    fun showPlacementControls(lying: Boolean, onTurn: (Float) -> Unit, onToggleLay: () -> Unit, onBack: (() -> Unit)?) {
        actionBar.clear()
        val small = 0.3f
        actionBar.add(ui.button("Turn <", UiKit.NEUTRAL, scale = small) { onTurn(15f) }).size(96f, 48f).pad(3f)
        actionBar.add(ui.button("Turn >", UiKit.NEUTRAL, scale = small) { onTurn(-15f) }).size(96f, 48f).pad(3f)
        actionBar.add(ui.button(if (lying) "Stand up" else "Lay down", UiKit.NEUTRAL, scale = small) { onToggleLay() }).size(112f, 48f).pad(3f)
        if (onBack != null) actionBar.add(ui.button("Back", UiKit.DANGER, scale = small) { onBack() }).size(80f, 48f).pad(3f)
    }

    fun showGameOver(winner: String, onAgain: () -> Unit, onMenu: () -> Unit) {
        overlay.clear()
        val card = Table().apply {
            background = ui.panel(Color(0.06f, 0.08f, 0.12f, 0.88f))
            pad(26f)
            add(ui.label("$winner wins!", 0.7f, UiKit.GOLD)).padBottom(8f).row()
            add(ui.label("All pieces placed.", 0.34f)).padBottom(20f).row()
            add(ui.button("Play again", UiKit.PRIMARY, scale = 0.42f) { onAgain() }).size(230f, 58f).padBottom(10f).row()
            add(ui.button("Menu", UiKit.NEUTRAL, scale = 0.36f) { onMenu() }).size(230f, 52f)
        }
        overlay.add(card)
    }

    fun act(delta: Float) = stage.act(delta)

    fun draw() = stage.draw()

    override fun dispose() = stage.dispose()
}
