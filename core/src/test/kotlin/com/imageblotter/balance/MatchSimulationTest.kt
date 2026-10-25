package com.imageblotter.balance

import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.bullet.Bullet
import com.badlogic.gdx.utils.GdxNativesLoader
import com.imageblotter.balance.ai.AiPlayer
import com.imageblotter.balance.model.Difficulty
import com.imageblotter.balance.model.GameSetup
import com.imageblotter.balance.model.GameState
import com.imageblotter.balance.model.PlayerSetup
import com.imageblotter.balance.physics.BalanceWorld
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Plays whole games with computer players against the real physics, headless. */
class MatchSimulationTest {
    @BeforeTest
    fun loadNatives() {
        GdxNativesLoader.load()
        Bullet.init()
    }

    private class Stats(var turns: Int = 0, var turnsWithFalls: Int = 0, var fallen: Int = 0, var maxOnPlate: Int = 0)

    private fun play(difficulty: Difficulty, players: Int, seed: Int, maxTurns: Int = 400): Pair<Stats, String?> {
        val rng = Random(seed)
        val setup = GameSetup(List(players) { PlayerSetup("Bot ${it + 1}", true) }, difficulty)
        val game = GameState(setup, rng)
        val ai = AiPlayer(difficulty, rng)
        val stats = Stats()
        val world = BalanceWorld()
        try {
            while (!game.isOver && stats.turns < maxTurns) {
                game.rollDie()
                val eligible = game.eligiblePieces()
                val placement = ai.choose(world.piecesOnPlate(), eligible)
                val target = world.plateToWorld(Vector3(placement.x, 0f, placement.z))
                target.y = world.stackTopY() + 1f + placement.piece.shape.height / 2f
                game.place(placement.piece)
                world.addPiece(placement.piece, target, 0f, false)

                var t = 0f
                var calm = 0f
                while (t < 8f && (t < 1f || calm < 0.5f)) {
                    world.step(1f / 60f); world.detectFallen(); t += 1f / 60f
                    calm = if (world.isSettled()) calm + 1f / 60f else 0f
                }
                val fallen = world.removeFallen()
                stats.turns++
                stats.fallen += fallen.size
                if (fallen.isNotEmpty()) stats.turnsWithFalls++
                stats.maxOnPlate = maxOf(stats.maxOnPlate, world.piecesOnPlate().size)
                game.endTurn(fallen)
            }
        } finally {
            world.dispose()
        }
        return stats to game.winner?.name
    }

    @Test
    fun botsFinishGamesAtEveryDifficulty() {
        val configs = listOf(
            Triple(Difficulty.EASY, 2, 1), Triple(Difficulty.NORMAL, 2, 2),
            Triple(Difficulty.HARD, 2, 3), Triple(Difficulty.HARD, 4, 4),
        )
        for ((difficulty, players, seed) in configs) {
            val (s, winner) = play(difficulty, players, seed)
            println("${difficulty.label} x$players: turns=${s.turns} withFalls=${s.turnsWithFalls} fallen=${s.fallen} maxOnPlate=${s.maxOnPlate} winner=$winner")
            assertNotNull(winner, "$difficulty x$players did not finish in ${s.turns} turns")
            assertTrue(s.maxOnPlate >= 12, "the plate held only ${s.maxOnPlate} pieces")
        }
    }
}
