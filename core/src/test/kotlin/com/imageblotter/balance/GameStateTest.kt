package com.imageblotter.balance

import com.imageblotter.balance.model.GameSetup
import com.imageblotter.balance.model.GameState
import com.imageblotter.balance.model.PieceColor
import com.imageblotter.balance.model.PlayerSetup
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameStateTest {
    private fun newGame(players: Int = 2, seed: Int = 1) =
        GameState(GameSetup(List(players) { PlayerSetup("P${it + 1}", false) }), Random(seed))

    @Test
    fun everyPlayerStartsWithTwelvePieces_oneTallOneWidePerColour() {
        val game = newGame(4)
        for (p in game.players) {
            assertEquals(GameState.PIECES_PER_PLAYER, p.piecesLeft)
            assertEquals(12, p.inventory.map { it.type }.toSet().size)
        }
        assertEquals(48, game.players.flatMap { it.inventory }.map { it.id }.toSet().size)
    }

    @Test
    fun dieShowsEveryColourAndNotJustOne() {
        val seen = mutableSetOf<PieceColor>()
        val game = GameState(GameSetup(listOf(PlayerSetup("A", false), PlayerSetup("B", false))))
        repeat(200) {
            seen += game.rollDie()
            game.endTurn(emptyList())
        }
        assertEquals(PieceColor.entries.toSet(), seen)
    }

    @Test
    fun eligiblePiecesMatchRolledColour() {
        val game = newGame()
        val color = game.rollDie()
        val eligible = game.eligiblePieces()
        assertEquals(2, eligible.size)
        assertTrue(eligible.all { it.color == color })
    }

    @Test
    fun anyPieceMayBePlacedWhenTheRolledColourIsGone() {
        val game = newGame()
        val color = game.rollDie()
        game.current.inventory.removeAll { it.color == color }
        assertEquals(10, game.eligiblePieces().size)
    }

    @Test
    fun placingAPieceOfAnotherColourIsRejected() {
        val game = newGame()
        val color = game.rollDie()
        val other = game.current.inventory.first { it.color != color }
        assertFailsWith<IllegalArgumentException> { game.place(other) }
    }

    @Test
    fun fallenPiecesGoToTheCurrentPlayerAndTheTurnPasses() {
        val game = newGame()
        val first = game.current
        val other = game.players[1]
        game.rollDie()
        val piece = game.eligiblePieces().first()
        game.place(piece)
        assertEquals(11, first.piecesLeft)

        // The placed piece and one of the other player's earlier pieces both fell.
        val stray = other.inventory.removeAt(0)
        val result = game.endTurn(listOf(piece, stray))

        assertEquals(12, first.piecesLeft - 1)
        assertTrue(stray in first.inventory)
        assertEquals(11, other.piecesLeft)
        assertEquals(2, result.returned.size)
        assertNull(result.winner)
        assertEquals(other, game.current)
        assertNull(game.roll)
    }

    @Test
    fun firstPlayerWithNoPiecesLeftWins() {
        val game = newGame()
        val player = game.current
        player.inventory.subList(1, player.inventory.size).clear()
        val last = player.inventory.single()

        // Roll until the last piece is placeable; with only one piece left any roll allows it.
        game.rollDie()
        game.place(last)
        val result = game.endTurn(emptyList())

        assertEquals(player, result.winner)
        assertEquals(player, game.winner)
        assertTrue(game.isOver)
        assertFailsWith<IllegalStateException> { game.rollDie() }
    }

    @Test
    fun lastPieceFallingMeansNoWin() {
        val game = newGame()
        val player = game.current
        player.inventory.subList(1, player.inventory.size).clear()
        val last = player.inventory.single()
        game.rollDie()
        game.place(last)
        val result = game.endTurn(listOf(last))
        assertNull(result.winner)
        assertNotNull(player.inventory.singleOrNull())
        assertEquals(game.players[1], game.current)
    }

    @Test
    fun cannotRollTwice() {
        val game = newGame()
        game.rollDie()
        assertFailsWith<IllegalStateException> { game.rollDie() }
    }

    @Test
    fun setupNeedsTwoToFourPlayers() {
        assertFailsWith<IllegalArgumentException> { GameSetup(listOf(PlayerSetup("solo", false))) }
        assertFailsWith<IllegalArgumentException> { GameSetup(List(5) { PlayerSetup("p$it", false) }) }
    }
}
