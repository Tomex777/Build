package studio.artistscene.app

import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.math.Transform
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentCameraManipulatorTest {
    private class Camera : CameraGestureDetector.CameraManipulator {
        var viewport = 0 to 0
        var updates = 0
        var gestures = 0
        override fun setViewport(width: Int, height: Int) { viewport = width to height }
        override fun getTransform(): Transform = error("No native camera needed by this test")
        override fun grabBegin(x: Int, y: Int, strafe: Boolean) { gestures++ }
        override fun grabUpdate(x: Int, y: Int) = Unit
        override fun grabEnd() = Unit
        override fun scrollBegin(x: Int, y: Int, separation: Float) = Unit
        override fun scrollUpdate(x: Int, y: Int, prevSeparation: Float, currSeparation: Float) { gestures++ }
        override fun scrollEnd() = Unit
        override fun update(deltaTime: Float) { updates++ }
    }

    @Test fun existingRenderLoopAndGesturesFollowReframedCamera() {
        val original = Camera()
        val reframed = Camera()
        val binding = CurrentCameraManipulator(original)
        // The renderer retains this reference for its entire surface lifetime.
        val renderLoopReference: CameraGestureDetector.CameraManipulator = binding
        renderLoopReference.setViewport(1080, 2400)
        renderLoopReference.update(0.016f)
        binding.replace(reframed)
        renderLoopReference.update(0.016f)
        renderLoopReference.grabBegin(100, 100, false)
        renderLoopReference.scrollUpdate(100, 100, 100f, 120f)
        assertEquals(1080 to 2400, reframed.viewport)
        assertEquals(1, original.updates)
        assertEquals(1, reframed.updates)
        assertEquals(0, original.gestures)
        assertEquals(2, reframed.gestures)
    }

    @Test fun replacementBeforeSurfaceAndAfterRotationReceivesCurrentDimensions() {
        val binding = CurrentCameraManipulator(Camera())
        val portrait = Camera()
        binding.replace(portrait)
        binding.setViewport(1080, 2400)
        binding.setViewport(2400, 1080)
        val landscape = Camera()
        binding.replace(landscape)
        assertEquals(2400 to 1080, landscape.viewport)
    }
}
