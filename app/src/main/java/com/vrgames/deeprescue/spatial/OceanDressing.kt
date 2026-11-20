package com.vrgames.deeprescue.spatial

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.Resource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.cos
import kotlin.math.sin

/**
 * v1.2 (PICO 1117/1118: 「美术设计比较单调」「要达到四星的话美术上需要做大加强」): the sea beyond the observation window
 * dressed — kelp swaying at the sides and back, shafts of light slanting down from the surface, a school of small fish
 * turning behind the reef. In the portal world's frame (the window at z 0, the scene behind it).
 */
class OceanDressing(private val ocean: Entity, keep: (Resource) -> Resource) {
    private val kelp = ArrayList<Pair<Entity, Double>>()
    private val shafts = ArrayList<Pair<Entity, UnlitMaterial>>()
    private val fish = ArrayList<Entity>()

    init {
        @Suppress("UNCHECKED_CAST")
        fun <T : Resource> k(r: T): T = keep(r) as T
        val cyl = k(MeshResource.createCylinder(1f, 1f))
        val cone = k(MeshResource.createCone(1f, 1f))
        val leafMat = k(PhysicallyBasedMaterial.create()).apply { setBaseColor(Color4(.24f, .44f, .2f, 1f)); setRoughness(.7f); setEmissiveColor(Color4(.04f, .08f, .03f, 1f)) }
        val leafMat2 = k(PhysicallyBasedMaterial.create()).apply { setBaseColor(Color4(.36f, .5f, .16f, 1f)); setRoughness(.7f); setEmissiveColor(Color4(.05f, .08f, .02f, 1f)) }
        val fishMat = k(PhysicallyBasedMaterial.create()).apply { setBaseColor(Color4(.75f, .8f, .86f, 1f)); setMetallic(.7f); setRoughness(.25f) }
        val fishMesh = k(MeshResource.createCapsule(.05f, .008f))
        // Kelp: tall wavy strands of short segments, at the sides and the back.
        val spots = listOf(-.72 to -.75, -.62 to -1.05, -.5 to -1.35, -.15 to -1.45, .22 to -1.4, .55 to -1.2, .7 to -.85, .66 to -1.5)
        for ((n, xz) in spots.withIndex()) {
            val (x, z) = xz
            val strand = Entity().apply { components.set(TransformComponent()) }
            strand.components[TransformComponent::class.java]!!.setPosition(Vector3(x.toFloat(), -.42f, z.toFloat()))
            val segs = 9
            var parent = strand
            for (i in 0 until segs) {
                val seg = Entity().apply { components.set(TransformComponent()) }
                seg.components[TransformComponent::class.java]!!.setPosition(Vector3(0f, if (i == 0) 0f else .1f, 0f))
                val blade = Entity().apply { components.set(TransformComponent()); components.set(ModelComponent(cyl, if (n % 2 == 0) leafMat else leafMat2)) }
                blade.components[TransformComponent::class.java]!!.apply { setPosition(Vector3(0f, .05f, 0f)); setScaleVector(Vector3(.014f, .1f, .006f)) }
                seg.addChild(blade)
                parent.addChild(seg)
                kelp += seg to (n * 1.3 + i * .35)
                parent = seg
            }
            ocean.addChild(strand)
        }
        // Light from the surface: soft cones from above, slanting.
        for (k in 0 until 5) {
            val m = k(UnlitMaterial.create(BlendingMode.TRANSPARENT)).apply { setBaseColor(Color4(.78f, .94f, 1f, 1f)); setOpacity(.06f); setDepthWrite(false); setCullingMode(MaterialCullingMode.NONE) }
            val e = Entity().apply { components.set(TransformComponent()); components.set(ModelComponent(cone, m)) }
            e.components[TransformComponent::class.java]!!.apply {
                setPosition(Vector3((-.6f + .3f * k), .9f, (-.9f - .15f * (k % 3))))
                setScaleVector(Vector3(.16f + .04f * (k % 2), 2.6f, .16f + .04f * (k % 2)))
                setEulerAngles(EulerAngles(8f, 0f, -10f + 5f * k))
            }
            ocean.addChild(e)
            shafts += e to m
        }
        // A school of small fish.
        for (i in 0 until 22) {
            val f = Entity().apply { components.set(TransformComponent()); components.set(ModelComponent(fishMesh, fishMat)) }
            ocean.addChild(f)
            fish += f
        }
    }

    fun frame(t: Double) {
        for ((seg, ph) in kelp) seg.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles((5 * sin(t * .9 + ph)).toFloat(), 0f, (6 * sin(t * .7 + ph * 1.3)).toFloat()))
        for ((k, s) in shafts.withIndex()) s.second.setOpacity((.05 + .025 * sin(t * .5 + k)).toFloat())
        val cx = .1 * sin(t * .1); val cz = -1.25 + .1 * cos(t * .13)
        for ((i, f) in fish.withIndex()) {
            val ph = t * .55 + i * .29
            val r = .28 + .06 * sin(i * 1.7)
            val x = cx + r * cos(ph)
            val z = cz + r * .5 * sin(ph)
            val y = .18 + .07 * sin(i * .9) + .02 * sin(ph * 3)
            f.components[TransformComponent::class.java]?.apply {
                setPosition(Vector3(x.toFloat(), y.toFloat(), z.toFloat()))
                setEulerAngles(EulerAngles(0f, Math.toDegrees(-ph).toFloat(), 90f))
            }
        }
    }
}
