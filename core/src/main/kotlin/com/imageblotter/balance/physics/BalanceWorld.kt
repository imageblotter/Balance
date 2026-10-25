package com.imageblotter.balance.physics

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.bullet.Bullet
import com.badlogic.gdx.physics.bullet.collision.btBoxShape
import com.badlogic.gdx.physics.bullet.collision.btCollisionConfiguration
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher
import com.badlogic.gdx.physics.bullet.collision.btCylinderShape
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration
import com.badlogic.gdx.physics.bullet.dynamics.btConstraintSolver
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld
import com.badlogic.gdx.physics.bullet.dynamics.btPoint2PointConstraint
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver
import com.badlogic.gdx.utils.Disposable
import com.imageblotter.balance.model.Piece
import com.imageblotter.balance.model.PieceShape
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** A piece resting (or moving) on the plate, expressed in the plate's own frame. */
class PieceOnPlate(
    val piece: Piece,
    /** Position relative to the plate centre, in the plate's frame. */
    val x: Float,
    val y: Float,
    val z: Float,
    /** Horizontal footprint radius, accounting for pieces that lie on their side. */
    val footprint: Float,
    /** Half of the vertical extent. */
    val halfHeight: Float,
) {
    val top: Float get() = y + halfHeight
    val mass: Float get() = piece.shape.mass
}

/**
 * The plate hanging from a single point, the table below it, and the pieces on it.
 *
 * The plate is a pendulum: the pivot sits [Dimensions.PIVOT_HEIGHT] above its centre, so it hangs level
 * when the centre of mass is straight below the pivot and tilts as the load moves off-centre.
 * [Bullet.init] must have run before the first instance is created.
 */
