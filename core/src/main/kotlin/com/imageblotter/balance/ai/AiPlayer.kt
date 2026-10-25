package com.imageblotter.balance.ai

import com.imageblotter.balance.model.Difficulty
import com.imageblotter.balance.model.Piece
import com.imageblotter.balance.physics.Dimensions
import com.imageblotter.balance.physics.PieceOnPlate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Where the computer wants to drop a piece, in the plate's frame. */
data class Placement(val piece: Piece, val x: Float, val z: Float)

/**
 * Picks a piece and a spot that keeps the centre of mass over the middle of the plate and the pile low.
 * Lower difficulties add noise to both the choice and the final aim.
 */
class AiPlayer(difficulty: Difficulty, private val random: Random = Random.Default) {
    private val scoreNoise: Float
    private val aimError: Float
    private val bestPieceChance: Float

    init {
        when (difficulty) {
            Difficulty.EASY -> { scoreNoise = 1.2f; aimError = 0.9f; bestPieceChance = 0f }
            Difficulty.NORMAL -> { scoreNoise = 0.4f; aimError = 0.35f; bestPieceChance = 0.5f }
            Difficulty.HARD -> { scoreNoise = 0.05f; aimError = 0.08f; bestPieceChance = 1f }
        }
    }

    fun choose(onPlate: List<PieceOnPlate>, eligible: List<Piece>): Placement {
        require(eligible.isNotEmpty()) { "Nothing to place" }
        val distinct = eligible.distinctBy { it.type }
        val candidates = if (random.nextFloat() < bestPieceChance) distinct else listOf(distinct.random(random))

        var best: Placement? = null
        var bestScore = Float.MAX_VALUE
        for (piece in candidates) {
            for ((x, z) in spots(piece.shape.radius)) {
                val score = score(onPlate, piece, x, z) + gaussian() * scoreNoise
                if (score < bestScore) {
                    bestScore = score
                    best = Placement(piece, x, z)
                }
            }
        }
        val pick = checkNotNull(best)
        val limit = Dimensions.PLATE_RADIUS - pick.piece.shape.radius
        var x = pick.x + gaussian() * aimError
        var z = pick.z + gaussian() * aimError
        val d = hypot(x, z)
        if (d > limit) { x *= limit / d; z *= limit / d }
        return pick.copy(x = x, z = z)
    }

    private fun spots(radius: Float): List<Pair<Float, Float>> {
        val maxRing = Dimensions.PLATE_RADIUS - radius - 0.2f
        val spots = mutableListOf(0f to 0f)
        var ring = 1f
        while (ring <= maxRing + 0.001f) {
            val n = (2 * PI * ring / 1.1f).toInt().coerceAtLeast(6)
            for (i in 0 until n) {
                val a = (2 * PI * i / n).toFloat()
                spots += ring * cos(a) to ring * sin(a)
            }
            ring += 1f
        }
        return spots
    }

    private fun score(onPlate: List<PieceOnPlate>, piece: Piece, x: Float, z: Float): Float {
        val shape = piece.shape
        var floor = PLATE_TOP
        var unsupported = 0f
        for (o in onPlate) {
            val d = hypot(x - o.x, z - o.z)
            if (d < o.footprint + shape.radius * 0.8f) {
                if (o.top > floor) floor = o.top
                if (d > o.footprint - shape.radius * 0.3f) unsupported = 1f
            }
        }
        var mx = piece.shape.mass * x
        var mz = piece.shape.mass * z
        var total = Dimensions.PLATE_MASS + shape.mass
        for (o in onPlate) { mx += o.mass * o.x; mz += o.mass * o.z; total += o.mass }
        val offset = hypot(mx / total, mz / total)

        val stacked = if (floor > PLATE_TOP + 0.01f) 1f else 0f
        val topHeight = floor + shape.height - PLATE_TOP
        return offset * 3f + stacked * 0.6f + unsupported * 3f + topHeight * 0.25f + sqrt(x * x + z * z) * 0.02f
    }

    private fun gaussian(): Float {
        val u = random.nextFloat().coerceAtLeast(1e-6f)
        val v = random.nextFloat()
        return (sqrt(-2f * ln(u)) * cos(2f * PI.toFloat() * v))
    }

    private companion object {
        const val PLATE_TOP = Dimensions.PLATE_THICKNESS / 2f
    }
}
