package com.imageblotter.balance.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.PixmapTextureData
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.utils.Disposable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Fonts and the rounded-rectangle look shared by every screen. */
class UiKit : Disposable {
    private val fontTexture = Texture(Gdx.files.internal("ui.png"), true).apply {
        setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
    }
    val font = BitmapFont(Gdx.files.internal("ui.fnt"), TextureRegion(fontTexture), false)

    private val roundedPixmap: Pixmap
    private val roundedTexture: Texture
    private val rounded: NinePatchDrawable

    init {
        val size = 96
        val radius = 30f
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        roundedPixmap = pixmap
        val half = size / 2f
        for (y in 0 until size) for (x in 0 until size) {
            // Signed distance to a rounded square, anti-aliased over one pixel.
            val dx = max(kotlin.math.abs(x + 0.5f - half) - (half - radius), 0f)
            val dy = max(kotlin.math.abs(y + 0.5f - half) - (half - radius), 0f)
            val d = sqrt(dx * dx + dy * dy) - radius
            val alpha = (0.5f - d).coerceIn(0f, 1f)
            pixmap.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, alpha))
        }
        // Managed, so the texture is rebuilt from the pixmap if the GL context is lost; the pixmap lives as long as the kit.
        roundedTexture = Texture(PixmapTextureData(pixmap, null, true, false, true)).apply {
            setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
        }
        val patch = NinePatch(roundedTexture, 32, 32, 32, 32).apply { scale(0.5f, 0.5f) }
        rounded = NinePatchDrawable(patch)
    }

    fun panel(color: Color): Drawable = rounded.tint(color)

    fun label(text: String, scale: Float = 0.34f, color: Color = Color.WHITE): Label =
        Label(text, Label.LabelStyle(font, color)).apply {
            setFontScale(scale)
            touchable = Touchable.disabled
        }

    fun button(text: String, fill: Color, textColor: Color = Color.WHITE, scale: Float = 0.36f, onClick: () -> Unit): TextButton {
        val down = Color(fill).mul(0.7f).also { it.a = fill.a }
        val style = TextButton.TextButtonStyle(rounded.tint(fill), rounded.tint(down), null, font).apply { fontColor = textColor }
        return TextButton(text, style).apply {
            label.setFontScale(scale)
            pad(10f, 22f, 12f, 22f)
            addListener(object : ClickListener() {
                override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: Float, y: Float) = onClick()
            })
        }
    }

    override fun dispose() {
        fontTexture.dispose()
        font.dispose()
        roundedTexture.dispose()
        roundedPixmap.dispose()
    }

    companion object {
        val INK = Color(0.1f, 0.12f, 0.16f, 1f)
        val PANEL = Color(0.08f, 0.1f, 0.14f, 0.72f)
        val PRIMARY = Color(0.2f, 0.62f, 0.4f, 1f)
        val NEUTRAL = Color(0.28f, 0.33f, 0.42f, 0.95f)
        val DANGER = Color(0.78f, 0.28f, 0.28f, 1f)
        val GOLD = Color(1f, 0.84f, 0.3f, 1f)

        /** One colour per seat, used for names in the scoreboard. */
        val SEATS = listOf(
            Color(0.42f, 0.8f, 1f, 1f), Color(1f, 0.55f, 0.75f, 1f),
            Color(0.72f, 0.6f, 1f, 1f), Color(0.6f, 0.95f, 0.5f, 1f),
        )

        /** Black or white, whichever reads better on [fill]. */
        fun textOn(fill: Color): Color = if (0.299f * fill.r + 0.587f * fill.g + 0.114f * fill.b > 0.6f) INK else Color.WHITE

        /** UI units per screen pixel so that the shorter screen side is about 480 units. */
        fun unitsPerPixel(width: Int, height: Int): Float = 480f / max(1, min(width, height))
    }
}

fun Actor.hitOnlyChildren() {
    touchable = Touchable.childrenOnly
}
