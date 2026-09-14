import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';
import { RectAreaLightUniformsLib } from 'three/addons/lights/RectAreaLightUniformsLib.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';

const canvas = document.getElementById('scene');
const errorEl = document.getElementById('error');
const hintEl = document.getElementById('hint');
const resetBtn = document.getElementById('resetView');

const ROOM_W = 5.4;
const ROOM_D = 4.6;
const ROOM_H = 3.0;
const WALL_T = 0.14;
const FLOOR_Y = 0;

function fail(err){
  console.error(err);
  errorEl.hidden = false;
  errorEl.textContent = `Runtime error: ${err?.message || String(err)}`;
}

try {
  RectAreaLightUniformsLib.init();

  const renderer = new THREE.WebGLRenderer({canvas, antialias:true, powerPreference:'high-performance'});
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.8));
  renderer.setSize(innerWidth, innerHeight, false);
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 0.96;
  renderer.outputColorSpace = THREE.SRGBColorSpace;

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0xaab8bb);
  scene.fog = new THREE.Fog(0xaab8bb, 16, 36);

  const pmrem = new THREE.PMREMGenerator(renderer);
  const envRT = pmrem.fromScene(new RoomEnvironment(renderer), 0.035);
  scene.environment = envRT.texture;

  const camera = new THREE.PerspectiveCamera(48, innerWidth/innerHeight, 0.05, 80);
  camera.position.set(6.15, 3.55, 6.75);

  const controls = new OrbitControls(camera, canvas);
  controls.enableDamping = true;
  controls.dampingFactor = 0.065;
  controls.enablePan = false;
  controls.minDistance = 4.3;
  controls.maxDistance = 10.5;
  controls.minPolarAngle = THREE.MathUtils.degToRad(52);
  controls.maxPolarAngle = THREE.MathUtils.degToRad(76);
  // Keep the camera in the open cutaway quadrant so walls never hide the room.
  controls.minAzimuthAngle = THREE.MathUtils.degToRad(24);
  controls.maxAzimuthAngle = THREE.MathUtils.degToRad(72);
  controls.target.set(0.05, 1.15, -0.15);
  controls.update();

  const MAX_ANISO = renderer.capabilities.getMaxAnisotropy();

  function canvasTexture(size, draw, repeat=[1,1]){
    const c = document.createElement('canvas');
    c.width = c.height = size;
    const ctx = c.getContext('2d');
    draw(ctx,size);
    const t = new THREE.CanvasTexture(c);
    t.colorSpace = THREE.SRGBColorSpace;
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.repeat.set(repeat[0], repeat[1]);
    t.anisotropy = Math.min(8, MAX_ANISO);
    return t;
  }

  const tex = {
    wood: canvasTexture(512,(ctx,s)=>{
      ctx.fillStyle='#b5895f';ctx.fillRect(0,0,s,s);
      for(let y=0;y<s;y+=64){
        const g=ctx.createLinearGradient(0,y,0,y+64);
        g.addColorStop(0,'rgba(255,241,214,.12)');
        g.addColorStop(.45,'rgba(87,53,29,.06)');
        g.addColorStop(1,'rgba(255,255,255,.08)');
        ctx.fillStyle=g;ctx.fillRect(0,y,s,64);
        ctx.strokeStyle='rgba(73,45,26,.32)';ctx.lineWidth=2;
        ctx.beginPath();ctx.moveTo(0,y+62);ctx.lineTo(s,y+62);ctx.stroke();
      }
      for(let i=0;i<90;i++){
        const y=Math.random()*s; const amp=2+Math.random()*5;
        ctx.strokeStyle=`rgba(86,50,26,${0.04+Math.random()*0.09})`;
        ctx.lineWidth=.7+Math.random()*1.3;
        ctx.beginPath();
        for(let x=0;x<=s;x+=16){
          const yy=y+Math.sin(x*.035+Math.random()*.15)*amp;
          if(x===0)ctx.moveTo(x,yy);else ctx.lineTo(x,yy);
        }
        ctx.stroke();
      }
      for(let i=0;i<18;i++){
        const x=Math.random()*s,y=Math.random()*s;
        ctx.strokeStyle='rgba(80,45,22,.16)';
        ctx.beginPath();ctx.ellipse(x,y,8+Math.random()*20,2+Math.random()*5,0,0,Math.PI*2);ctx.stroke();
      }
    },[2.6,2.1]),
    plaster: canvasTexture(256,(ctx,s)=>{
      ctx.fillStyle='#e7e0d5';ctx.fillRect(0,0,s,s);
      const img=ctx.getImageData(0,0,s,s);const d=img.data;
      for(let i=0;i<d.length;i+=4){const n=(Math.random()-.5)*10;d[i]+=n;d[i+1]+=n;d[i+2]+=n;}
      ctx.putImageData(img,0,0);
    },[4,3]),
    fabric: canvasTexture(256,(ctx,s)=>{
      ctx.fillStyle='#d6d1c9';ctx.fillRect(0,0,s,s);
      ctx.strokeStyle='rgba(60,54,48,.09)';ctx.lineWidth=1;
      for(let i=0;i<s;i+=5){ctx.beginPath();ctx.moveTo(i,0);ctx.lineTo(i,s);ctx.stroke();ctx.beginPath();ctx.moveTo(0,i);ctx.lineTo(s,i);ctx.stroke();}
    },[3,2]),
    rug: canvasTexture(256,(ctx,s)=>{
      ctx.fillStyle='#77786d';ctx.fillRect(0,0,s,s);
      ctx.fillStyle='rgba(238,232,219,.42)';
      for(let y=18;y<s;y+=34){for(let x=18;x<s;x+=34){ctx.beginPath();ctx.arc(x,y,5,0,Math.PI*2);ctx.fill();}}
    },[2,1.5]),
    art: canvasTexture(512,(ctx,s)=>{
      ctx.fillStyle='#d9d1c3';ctx.fillRect(0,0,s,s);
      ctx.fillStyle='#4f5b54';ctx.beginPath();ctx.ellipse(160,250,95,155,-.45,0,Math.PI*2);ctx.fill();
      ctx.fillStyle='#b86e4f';ctx.beginPath();ctx.arc(335,160,84,0,Math.PI*2);ctx.fill();
      ctx.fillStyle='#2c3131';ctx.fillRect(260,290,150,78);
      ctx.strokeStyle='#f1eadf';ctx.lineWidth=18;ctx.beginPath();ctx.moveTo(65,95);ctx.lineTo(430,395);ctx.stroke();
    })
  };

  const mat = {
    wall: new THREE.MeshStandardMaterial({map:tex.plaster,color:0xf0ebe3,roughness:.91}),
    trim: new THREE.MeshStandardMaterial({color:0xd8d0c5,roughness:.72}),
    wood: new THREE.MeshStandardMaterial({map:tex.wood,color:0xffffff,roughness:.52,metalness:.03}),
    darkWood: new THREE.MeshStandardMaterial({map:tex.wood,color:0x6f4c35,roughness:.58,metalness:.02}),
    black: new THREE.MeshStandardMaterial({color:0x242725,roughness:.56,metalness:.34}),
    steel: new THREE.MeshStandardMaterial({color:0x80837f,roughness:.32,metalness:.82}),
    fabric: new THREE.MeshStandardMaterial({map:tex.fabric,color:0xe6e0d8,roughness:.98}),
    duvet: new THREE.MeshStandardMaterial({map:tex.fabric,color:0xb9c1bb,roughness:.95}),
    accent: new THREE.MeshStandardMaterial({color:0xa55f49,roughness:.77}),
    rug: new THREE.MeshStandardMaterial({map:tex.rug,color:0xffffff,roughness:1}),
    glass: new THREE.MeshPhysicalMaterial({color:0xb9d4df,roughness:.05,transmission:.18,transparent:true,opacity:.30,metalness:0,clearcoat:1,clearcoatRoughness:.04,side:THREE.DoubleSide}),
    curtain: new THREE.MeshStandardMaterial({color:0xd8d1c3,roughness:.96,side:THREE.DoubleSide}),
    leaf: new THREE.MeshStandardMaterial({color:0x476749,roughness:.9}),
    pot: new THREE.MeshStandardMaterial({color:0xb27456,roughness:.85}),
    lampShade: new THREE.MeshStandardMaterial({color:0xe7dbc8,roughness:.88,side:THREE.DoubleSide}),
    lampShadeOn: new THREE.MeshStandardMaterial({color:0xf0dfbd,roughness:.78,emissive:0xffc96c,emissiveIntensity:.38,side:THREE.DoubleSide})
  };

  function mesh(geo, material, {p=[0,0,0],r=[0,0,0],s=[1,1,1],cast=true,receive=true,parent=scene,name=''}={}){
    const m=new THREE.Mesh(geo,material);m.position.set(...p);m.rotation.set(...r);m.scale.set(...s);m.castShadow=cast;m.receiveShadow=receive;m.name=name;parent.add(m);return m;
  }
  function rounded(w,h,d,r=.06,segments=4,material=mat.wood,opts={}){
    return mesh(new RoundedBoxGeometry(w,h,d,segments,r),material,opts);
  }
  function box(w,h,d,material,opts={}){return mesh(new THREE.BoxGeometry(w,h,d),material,opts)}
  function cyl(rt,rb,h,seg,material,opts={}){return mesh(new THREE.CylinderGeometry(rt,rb,h,seg),material,opts)}


  // Soft baked-style contact shadows. They do not replace real shadows; they give
  // furniture the grounded weight that disappears under broad ambient lighting.
  const contactTex = canvasTexture(256,(ctx,size)=>{
    const g=ctx.createRadialGradient(size/2,size/2,8,size/2,size/2,size/2);
    g.addColorStop(0,'rgba(20,16,12,.48)');
    g.addColorStop(.45,'rgba(20,16,12,.22)');
    g.addColorStop(1,'rgba(20,16,12,0)');
    ctx.fillStyle=g;ctx.fillRect(0,0,size,size);
  });
  contactTex.wrapS=contactTex.wrapT=THREE.ClampToEdgeWrapping;
  const contactMat = new THREE.MeshBasicMaterial({map:contactTex,transparent:true,depthWrite:false,opacity:.48,toneMapped:false});
  function contactShadow(w,d,x,z,opacity=.45){
    const m=contactMat.clone();m.opacity=opacity;
    return mesh(new THREE.PlaneGeometry(w,d),m,{p:[x,.012,z],r:[-Math.PI/2,0,0],cast:false,receive:false,parent:room});
  }

  // ---------- Architectural shell ----------
  const room = new THREE.Group();scene.add(room);
  // floor slab
  box(ROOM_W,.16,ROOM_D,mat.wood,{p:[0,-.08,0],parent:room,cast:false});
  // back wall, segmented around window opening
  const backZ = -ROOM_D/2;
  const win = {cx:1.05, cy:1.83, w:1.65, h:1.25};
  const winL=win.cx-win.w/2, winR=win.cx+win.w/2, winB=win.cy-win.h/2, winT=win.cy+win.h/2;
  box(winL+ROOM_W/2,ROOM_H,WALL_T,mat.wall,{p:[(-ROOM_W/2+winL)/2,ROOM_H/2,backZ],parent:room});
  box(ROOM_W/2-winR,ROOM_H,WALL_T,mat.wall,{p:[(winR+ROOM_W/2)/2,ROOM_H/2,backZ],parent:room});
  box(win.w,winB,WALL_T,mat.wall,{p:[win.cx,winB/2,backZ],parent:room});
  box(win.w,ROOM_H-winT,WALL_T,mat.wall,{p:[win.cx,(winT+ROOM_H)/2,backZ],parent:room});
  // side walls
  box(WALL_T,ROOM_H,ROOM_D,mat.wall,{p:[-ROOM_W/2,ROOM_H/2,0],parent:room});
  // Right/front side intentionally open: architectural cutaway rather than an occluding box.
  // baseboards
  box(ROOM_W,.13,.06,mat.trim,{p:[0,.065,backZ+.09],parent:room});
  box(.06,.13,ROOM_D,mat.trim,{p:[-ROOM_W/2+.09,.065,0],parent:room});
  // Finished cut edges make the open room read as a deliberate architectural section.
  box(ROOM_W,.09,.08,mat.darkWood,{p:[0,.045,ROOM_D/2-.02],parent:room});
  box(.08,.09,ROOM_D,mat.darkWood,{p:[ROOM_W/2-.02,.045,0],parent:room});


  // window reveal + frame
  const frameDepth=.12;
  box(win.w+.15,.09,frameDepth,mat.trim,{p:[win.cx,winT+.045,backZ+.02],parent:room});
  box(win.w+.15,.09,frameDepth,mat.trim,{p:[win.cx,winB-.045,backZ+.02],parent:room});
  box(.09,win.h+.12,frameDepth,mat.trim,{p:[winL-.045,win.cy,backZ+.02],parent:room});
  box(.09,win.h+.12,frameDepth,mat.trim,{p:[winR+.045,win.cy,backZ+.02],parent:room});
  box(.05,win.h,frameDepth,mat.trim,{p:[win.cx,win.cy,backZ+.025],parent:room});
  box(win.w+.28,.08,.25,mat.trim,{p:[win.cx,winB-.09,backZ+.10],parent:room});
  mesh(new THREE.PlaneGeometry(win.w,win.h),mat.glass,{p:[win.cx,win.cy,backZ-.015],parent:room,cast:false,receive:false});

  // exterior backdrop visible through glass
  const outsideTex = canvasTexture(512,(ctx,s)=>{
    const g=ctx.createLinearGradient(0,0,0,s);g.addColorStop(0,'#91b4c9');g.addColorStop(.58,'#cad8d8');g.addColorStop(1,'#8da07e');ctx.fillStyle=g;ctx.fillRect(0,0,s,s);
    ctx.fillStyle='rgba(86,105,77,.72)';
    for(let i=0;i<9;i++){const x=i*70+20,y=350+Math.sin(i)*20;ctx.beginPath();ctx.arc(x,y,45+Math.random()*20,0,Math.PI*2);ctx.fill();}
    ctx.fillStyle='rgba(69,77,72,.35)';ctx.fillRect(0,420,s,92);
  });
  const outsideMat = new THREE.MeshBasicMaterial({map:outsideTex});
  mesh(new THREE.PlaneGeometry(win.w-.04,win.h-.04),outsideMat,{p:[win.cx,win.cy,backZ-.24],parent:room,cast:false,receive:false});

  // curtains with actual folds
  function curtainPanel(xCenter,width){
    const geo=new THREE.PlaneGeometry(width,1.75,24,12);
