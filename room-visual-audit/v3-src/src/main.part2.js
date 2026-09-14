    const a=geo.attributes.position;
    for(let i=0;i<a.count;i++){
      const x=a.getX(i), y=a.getY(i);
      const z=Math.sin((x/width+.5)*Math.PI*8)*.045 + Math.sin((y+1)*5)*.006;
      a.setZ(i,z);
    }
    geo.computeVertexNormals();
    return mesh(geo,mat.curtain,{p:[xCenter,1.72,backZ+.11],parent:room});
  }
  curtainPanel(winL-.18,.52); curtainPanel(winR+.18,.52);
  cyl(.025,.025,2.45,12,mat.black,{p:[win.cx,2.63,backZ+.16],r:[0,0,Math.PI/2],parent:room});
  for(const x of [winL-.46,winR+.46]) cyl(.07,.07,.07,10,mat.black,{p:[x,2.63,backZ+.16],r:[0,0,Math.PI/2],parent:room});

  // ---------- Bed ----------
  const bed=new THREE.Group(); bed.position.set(-1.55,0,.18); bed.rotation.y=.02; room.add(bed);
  rounded(1.62,.28,2.28,.07,4,mat.darkWood,{p:[0,.27,0],parent:bed});
  rounded(1.52,.22,2.12,.11,5,mat.fabric,{p:[0,.51,0],parent:bed});
  // upholstered headboard with inset ribs
  rounded(1.72,1.05,.14,.07,4,mat.fabric,{p:[0,1.05,-1.08],parent:bed});
  for(let x=-.55;x<=.55;x+=.275) box(.018,.78,.018,mat.trim,{p:[x,1.05,-1.0],parent:bed,cast:false});
  // duvet
  rounded(1.48,.16,1.56,.09,5,mat.duvet,{p:[0,.70,.24],parent:bed});
  // duvet fold lip near foot
  rounded(1.48,.20,.28,.09,5,mat.duvet,{p:[0,.66,1.0],parent:bed});
  // pillows
  rounded(.65,.16,.42,.14,6,mat.fabric,{p:[-.36,.75,-.72],r:[0,.05,.05],parent:bed});
  rounded(.65,.16,.42,.14,6,mat.fabric,{p:[ .36,.75,-.72],r:[0,-.05,-.05],parent:bed});
  // bedside table
  rounded(.48,.56,.43,.045,4,mat.darkWood,{p:[-.98,.31,-.77],parent:bed});
  box(.30,.04,.025,mat.black,{p:[-.98,.32,-.545],parent:bed});

  // ---------- Desk ----------
  const desk=new THREE.Group(); desk.position.set(1.08,0,-1.50); room.add(desk);
  rounded(1.82,.10,.68,.045,4,mat.wood,{p:[0,.82,0],parent:desk});
  // tapered legs using 4-sided cylinders
  for(const x of [-.78,.78]) for(const z of [-.25,.25]) cyl(.035,.055,.80,4,mat.black,{p:[x,.40,z],parent:desk});
  // drawer under top with lip and handle
  rounded(.66,.26,.55,.025,3,mat.darkWood,{p:[.46,.64,0],parent:desk});
  rounded(.52,.035,.025,.012,3,mat.black,{p:[.46,.64,.292],parent:desk,cast:false});
  // desktop accessories
  rounded(.66,.025,.34,.02,3,new THREE.MeshStandardMaterial({color:0x605f57,roughness:.9}),{p:[-.30,.89,.01],parent:desk});
  // notebook
  rounded(.28,.026,.38,.025,3,new THREE.MeshStandardMaterial({color:0xe8e1d2,roughness:.85}),{p:[.30,.90,.02],r:[0,.11,0],parent:desk});

  // monitor with a real inset screen and slim stand
  const monitor=new THREE.Group(); monitor.position.set(.20,1.30,-.15); desk.add(monitor);
  rounded(.86,.53,.055,.035,4,mat.black,{parent:monitor});
  const screenMat=new THREE.MeshStandardMaterial({color:0x172329,emissive:0x214652,emissiveIntensity:.42,roughness:.22,metalness:.08});
  rounded(.76,.43,.015,.025,3,screenMat,{p:[0,0,.036],parent:monitor,cast:false});
  box(.07,.31,.06,mat.black,{p:[0,-.40,0],parent:monitor});
  rounded(.42,.035,.22,.025,3,mat.black,{p:[0,-.56,.02],parent:monitor});
  // keyboard, mouse and ceramic mug
  rounded(.54,.035,.19,.025,3,new THREE.MeshStandardMaterial({color:0x353937,roughness:.72}),{p:[-.13,.91,.20],parent:desk});
  rounded(.09,.035,.14,.04,4,mat.black,{p:[.33,.91,.20],parent:desk});
  const mugMat=new THREE.MeshPhysicalMaterial({color:0xe9e3d9,roughness:.44,clearcoat:.5,clearcoatRoughness:.22});
  cyl(.07,.06,.13,18,mugMat,{p:[.67,.98,.12],parent:desk});
  const mugHandle=mesh(new THREE.TorusGeometry(.06,.012,8,16,Math.PI*1.45),mugMat,{p:[.74,1.01,.12],r:[Math.PI/2,0,Math.PI/2],parent:desk});

  // desk lamp, clickable
  const lamp=new THREE.Group();lamp.name='DeskLamp';lamp.position.set(-.58,.90,.05);desk.add(lamp);
  const lampBase=rounded(.24,.055,.18,.045,4,mat.black,{p:[0,.03,0],parent:lamp});
  const arm1=cyl(.018,.018,.52,10,mat.black,{p:[0,.29,0],r:[0,0,-.42],parent:lamp});
  const elbow=new THREE.Vector3(.105,.52,0);
  cyl(.018,.018,.40,10,mat.black,{p:[.20,.68,0],r:[0,0,.75],parent:lamp});
  const shadeGroup=new THREE.Group();shadeGroup.position.set(.34,.78,.0);shadeGroup.rotation.z=-.55;lamp.add(shadeGroup);
  const shade=mesh(new THREE.CylinderGeometry(.06,.19,.26,24,1,true),mat.lampShade,{r:[Math.PI/2,0,0],parent:shadeGroup});
  rounded(.07,.035,.07,.02,3,mat.black,{p:[0,0,.15],parent:shadeGroup});
  const deskSpot=new THREE.SpotLight(0xffd79a,0,4.2,THREE.MathUtils.degToRad(36),.5,1.4);
  deskSpot.position.set(.34,.78,.16);deskSpot.target.position.set(.1,.05,.45);deskSpot.castShadow=true;deskSpot.shadow.mapSize.set(512,512);lamp.add(deskSpot);lamp.add(deskSpot.target);
  lamp.userData.interactive='lamp';shade.userData.interactive='lamp';lampBase.userData.interactive='lamp';
  let lampOn=false;
  function setLamp(on){lampOn=on;deskSpot.intensity=on?38:0;shade.material=on?mat.lampShadeOn:mat.lampShade;}
  setLamp(false);

  // ---------- Chair ----------
  const chair=new THREE.Group(); chair.position.set(1.05,0,-.55); chair.rotation.y=Math.PI; room.add(chair);
  rounded(.68,.13,.62,.11,5,new THREE.MeshStandardMaterial({color:0x59615e,roughness:.88}),{p:[0,.62,0],parent:chair});
  // curved back panel
  const sh=new THREE.Shape();
  sh.moveTo(-.33,-.36);sh.quadraticCurveTo(-.40,.05,-.30,.46);sh.quadraticCurveTo(0,.57,.30,.46);sh.quadraticCurveTo(.40,.05,.33,-.36);sh.closePath();
  const backGeo=new THREE.ExtrudeGeometry(sh,{depth:.08,bevelEnabled:true,bevelSize:.025,bevelThickness:.025,bevelSegments:3,curveSegments:8});backGeo.translate(0,0,-.04);
  mesh(backGeo,new THREE.MeshStandardMaterial({color:0x4e5653,roughness:.87}),{p:[0,1.16,-.25],r:[-.08,0,0],parent:chair});
  cyl(.055,.055,.48,16,mat.black,{p:[0,.36,0],parent:chair});
  cyl(.11,.11,.08,16,mat.black,{p:[0,.14,0],parent:chair});
  for(let i=0;i<5;i++){
    const a=i*Math.PI*2/5;
    const spoke=new THREE.Group();spoke.rotation.y=a;spoke.position.y=.10;chair.add(spoke);
    box(.055,.045,.55,mat.black,{p:[0,0,.22],parent:spoke});
    cyl(.045,.045,.07,12,mat.black,{p:[0,-.015,.52],r:[0,0,Math.PI/2],parent:spoke});
  }

  // ---------- Shelf / decor ----------
  const shelf=new THREE.Group(); shelf.position.set(1.12,1.18,backZ+.25); shelf.rotation.y=0; room.add(shelf);
  rounded(1.42,.08,.30,.025,3,mat.darkWood,{p:[0,.52,0],parent:shelf});
  rounded(1.42,.08,.30,.025,3,mat.darkWood,{p:[0,-.05,0],parent:shelf});
  box(.07,.68,.28,mat.darkWood,{p:[-.66,.24,0],parent:shelf});
  box(.07,.68,.28,mat.darkWood,{p:[.66,.24,0],parent:shelf});
  const bookMats=[0x8f4d3a,0x35584f,0xb88c55,0x545663,0x7c705f].map(c=>new THREE.MeshStandardMaterial({color:c,roughness:.82}));
  let bx=-.48;
  for(let i=0;i<9;i++){
    const h=.25+(i%4)*.035,w=.07+(i%3)*.012;
    rounded(w,h,.20,.01,2,bookMats[i%bookMats.length],{p:[bx,-.05+.04+h/2,.02],r:[0,0,(i===4)?.08:0],parent:shelf});
    bx+=w+.028;
  }
  // plant
  cyl(.15,.11,.22,18,mat.pot,{p:[.42,.68,0],parent:shelf});
  for(let i=0;i<8;i++){
    const a=i*Math.PI*2/8;
    const leaf=mesh(new THREE.SphereGeometry(.075,10,8),mat.leaf,{p:[.42+Math.cos(a)*.12,.88+Math.sin(i*.7)*.07,Math.sin(a)*.10],s:[1.5,.42,.6],parent:shelf});
    leaf.rotation.z=a*.35;
  }

  // ---------- Wall art ----------
  const artFrame=rounded(1.08,.78,.06,.025,3,mat.darkWood,{p:[-1.18,1.93,backZ+.10],parent:room});
  mesh(new THREE.PlaneGeometry(.96,.66),new THREE.MeshStandardMaterial({map:tex.art,roughness:.72}),{p:[-1.18,1.93,backZ+.135],parent:room,cast:false});

  // ---------- Rug ----------
  rounded(2.25,.028,1.45,.14,5,mat.rug,{p:[.25,.025,.52],parent:room,cast:false});

  // ---------- Floor lamp ----------
  const floorLamp=new THREE.Group();floorLamp.position.set(2.12,0,1.50);room.add(floorLamp);
  cyl(.22,.22,.045,24,mat.black,{p:[0,.023,0],parent:floorLamp});
  cyl(.025,.025,1.56,14,mat.black,{p:[0,.80,0],parent:floorLamp});
  const floorShade=mesh(new THREE.CylinderGeometry(.20,.34,.42,32,1,true),mat.lampShadeOn,{p:[0,1.66,0],parent:floorLamp});
  const floorLight=new THREE.PointLight(0xffd6a0,6.5,3.2,2);floorLight.position.set(0,1.55,0);floorLamp.add(floorLight);


  // ceiling pendant gives the open-top cutaway a believable upper focal point
  const pendant=new THREE.Group();pendant.position.set(.25,ROOM_H-.02,.28);room.add(pendant);
  cyl(.012,.012,.42,10,mat.black,{p:[0,-.20,0],parent:pendant});
  mesh(new THREE.CylinderGeometry(.11,.28,.28,32,1,true),mat.lampShade,{p:[0,-.48,0],parent:pendant});
  const pendantLight=new THREE.PointLight(0xffd6a5,4.2,4.8,2);pendantLight.position.set(0,-.52,0);pendant.add(pendantLight);

  // ---------- radiator under window ----------
  const radiator=new THREE.Group();radiator.position.set(1.10,.55,backZ+.18);room.add(radiator);
  for(let i=-5;i<=5;i++) rounded(.075,.64,.11,.025,3,mat.trim,{p:[i*.10,0,0],parent:radiator});
  box(1.18,.05,.13,mat.trim,{p:[0,.33,0],parent:radiator});
  box(1.18,.05,.13,mat.trim,{p:[0,-.33,0],parent:radiator});

  // Grounding/contact shadows under the heaviest pieces.
  contactShadow(2.15,2.75,-1.55,.18,.38);
  contactShadow(2.45,1.25,1.08,-1.48,.30);
  contactShadow(1.25,1.15,1.05,-.55,.28);
  contactShadow(.85,.85,2.12,1.50,.24);

  // ---------- Lighting ----------
  const hemi=new THREE.HemisphereLight(0xe8f1f4,0x665b49,.46);scene.add(hemi);
  const sun=new THREE.DirectionalLight(0xffedcf,1.82);sun.position.set(4.7,7.0,5.2);sun.target.position.set(.7,0,-2.3);sun.castShadow=true;sun.shadow.mapSize.set(2048,2048);sun.shadow.camera.near=.5;sun.shadow.camera.far=20;sun.shadow.camera.left=-6;sun.shadow.camera.right=6;sun.shadow.camera.top=6;sun.shadow.camera.bottom=-4;sun.shadow.bias=-.0005;sun.shadow.normalBias=.025;scene.add(sun,sun.target);
  const windowFill=new THREE.RectAreaLight(0xcfeaff,3.7,1.6,1.2);windowFill.position.set(win.cx,win.cy,backZ+.28);windowFill.lookAt(win.cx,1.2,1.5);scene.add(windowFill);
  const bounce=new THREE.PointLight(0xffd7b0,1.15,6,2);bounce.position.set(-1.8,2.4,2.0);scene.add(bounce);

  // ---------- Interaction ----------
  const raycaster=new THREE.Raycaster();
  const pointer=new THREE.Vector2();
  let down={x:0,y:0};
  canvas.addEventListener('pointerdown',e=>{down.x=e.clientX;down.y=e.clientY;});
  canvas.addEventListener('pointerup',e=>{
    const moved=Math.hypot(e.clientX-down.x,e.clientY-down.y);
    if(moved>8) return;
    const rect=canvas.getBoundingClientRect();
    pointer.x=((e.clientX-rect.left)/rect.width)*2-1;
    pointer.y=-((e.clientY-rect.top)/rect.height)*2+1;
    raycaster.setFromCamera(pointer,camera);
    const hits=raycaster.intersectObjects(scene.children,true);
    for(const h of hits){
      let o=h.object;
      while(o){
        if(o.userData?.interactive==='lamp'){setLamp(!lampOn);hintEl.classList.add('hide');return;}
        o=o.parent;
      }
    }
  });

  function resetView(){
    const mobile = innerWidth < 620 || innerHeight > innerWidth * 1.45;
    camera.position.set(mobile ? 5.35 : 6.15, mobile ? 3.05 : 3.55, mobile ? 6.35 : 6.75);
    controls.target.set(.05,1.15,-.15);
    controls.update();
  }
  resetBtn.addEventListener('click',resetView);
  window.addEventListener('keydown',e=>{
    if(e.code==='KeyR') resetView();
    if(e.code==='KeyL') setLamp(!lampOn);
  });

  setTimeout(()=>hintEl.classList.add('hide'),1500);

  function resize(){
    const w=innerWidth,h=innerHeight;
    renderer.setSize(w,h,false);
    camera.aspect=w/h;
    camera.updateProjectionMatrix();
  }
  addEventListener('resize',resize);
  addEventListener('orientationchange',()=>setTimeout(resize,150));

  renderer.render(scene,camera);

  const clock=new THREE.Clock();
  function frame(){
    const dt=Math.min(clock.getDelta(),.05);
    controls.update(dt);
    renderer.render(scene,camera);
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
} catch(err){
  fail(err);
}