class BalanceWorld : Disposable {
    private val config: btCollisionConfiguration = btDefaultCollisionConfiguration()
    private val dispatcher = btCollisionDispatcher(config)
    private val broadphase = btDbvtBroadphase()
    private val solver: btConstraintSolver = btSequentialImpulseConstraintSolver()
    private val world = btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config)

    private val plateShape = btCylinderShape(
        Vector3(Dimensions.PLATE_RADIUS, Dimensions.PLATE_THICKNESS / 2f, Dimensions.PLATE_RADIUS),
    )
    private val tableShape = btBoxShape(Vector3(Dimensions.TABLE_HALF_SIZE, 1f, Dimensions.TABLE_HALF_SIZE))
    private val pieceShapes = PieceShape.entries.associateWith {
        btCylinderShape(Vector3(it.radius, it.height / 2f, it.radius))
    }

    private val plate: btRigidBody
    private val table: btRigidBody
    private val hinge: btPoint2PointConstraint

    private class PieceBody(val piece: Piece, val body: btRigidBody, var fallen: Boolean = false)

    private val bodies = LinkedHashMap<Piece, PieceBody>()

    private val tmpMatrix = Matrix4()
    private val tmpInverse = Matrix4()
    private val tmpVec = Vector3()
    private val tmpQuat = Quaternion()

    init {
        world.gravity = Vector3(0f, -Dimensions.GRAVITY, 0f)
        world.solverInfo.numIterations = 30

        table = newBody(0f, tableShape, Matrix4().setToTranslation(0f, -1f, 0f), friction = 0.6f)
        world.addRigidBody(table)

        plate = newBody(
            Dimensions.PLATE_MASS,
            plateShape,
            Matrix4().setToTranslation(0f, Dimensions.PLATE_CENTER_Y, 0f),
            friction = 0.8f,
        )
        plate.setDamping(LINEAR_DAMPING, ANGULAR_DAMPING)
        world.addRigidBody(plate)

        hinge = btPoint2PointConstraint(plate, Vector3(0f, Dimensions.PIVOT_HEIGHT, 0f))
        world.addConstraint(hinge, true)
    }

    private fun newBody(mass: Float, shape: com.badlogic.gdx.physics.bullet.collision.btCollisionShape, transform: Matrix4, friction: Float): btRigidBody {
        val inertia = Vector3()
        if (mass > 0f) shape.calculateLocalInertia(mass, inertia)
        return btRigidBody(mass, null, shape, inertia).apply {
            worldTransform = transform
            setFriction(friction)
            setRestitution(0.05f)
            activationState = DISABLE_DEACTIVATION
        }
    }

    /** Drops [piece] into the world at [position] (world coordinates) with the given heading. */
    fun addPiece(piece: Piece, position: Vector3, yaw: Float, lying: Boolean) {
        val transform = Matrix4()
        transform.set(pieceRotation(yaw, lying, tmpQuat)).setTranslation(position)
        val body = newBody(piece.shape.mass, pieceShapes.getValue(piece.shape), transform, friction = 0.7f)
        body.setDamping(0.02f, 0.05f)
        world.addRigidBody(body)
        bodies[piece] = PieceBody(piece, body)
    }

    fun step(delta: Float) {
        world.stepSimulation(delta.coerceAtMost(1f / 20f), 12, 1f / 120f)
    }

    fun contains(piece: Piece) = piece in bodies

    fun plateTransform(out: Matrix4): Matrix4 = out.also { plate.getWorldTransform(it) }

    fun pieceTransform(piece: Piece, out: Matrix4): Matrix4 = out.also { bodies.getValue(piece).body.getWorldTransform(it) }

    val pieces: Collection<Piece> get() = bodies.keys

    fun isFallen(piece: Piece) = bodies[piece]?.fallen == true

    /**
     * Marks pieces that dropped below the plate as fallen and returns the ones newly detected.
     * Fallen bodies stay in the world (they bounce on the table) until [removeFallen].
     */
    fun detectFallen(): List<Piece> {
        plate.getWorldTransform(tmpMatrix)
        tmpInverse.set(tmpMatrix).inv()
        val newlyFallen = mutableListOf<Piece>()
        for (pb in bodies.values) {
            if (pb.fallen) continue
            pb.body.getWorldTransform(tmpMatrix)
            tmpMatrix.getTranslation(tmpVec).mul(tmpInverse)
            if (tmpVec.y < FALL_LEVEL) {
                pb.fallen = true
                newlyFallen += pb.piece
            }
        }
        return newlyFallen
    }

    /** Removes every fallen piece from the world and returns them. */
    fun removeFallen(): List<Piece> {
        val gone = bodies.values.filter { it.fallen }
        gone.forEach { remove(it) }
        return gone.map { it.piece }
    }

    private fun remove(pb: PieceBody) {
        world.removeRigidBody(pb.body)
        pb.body.dispose()
        bodies.remove(pb.piece)
    }

    /** True once the plate and everything still on it have stopped moving. */
    fun isSettled(): Boolean {
        if (plate.linearVelocity.len() > SETTLED_LINEAR || plate.angularVelocity.len() > SETTLED_ANGULAR) return false
        return bodies.values.none {
            !it.fallen && (it.body.linearVelocity.len() > SETTLED_LINEAR || it.body.angularVelocity.len() > SETTLED_ANGULAR)
        }
    }

    /** Snapshot of the pieces still on the plate, in the plate's frame. */
    fun piecesOnPlate(): List<PieceOnPlate> {
        plate.getWorldTransform(tmpMatrix)
        tmpInverse.set(tmpMatrix).inv()
        return bodies.values.filter { !it.fallen }.map { pb ->
            pb.body.getWorldTransform(tmpMatrix)
            val axisY = tmpVec.set(0f, 1f, 0f).rot(tmpMatrix).y
            val local = tmpMatrix.getTranslation(Vector3()).mul(tmpInverse)
            val shape = pb.piece.shape
            val upright = abs(axisY)
            val side = sqrt(max(0f, 1f - upright * upright))
            PieceOnPlate(
                pb.piece, local.x, local.y, local.z,
                footprint = side * shape.height / 2f + upright * shape.radius,
                halfHeight = upright * shape.height / 2f + side * shape.radius,
            )
        }
    }

    /** World height of the highest point of anything on the plate (plate top when empty). */
    fun stackTopY(): Float {
        plate.getWorldTransform(tmpMatrix)
        var top = tmpMatrix.getTranslation(tmpVec).y + Dimensions.PLATE_THICKNESS / 2f + Dimensions.PLATE_RADIUS * 0.3f
        for (pb in bodies.values) {
            if (pb.fallen) continue
            pb.body.getWorldTransform(tmpMatrix)
            val s = pb.piece.shape
            top = max(top, tmpMatrix.getTranslation(tmpVec).y + sqrt(s.radius * s.radius + s.height * s.height / 4f))
        }
        return top
    }

    /** Maps a point in the plate's frame to world coordinates. */
    fun plateToWorld(local: Vector3, out: Vector3 = Vector3()): Vector3 {
        plate.getWorldTransform(tmpMatrix)
        return out.set(local).mul(tmpMatrix)
    }

    /** Rotation of the plate away from level, in degrees. */
    fun tiltDegrees(): Float {
        plate.getWorldTransform(tmpMatrix)
        val up = Vector3(0f, 1f, 0f).rot(tmpMatrix)
        return Math.toDegrees(Math.acos(up.y.coerceIn(-1f, 1f).toDouble())).toFloat()
    }

    override fun dispose() {
        bodies.values.toList().forEach { remove(it) }
        world.removeConstraint(hinge)
        world.removeRigidBody(plate)
        world.removeRigidBody(table)
        hinge.dispose()
        plate.dispose()
        table.dispose()
        pieceShapes.values.forEach { it.dispose() }
        plateShape.dispose()
        tableShape.dispose()
        world.dispose()
        solver.dispose()
        broadphase.dispose()
        dispatcher.dispose()
        config.dispose()
    }

    companion object {
        private const val DISABLE_DEACTIVATION = 4
        private const val LINEAR_DAMPING = 0.9f
        private const val ANGULAR_DAMPING = 0.9f
        private const val SETTLED_LINEAR = 0.15f
        private const val SETTLED_ANGULAR = 0.2f

        /** Plate-local height below which a piece counts as fallen. */
        private const val FALL_LEVEL = -1.5f

        /** Rotation of a cylinder standing up, or lying on its side (axis along z), turned by [yaw] degrees. */
        fun pieceRotation(yaw: Float, lying: Boolean, out: Quaternion = Quaternion()): Quaternion {
            out.setFromAxis(Vector3.Y, yaw)
            if (lying) out.mul(Quaternion(Vector3.X, 90f))
            return out
        }
    }
}
