package com.imageblotter.balance.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.PixmapTextureData
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.imageblotter.balance.model.Piece
import com.imageblotter.balance.model.PieceColor
import com.imageblotter.balance.model.PieceShape
import com.imageblotter.balance.model.PieceType
import com.imageblotter.balance.physics.BalanceWorld
import com.imageblotter.balance.physics.Dimensions
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** A piece being carried above the plate, before it is released. */
class HeldPiece(val piece: Piece) {
    val position = Vector3()
    var yaw = 0f
    var lying = false

    /** Half of the vertical extent: how far the piece reaches above and below [position]. */
    val halfHeight: Float get() = if (lying) piece.shape.radius else piece.shape.height / 2f

    /** Radius of the circle the piece covers on the plate. */
    val footprint: Float get() = if (lying) maxOf(piece.shape.radius, piece.shape.height / 2f) else piece.shape.radius
}

/** Everything that is drawn in 3D: table, stand, hanging plate, pieces, the die, and the camera. */
class Scene3D : Disposable {
    val camera = PerspectiveCamera(45f, 1f, 1f).apply { near = 1f; far = 200f }

    private val batch = ModelBatch()
    private val shapes = ShapeRenderer()
    private val environment = Environment().apply {
        set(ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.52f, 1f))
        add(DirectionalLight().set(0.85f, 0.83f, 0.78f, -0.5f, -1f, -0.7f))
        add(DirectionalLight().set(0.25f, 0.27f, 0.32f, 0.7f, -0.3f, 0.5f))
    }

    private val models = mutableListOf<Disposable>()
    private val woodPixmap: Pixmap
    private val woodTexture: Texture
    private val tableInstance: ModelInstance
    private val standInstances: List<ModelInstance>
    private val plateInstance: ModelInstance
    private val stringInstances: List<ModelInstance>
    private val dieInstance: ModelInstance
    private val pieceModels = HashMap<PieceType, Model>()
    private val pieceInstances = HashMap<Piece, ModelInstance>()

    // Camera orbit around the plate.
    var orbitYaw = 0f
        private set
    var orbitPitch = 32f
        private set
    var orbitDistance = 27f
        private set

    private val cameraTarget = Vector3(0f, 5f, 0f)
    private var viewWidth = 1
    private var viewHeight = 1

    // Die animation.
    private var dieStartTime = 0f
    private var dieDuration = 0f
    private var dieClock = 0f
    private val dieAxis = Vector3(0f, 1f, 0f)
    private val dieTarget = Quaternion()
    private val dieHome = Vector3(0f, DIE_HALF, DIE_DISTANCE)
    val isDieRolling: Boolean get() = dieClock < dieStartTime + dieDuration

    private val tmpMatrix = Matrix4()
    private val tmpVec = Vector3()
    private val tmpVec2 = Vector3()
    private val tmpQuat = Quaternion()

    init {
        val builder = ModelBuilder()
        val attrs = (Usage.Position or Usage.Normal or Usage.TextureCoordinates).toLong()

        woodPixmap = makeWoodPixmap()
        // Managed, so the texture is rebuilt from the pixmap if the GL context is lost.
        woodTexture = Texture(PixmapTextureData(woodPixmap, null, true, false, true)).apply {
            setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
            setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        }
        val table = track(
            builder.createBox(
                Dimensions.TABLE_HALF_SIZE * 2f, 2f, Dimensions.TABLE_HALF_SIZE * 2f,
                Material(TextureAttribute.createDiffuse(woodTexture), ColorAttribute.createSpecular(0.15f, 0.15f, 0.15f, 1f)),
                attrs,
            ),
        )
        tableInstance = ModelInstance(table).apply { transform.setToTranslation(0f, -1f, 0f) }

        val steel = Material(ColorAttribute.createDiffuse(0.2f, 0.2f, 0.22f, 1f), ColorAttribute.createSpecular(0.5f, 0.5f, 0.5f, 1f), FloatAttribute.createShininess(40f))
        val postHeight = Dimensions.PIVOT_Y + 0.8f
        val armLength = 15f
        val post = track(builder.createCylinder(1.2f, postHeight, 1.2f, 20, steel, attrs))
        val base = track(builder.createCylinder(5f, 0.6f, 5f, 28, steel, attrs))
        val arm = track(builder.createBox(0.8f, 0.8f, armLength, steel, attrs))
        val postZ = -armLength
        standInstances = listOf(
            ModelInstance(base).apply { transform.setToTranslation(0f, 0.3f, postZ) },
            ModelInstance(post).apply { transform.setToTranslation(0f, postHeight / 2f, postZ) },
            ModelInstance(arm).apply { transform.setToTranslation(0f, Dimensions.PIVOT_Y + 0.4f, postZ + armLength / 2f) },
        )

        val plateMaterial = Material(
            ColorAttribute.createDiffuse(0.97f, 0.97f, 0.95f, 1f),
            ColorAttribute.createSpecular(0.4f, 0.4f, 0.4f, 1f),
            FloatAttribute.createShininess(30f),
        )
        plateInstance = ModelInstance(
            track(
                builder.createCylinder(
                    Dimensions.PLATE_RADIUS * 2f, Dimensions.PLATE_THICKNESS, Dimensions.PLATE_RADIUS * 2f, 48, plateMaterial, attrs,
                ),
            ),
        )

        val stringMaterial = Material(ColorAttribute.createDiffuse(0.88f, 0.82f, 0.62f, 1f))
        val stringModel = track(builder.createCylinder(0.1f, 1f, 0.1f, 6, stringMaterial, attrs))
        stringInstances = List(3) { ModelInstance(stringModel) }

        for (shape in PieceShape.entries) for (color in PieceColor.entries) {
            val material = Material(
                ColorAttribute.createDiffuse(color.r, color.g, color.b, 1f),
                ColorAttribute.createSpecular(0.35f, 0.35f, 0.35f, 1f),
                FloatAttribute.createShininess(25f),
            )
            pieceModels[PieceType(color, shape)] =
                track(builder.createCylinder(shape.radius * 2f, shape.height, shape.radius * 2f, 32, material, attrs))
        }

        dieInstance = ModelInstance(track(buildDie(builder)))
        resetDie()
        setOrbit(0f, orbitPitch, orbitDistance)
    }

    private fun <T : Disposable> track(model: T): T = model.also { models += it }

    private fun buildDie(builder: ModelBuilder): Model {
        val usage = (Usage.Position or Usage.Normal).toLong()
        builder.begin()
        val white = Material(ColorAttribute.createDiffuse(0.97f, 0.97f, 0.95f, 1f), ColorAttribute.createSpecular(0.3f, 0.3f, 0.3f, 1f))
        builder.part("cube", GL20.GL_TRIANGLES, usage, white).box(DIE_HALF * 2f, DIE_HALF * 2f, DIE_HALF * 2f)
        for ((i, normal) in DIE_NORMALS.withIndex()) {
            val c = PieceColor.entries[i]
            val dot = builder.part("dot$i", GL20.GL_TRIANGLES, usage, Material(ColorAttribute.createDiffuse(c.r, c.g, c.b, 1f)))
            val o = DIE_HALF + 0.015f
            dot.circle(DIE_HALF * 0.62f, 28, normal.x * o, normal.y * o, normal.z * o, normal.x, normal.y, normal.z)
        }
        return builder.end()
    }

    /** Which die face carries [color]; faces follow the order of [PieceColor.entries]. */
    private fun faceNormal(color: PieceColor): Vector3 = DIE_NORMALS[color.ordinal]

    // ---- camera -----------------------------------------------------------------------------------------------

    fun resize(width: Int, height: Int) {
        viewWidth = width.coerceAtLeast(1)
        viewHeight = height.coerceAtLeast(1)
        val aspect = viewWidth.toFloat() / viewHeight
        // Keep the plate and the die in view: fixed horizontal field of view on tall screens, fixed vertical on wide ones.
        val vertical = if (aspect < 1f) 2f * atan(tan(Math.toRadians(HORIZONTAL_FOV / 2.0)).toFloat() / aspect) else Math.toRadians(VERTICAL_FOV.toDouble()).toFloat()
        camera.fieldOfView = Math.toDegrees(vertical.toDouble()).toFloat()
        camera.viewportWidth = viewWidth.toFloat()
        camera.viewportHeight = viewHeight.toFloat()
        updateCamera()
    }

    fun setOrbit(yaw: Float, pitch: Float, distance: Float) {
        orbitYaw = yaw
        orbitPitch = pitch.coerceIn(10f, 80f)
        orbitDistance = distance.coerceIn(18f, 48f)
        updateCamera()
    }

    fun orbitBy(deltaYaw: Float, deltaPitch: Float) = setOrbit(orbitYaw + deltaYaw, orbitPitch + deltaPitch, orbitDistance)

    fun zoomBy(factor: Float) = setOrbit(orbitYaw, orbitPitch, orbitDistance * factor)

    private fun updateCamera() {
        val yaw = orbitYaw * MathUtils.degreesToRadians
        val pitch = orbitPitch * MathUtils.degreesToRadians
        camera.position.set(
            cameraTarget.x + orbitDistance * sin(yaw) * cos(pitch),
            cameraTarget.y + orbitDistance * sin(pitch),
            cameraTarget.z + orbitDistance * cos(yaw) * cos(pitch),
        )
        camera.up.set(0f, 1f, 0f)
        camera.lookAt(cameraTarget)
        camera.update()
    }

    /** The point on the horizontal plane at world height [y] that lies under the screen position, or null. */
    fun pickOnPlane(screenX: Float, screenY: Float, y: Float, out: Vector3): Vector3? {
        val ray = camera.getPickRay(screenX, screenY)
        return if (Intersector.intersectRayPlane(ray, Plane(Vector3.Y, -y), out)) out else null
    }

    // ---- die --------------------------------------------------------------------------------------------------

    private fun resetDie() {
        dieInstance.transform.set(dieHome, Quaternion())
        dieStartTime = 0f
        dieDuration = 0f
        dieClock = 0f
    }

    /** Tumbles the die and leaves it showing [result] on top after [duration] seconds. */
    fun rollDie(result: PieceColor, duration: Float, random: kotlin.random.Random = kotlin.random.Random.Default) {
        val n = faceNormal(result)
        if (n.y < -0.99f) dieTarget.setFromAxis(Vector3.X, 180f) else dieTarget.setFromCross(n, Vector3.Y)
        dieTarget.mulLeft(Quaternion(Vector3.Y, random.nextFloat() * 360f))
        dieAxis.set(random.nextFloat() - 0.5f, random.nextFloat() - 0.2f, random.nextFloat() - 0.5f).nor()
        dieStartTime = dieClock
        dieDuration = duration
    }

    fun showDieResult(result: PieceColor) {
        rollDie(result, 0.001f)
        dieClock += 0.01f
        updateDie()
    }

    private fun updateDie() {
        // The die stays on the table in front of the viewer, wherever the camera has been turned to.
        val yaw = orbitYaw * MathUtils.degreesToRadians
        dieHome.set(sin(yaw) * DIE_DISTANCE, DIE_HALF, cos(yaw) * DIE_DISTANCE)
        val u = if (dieDuration <= 0f) 1f else ((dieClock - dieStartTime) / dieDuration).coerceIn(0f, 1f)
        val remaining = (1f - u)
        val spin = remaining * remaining * 900f
        val hop = abs(sin(u * 3.2f * PI.toFloat())) * remaining * 3.2f
        tmpQuat.setFromAxis(dieAxis, spin).mul(dieTarget)
        tmpVec.set(dieHome.x, dieHome.y + hop, dieHome.z)
        dieInstance.transform.set(tmpVec, tmpQuat)
    }

    // ---- drawing ----------------------------------------------------------------------------------------------

    fun update(delta: Float) {
        dieClock += delta
        updateDie()
    }

    private fun placeString(instance: ModelInstance, from: Vector3, to: Vector3) {
        tmpVec.set(to).sub(from)
        val length = tmpVec.len()
        tmpVec.nor()
        if (tmpVec.y > 0.9999f) tmpQuat.idt() else tmpQuat.setFromCross(Vector3.Y, tmpVec)
        tmpVec2.set(from).add(to).scl(0.5f)
        instance.transform.set(tmpVec2, tmpQuat, Vector3(1f, length, 1f))
    }

    private fun instanceFor(piece: Piece): ModelInstance =
        pieceInstances.getOrPut(piece) { ModelInstance(pieceModels.getValue(piece.type)) }

    fun render(world: BalanceWorld, held: HeldPiece?) {
        world.plateTransform(plateInstance.transform)
        val pivot = tmpVec.set(0f, Dimensions.PIVOT_Y, 0f).cpy()
        for ((i, s) in stringInstances.withIndex()) {
            val a = (90f + 120f * i) * MathUtils.degreesToRadians
            val rim = world.plateToWorld(
                Vector3(cos(a) * Dimensions.PLATE_RADIUS * 0.96f, Dimensions.PLATE_THICKNESS / 2f, sin(a) * Dimensions.PLATE_RADIUS * 0.96f),
            )
            placeString(s, rim, pivot)
        }
        for (piece in world.pieces) world.pieceTransform(piece, instanceFor(piece).transform)
        pieceInstances.keys.retainAll { it in world.pieces || it == held?.piece }

        held?.let {
            instanceFor(it.piece).transform.set(it.position, BalanceWorld.pieceRotation(it.yaw, it.lying, tmpQuat))
        }

        batch.begin(camera)
        batch.render(tableInstance, environment)
        batch.render(standInstances, environment)
        batch.render(plateInstance, environment)
        batch.render(stringInstances, environment)
        batch.render(dieInstance, environment)
        for (piece in world.pieces) batch.render(instanceFor(piece), environment)
        held?.let { batch.render(instanceFor(it.piece), environment) }
        batch.end()

        held?.let { drawDropGuide(world, it) }
    }

    /** A ring on the plate and a line down to it, showing where the held piece will land. */
    private fun drawDropGuide(world: BalanceWorld, held: HeldPiece) {
        val top = Dimensions.PLATE_THICKNESS / 2f
        // Plate-frame position straight below the held piece.
        val local = tmpVec.set(held.position)
        world.plateTransform(tmpMatrix)
        local.mul(Matrix4(tmpMatrix).inv())
        val cx = local.x
        val cz = local.z
        val radius = held.footprint

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glLineWidth(2f)
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color(0.1f, 0.12f, 0.2f, 0.9f)
        val segments = 32
        var prev: Vector3? = null
        for (i in 0..segments) {
            val a = i * 2f * PI.toFloat() / segments
            val p = world.plateToWorld(Vector3(cx + cos(a) * radius, top, cz + sin(a) * radius))
            prev?.let { shapes.line(it, p) }
            prev = p
        }
        val ground = world.plateToWorld(Vector3(cx, top, cz))
        shapes.line(Vector3(held.position).sub(0f, held.halfHeight, 0f), ground)
        shapes.end()
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        models.forEach { it.dispose() }
        woodTexture.dispose()
        woodPixmap.dispose()
    }

    companion object {
        private const val HORIZONTAL_FOV = 52f
        private const val VERTICAL_FOV = 50f
        const val DIE_HALF = 1.1f
        private const val DIE_DISTANCE = 11.5f

        /** Outward normals of the six die faces. */
        private val DIE_NORMALS = listOf(
            Vector3(1f, 0f, 0f), Vector3(-1f, 0f, 0f), Vector3(0f, 1f, 0f),
            Vector3(0f, -1f, 0f), Vector3(0f, 0f, 1f), Vector3(0f, 0f, -1f),
        )

        private fun makeWoodPixmap(): Pixmap {
            val size = 256
            val pixmap = Pixmap(size, size, Pixmap.Format.RGB888)
            val planks = 6
            for (y in 0 until size) {
                val plank = y * planks / size
                val shade = 0.9f + 0.1f * sin(plank * 12.9898f).let { it - it.toInt() }
                for (x in 0 until size) {
                    val grain = 0.5f + 0.5f * sin(x * 0.08f + plank * 3f + sin(y * 0.15f) * 0.6f)
                    val edge = if (y * planks % size < 2 * planks) 0.55f else 1f
                    val v = (0.52f + 0.1f * grain) * shade * edge
                    pixmap.drawPixel(x, y, Color.rgba8888(v * 0.95f, v * 0.68f, v * 0.45f, 1f))
                }
            }
            return pixmap
        }
    }
}
