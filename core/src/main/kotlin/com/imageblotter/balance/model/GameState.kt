package com.imageblotter.balance.model

import kotlin.random.Random

/** What happened when a turn was resolved. */
data class TurnResult(val player: Player, val returned: List<Piece>, val winner: Player?)

/**
 * The rules of the game, free of physics and rendering.
 *
 * A turn is: [rollDie] -> choose one of [eligiblePieces] -> [place] -> [endTurn] with the pieces that
 * fell off the plate. Whoever just placed takes every fallen piece into their inventory. The first
 * player left without pieces wins.
 */
class GameState(setup: GameSetup, private val random: Random = Random.Default) {
    val players: List<Player>
    var currentIndex = 0
        private set
    var roll: PieceColor? = null
        private set
    var winner: Player? = null
        private set

    val current: Player get() = players[currentIndex]
    val isOver: Boolean get() = winner != null

    init {
        var nextId = 0
        players = setup.players.mapIndexed { i, p ->
            Player(i, p.name, p.isAi).also { player ->
                for (color in PieceColor.entries) for (shape in PieceShape.entries) {
                    player.inventory += Piece(nextId++, PieceType(color, shape))
                }
            }
        }
    }

    fun rollDie(): PieceColor {
        check(!isOver && roll == null) { "Already rolled" }
        return PieceColor.entries[random.nextInt(PieceColor.entries.size)].also { roll = it }
    }

    /** Pieces of the rolled colour, or any piece when the current player has none of that colour. */
    fun eligiblePieces(): List<Piece> {
        val rolled = checkNotNull(roll) { "Roll the die first" }
        val ofColor = current.inventory.filter { it.color == rolled }
        return ofColor.ifEmpty { current.inventory.toList() }
    }

    /** Takes [piece] out of the current player's inventory; it is now on its way to the plate. */
    fun place(piece: Piece) {
        require(piece in eligiblePieces()) { "$piece may not be placed now" }
        current.inventory.remove(piece)
    }

    /** Ends the turn: [fallen] pieces go to the current player, then the win check and next player. */
    fun endTurn(fallen: List<Piece>): TurnResult {
        checkNotNull(roll) { "No turn in progress" }
        val player = current
        player.inventory += fallen
        roll = null
        if (player.inventory.isEmpty()) {
            winner = player
        } else {
            currentIndex = (currentIndex + 1) % players.size
        }
        return TurnResult(player, fallen, winner)
    }

    companion object {
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 4
        const val PIECES_PER_PLAYER = 12
    }
}
