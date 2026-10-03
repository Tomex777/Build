package studio.artistscene.app

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import studio.artistscene.core.*

/** Uses the real importer, durable scene editor, Filament rigs and application viewport. */
class AnimeTreeSceneTest {
 @Test fun composePoseAndReopen() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  val device=UiDevice.getInstance(instrumentation)
  device.setOrientationLeft()
  val proof=requireNotNull(context.getExternalFilesDir(null))
  fun find(tag:String)=requireNotNull(device.wait(Until.findObject(By.res(tag)),60_000)){"Missing $tag"}
  fun shot(name:String) { SystemClock.sleep(5_000); assertTrue(device.takeScreenshot(File(proof,"anime-$name.png"))) }
  val importer=SceneAssetImporter(context)
  fun imported(file:String,kind:ActorKind,id:String,name:String,transform:Transform):Actor {
   val staged=File(context.cacheDir,"$file.glb")
   instrumentation.context.assets.open("anime-tree/$file.glb").use { input -> staged.outputStream().use { input.copyTo(it) } }
   val result=importer.import(Uri.fromFile(staged),kind).getOrThrow()
   staged.delete()
   return result.actor.copy(id=id,name=name,transform=transform)
  }
  val actors=listOf(
   imported("girl",ActorKind.CHARACTER,"girl","Girl",Transform(position=Vec3(-.65f,0f,.7f),rotationEulerDegrees=Vec3(y=215f))),
   imported("boy",ActorKind.CHARACTER,"boy","Boy",Transform(position=Vec3(.65f,0f,.8f),rotationEulerDegrees=Vec3(y=145f),scale=Vec3(1.03f,1.03f,1.03f))),
   imported("tree",ActorKind.ENVIRONMENT,"tree","Tree",Transform(position=Vec3(0f,0f,-.35f),scale=Vec3(1.4f,1.4f,1.4f))),
   imported("bike",ActorKind.VEHICLE,"bike-left","Bicycle left",Transform(position=Vec3(-.9f,0f,-.35f),rotationEulerDegrees=Vec3(y=90f,z=6f),scale=Vec3(.85f,.85f,.85f))),
   imported("bike",ActorKind.VEHICLE,"bike-right","Bicycle right",Transform(position=Vec3(.95f,0f,-.35f),rotationEulerDegrees=Vec3(y=82f,z=-6f),scale=Vec3(.85f,.85f,.85f))),
   imported("ground",ActorKind.ENVIRONMENT,"ground","Grass",Transform(position=Vec3(0f,-.10f,0f),scale=Vec3(2.3f,.05f,2.3f))),
  )
  val store=SceneProjectStore(context)
  val id="anime-under-tree"
  val camera=SceneCamera("camera-main","Scene camera",position=Vec3(3.3f,2.4f,6.5f),target=Vec3(0f,1.65f,0f),verticalFovDegrees=48f)
  store.save(SceneProject(id=id,name="Under the tree",actors=actors,cameras=listOf(camera),world=WorldSettings(backgroundHex="#B9D5E8",groundEnabled=false,gridEnabled=false)))
  // Let Mise discover the imported rigs, then use the same editor pose operations as UI.
  ActivityScenario.launch(MainActivity::class.java).use {
   find("project-open-$id").click();find("inspector")
   SystemClock.sleep(20_000)
   find("save-project").click();assertTrue(device.wait(Until.hasObject(By.text("Saved scene")),10_000))
   shot("rest-editor")
  }
  val discovered=store.load(id)
  for(a in discovered.actors.filter { it.kind==ActorKind.CHARACTER }) {
   assertTrue("${a.id} imported rig not discovered",a.rigDefinition!!.bones.size>40)
   Log.i("AnimeScene","${a.id} bones=${a.rigDefinition!!.bones.size}")
  }
  var editor=SceneEditorState(discovered)
  fun pose(id:String,name:String,rotation:Vec3) {
   editor=editor.selectActor(id)
   val bone=editor.selectedActor!!.rigDefinition!!.bones.single { it.name==name }
   editor=editor.setRigJointRotation(bone.id,rotation)
  }
  for(idc in listOf("girl","boy")) {
   val thigh=if(idc=="girl")125f else 115f
   val knee=if(idc=="girl")-105f else -140f
   pose(idc,"J_Bip_L_UpperLeg",Vec3(x=thigh,z=if(idc=="girl")-8f else -12f))
   pose(idc,"J_Bip_R_UpperLeg",Vec3(x=thigh,z=if(idc=="girl")8f else 12f))
   pose(idc,"J_Bip_L_LowerLeg",Vec3(x=knee));pose(idc,"J_Bip_R_LowerLeg",Vec3(x=knee))
   pose(idc,"J_Bip_L_UpperArm",Vec3(x=25f,z=70f));pose(idc,"J_Bip_R_UpperArm",Vec3(x=25f,z=-70f))
   pose(idc,"J_Bip_L_LowerArm",Vec3(y=-50f,z=-15f));pose(idc,"J_Bip_R_LowerArm",Vec3(y=50f,z=15f))
   pose(idc,"J_Bip_C_Spine",Vec3(x=if(idc=="boy")12f else -5f))
   pose(idc,"J_Bip_C_Head",Vec3(y=if(idc=="girl")-12f else 12f))
   editor=editor.selectActor(idc).setPosition(TransformAxis.Y,if(idc=="girl")-.68f else -.55f)
  }
  // Follow the skirt's existing rig with the seated legs. No skirt mesh is replaced.
  for(name in listOf("J_Sec_L_SkirtFront0","J_Sec_R_SkirtFront0")) {
   discovered.actors.first { it.id=="girl" }.rigDefinition!!.bones.firstOrNull { it.name==name }?.let { pose("girl",name,Vec3(x=65f)) }
  }
  assertTrue(editor.canUndo)
  val posed=editor.project
  val undo=editor.undo();assertNotEquals(posed,undo.project);assertEquals(posed,undo.redo().project)
  store.save(posed)
  ActivityScenario.launch(MainActivity::class.java).use {
   find("project-open-$id").click();find("inspector");SystemClock.sleep(20_000)
   shot("posed-editor")
   find("reference-mode").click();shot("posed-clean")
   find("exit-reference-mode").click()
   find("save-project").click();assertTrue(device.wait(Until.hasObject(By.text("Saved scene")),10_000))
  }
  val restored=store.load(id)
  for(idc in listOf("girl","boy")) {
   assertEquals(posed.actors.first { it.id==idc }.rig,restored.actors.first { it.id==idc }.rig)
   assertEquals(posed.actors.first { it.id==idc }.transform,restored.actors.first { it.id==idc }.transform)
  }
  // Save the app-authored scene plus original managed payloads for review.
  File(proof,"anime-under-tree.scene.json").writeText(SceneProjectCodec.encode(restored))
  val payloads=File(proof,"anime-assets").apply{mkdirs()}
  restored.actors.mapNotNull{it.asset}.distinctBy{it.relativePath}.forEach { asset ->
   val src=File(context.filesDir,asset.relativePath)
   src.copyTo(File(payloads,src.name),overwrite=true)
  }
  Log.i("AnimeScene","PASS imported=6 characters=2 bicycles=2 posed=2 save-reopen=true")
 }
}
