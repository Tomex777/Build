from pathlib import Path

p = Path('/tmp/room-v4/site/src/main.js')
s = p.read_text()

def repl(old: str, new: str, label: str):
    global s
    n = s.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 match, got {n}')
    s = s.replace(old, new)

repl('  renderer.toneMappingExposure = 0.96;', '  renderer.toneMappingExposure = 0.92;', 'exposure')
repl(
    '  scene.background = new THREE.Color(0xaab8bb);\n  scene.fog = new THREE.Fog(0xaab8bb, 16, 36);',
    '  scene.background = new THREE.Color(0x98a7aa);\n  scene.fog = new THREE.Fog(0x98a7aa, 16, 36);',
    'background'
)
repl(
    '  const camera = new THREE.PerspectiveCamera(48, innerWidth/innerHeight, 0.05, 80);\n  camera.position.set(6.15, 3.55, 6.75);',
    '  const portraitLayout = () => innerWidth < 620 || innerHeight > innerWidth * 1.45;\n  const landscapeMobile = () => innerHeight < 500 && innerWidth > innerHeight;\n  const viewFov = () => portraitLayout() ? 57 : (landscapeMobile() ? 45 : 47);\n  const viewPos = () => portraitLayout() ? [6.95,4.85,8.25] : (landscapeMobile() ? [5.85,3.40,6.10] : [5.85,3.65,6.30]);\n  const viewMaxDistance = () => portraitLayout() ? 14.0 : (landscapeMobile() ? 10.0 : 10.0);\n  const camera = new THREE.PerspectiveCamera(viewFov(), innerWidth/innerHeight, 0.05, 80);\n  camera.position.set(...viewPos());',
    'camera'
)
repl('  controls.maxDistance = 10.5;', '  controls.maxDistance = viewMaxDistance();', 'max distance')
repl(
    '  const floorLamp=new THREE.Group();floorLamp.position.set(2.12,0,1.50);room.add(floorLamp);',
    '  const floorLamp=new THREE.Group();floorLamp.position.set(-2.05,0,1.34);room.add(floorLamp);',
    'floor lamp position'
)
repl(
    '  const floorShade=mesh(new THREE.CylinderGeometry(.20,.34,.42,32,1,true),mat.lampShadeOn,{p:[0,1.66,0],parent:floorLamp});',
    '  const floorShade=mesh(new THREE.CylinderGeometry(.18,.30,.38,32,1,true),mat.lampShadeOn,{p:[0,1.63,0],parent:floorLamp});',
    'floor shade'
)
repl(
    '  const floorLight=new THREE.PointLight(0xffd6a0,6.5,3.2,2);floorLight.position.set(0,1.55,0);floorLamp.add(floorLight);',
    '  const floorLight=new THREE.PointLight(0xffd6a0,2.2,3.0,2);floorLight.position.set(0,1.52,0);floorLamp.add(floorLight);',
    'floor light'
)
repl(
    '  const pendantLight=new THREE.PointLight(0xffd6a5,4.2,4.8,2);pendantLight.position.set(0,-.52,0);pendant.add(pendantLight);',
    '  const pendantLight=new THREE.PointLight(0xffd6a5,1.45,4.6,2);pendantLight.position.set(0,-.52,0);pendant.add(pendantLight);',
    'pendant light'
)
repl(
    'function setLamp(on){lampOn=on;deskSpot.intensity=on?38:0;shade.material=on?mat.lampShadeOn:mat.lampShade;}',
    'function setLamp(on){lampOn=on;deskSpot.intensity=on?52:0;shade.material=on?mat.lampShadeOn:mat.lampShade;}',
    'desk lamp intensity'
)
repl('  const hemi=new THREE.HemisphereLight(0xe8f1f4,0x665b49,.46);scene.add(hemi);', '  const hemi=new THREE.HemisphereLight(0xe8f1f4,0x665b49,.30);scene.add(hemi);', 'hemi')
repl('  const sun=new THREE.DirectionalLight(0xffedcf,1.82);', '  const sun=new THREE.DirectionalLight(0xffedcf,2.12);', 'sun')
repl('  const windowFill=new THREE.RectAreaLight(0xcfeaff,3.7,1.6,1.2);', '  const windowFill=new THREE.RectAreaLight(0xcfeaff,2.35,1.6,1.2);', 'window fill')
repl('  const bounce=new THREE.PointLight(0xffd7b0,1.15,6,2);', '  const bounce=new THREE.PointLight(0xffd7b0,.70,6,2);', 'bounce')

old_reset = '''  function resetView(){
    const mobile = innerWidth < 620 || innerHeight > innerWidth * 1.45;
    camera.position.set(mobile ? 5.35 : 6.15, mobile ? 3.05 : 3.55, mobile ? 6.35 : 6.75);
    controls.target.set(.05,1.15,-.15);
    controls.update();
  }'''
new_reset = '''  function resetView(){
    camera.fov = viewFov();
    controls.maxDistance = viewMaxDistance();
    camera.position.set(...viewPos());
    controls.target.set(.05,1.10,-.18);
    camera.updateProjectionMatrix();
    controls.update();
  }'''
repl(old_reset, new_reset, 'reset view')

old_resize = '''  function resize(){
    const w=innerWidth,h=innerHeight;
    renderer.setSize(w,h,false);
    camera.aspect=w/h;
    camera.updateProjectionMatrix();
  }'''
new_resize = '''  function resize(){
    const w=innerWidth,h=innerHeight;
    renderer.setSize(w,h,false);
    camera.aspect=w/h;
    camera.fov=viewFov();
    controls.maxDistance=viewMaxDistance();
    camera.updateProjectionMatrix();
  }'''
repl(old_resize, new_resize, 'resize')

p.write_text(s)
