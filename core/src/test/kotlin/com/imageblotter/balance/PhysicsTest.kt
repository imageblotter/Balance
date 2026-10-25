package com.imageblotter.balance

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.bullet.Bullet
import com.badlogic.gdx.utils.GdxNativesLoader
import com.imageblotter.balance.model.Piece
import com.imageblotter.balance.model.PieceColor
import com.imageblotter.balance.model.PieceShape
import com.imageblotter.balance.model.PieceType
import com.imageblotter.balance.physics.BalanceWorld
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhysicsTest {
    private var nextId = 0

    @BeforeTest
    fun loadNatives() {
        GdxNativesLoader.load()
        Bullet.init()
    }

    private fun piece(shape: PieceShape = PieceShape.WIDE, color: PieceColor = PieceColor.RED) =
        Piece(nextId++, PieceType(color, shape))

    private fun withWorld(block: (BalanceWorld) -> Unit) {
        val world = BalanceWorld()
        try {
            block(world)
        } finally {
            world.dispose()
        }
    }

    private fun BalanceWorld.run(seconds: Float) {
        repeat((seconds * 60).toInt()) { step(1f / 60f); detectFallen() }
    }

    /** Drops [p] from just above the pile at plate-frame position ([x], [z]). */
    private fun BalanceWorld.drop(p: Piece, x: Float, z: Float, lying: Boolean = false) {
        val at = plateToWorld(Vector3(x, 0f, z))
        at.y = stackTopY() + 1f + p.shape.height
        addPiece(p, at, 0f, lying)
    }

    @Test
    fun emptyPlateHangsLevelAndSettles() = withWorld { w ->
        w.run(3f)
        assertTrue(w.tiltDegrees() < 0.5f, "tilt ${w.tiltDegrees()}")
        assertTrue(w.isSettled())
    }

    @Test
    fun centredPieceStaysAndPlateStaysLevel() = withWorld { w ->
        val p = piece()
        w.drop(p, 0f, 0f)
        w.run(4f)
        assertTrue(w.contains(p) && !w.isFallen(p))
        assertTrue(w.isSettled())
        assertTrue(w.tiltDegrees() < 1f, "tilt ${w.tiltDegrees()}")
    }

    @Test
    fun offCentreWeightTiltsThePlateAndCounterweightLevelsIt() = withWorld { w ->
        val a = piece()
        w.drop(a, 4f, 0f)
        w.run(4f)
        val tilted = w.tiltDegrees()
        assertTrue(tilted > 2f, "expected a visible tilt, got $tilted")
        assertTrue(!w.isFallen(a))

        w.drop(piece(), -4f, 0f)
        w.run(4f)
        assertTrue(w.tiltDegrees() < tilted / 2f, "tilt ${w.tiltDegrees()} should drop below half of $tilted")
        assertTrue(w.isSettled())
    }

    @Test
    fun aPieceLaidOnTheRimRollsOffAndIsReportedOnce() = withWorld { w ->
        val p = piece(PieceShape.TALL)
        w.drop(p, 6f, 0f, lying = true)
        var reports = 0
        repeat(5 * 60) { w.step(1f / 60f); reports += w.detectFallen().size }
        assertEquals(1, reports)
        assertTrue(w.isFallen(p))
        assertTrue(w.piecesOnPlate().isEmpty())

        assertEquals(listOf(p), w.removeFallen())
        assertTrue(!w.contains(p))
    }

    @Test
    fun piecesOnPlateAreReportedInThePlateFrame() = withWorld { w ->
        val p = piece()
        w.drop(p, 2f, -1f)
        w.run(3f)
        val seen = w.piecesOnPlate().single()
        assertEquals(p, seen.piece)
        assertTrue(kotlin.math.abs(seen.x - 2f) < 0.6f && kotlin.math.abs(seen.z + 1f) < 0.6f, "at ${seen.x}, ${seen.z}")
        assertTrue(seen.y in 0.2f..1.5f, "height ${seen.y}")
    }
}
