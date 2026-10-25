package com.imageblotter.balance.model

/** The six die colours; every player owns one tall and one wide piece of each. */
enum class PieceColor(val label: String, val r: Float, val g: Float, val b: Float) {
    RED("Red", 0.86f, 0.12f, 0.16f),
    YELLOW("Yellow", 0.98f, 0.80f, 0.05f),
    BLUE("Blue", 0.05f, 0.62f, 0.82f),
    ORANGE("Orange", 0.97f, 0.50f, 0.07f),
    GREY("Grey", 0.42f, 0.43f, 0.46f),
    GREEN("Green", 0.13f, 0.66f, 0.28f),
}

/** Cylinder dimensions in world units (the plate has radius [com.imageblotter.balance.physics.Dimensions.PLATE_RADIUS]). */
enum class PieceShape(val label: String, val radius: Float, val height: Float) {
    TALL("Tall", 0.6f, 2.2f),
    WIDE("Wide", 1.1f, 0.8f);

    val mass: Float get() = Math.PI.toFloat() * radius * radius * height * DENSITY

    private companion object {
        const val DENSITY = 0.25f
    }
}

data class PieceType(val color: PieceColor, val shape: PieceShape) {
    val label: String get() = "${color.label} ${shape.label.lowercase()}"
}

class Piece(val id: Int, val type: PieceType) {
    val color: PieceColor get() = type.color
    val shape: PieceShape get() = type.shape
    override fun toString() = "Piece#$id(${type.label})"
}

class Player(val index: Int, val name: String, val isAi: Boolean) {
    val inventory: MutableList<Piece> = mutableListOf()
    val piecesLeft: Int get() = inventory.size
}

enum class Difficulty(val label: String) { EASY("Easy"), NORMAL("Normal"), HARD("Hard") }

data class PlayerSetup(val name: String, val isAi: Boolean)

data class GameSetup(val players: List<PlayerSetup>, val difficulty: Difficulty = Difficulty.NORMAL) {
    init {
        require(players.size in GameState.MIN_PLAYERS..GameState.MAX_PLAYERS) { "2-4 players" }
    }
}
