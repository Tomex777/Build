const SVG = '<svg class="night-mark" viewBox="0 0 108 108" aria-hidden="true"><rect class="logo-bg" width="108" height="108" rx="24" fill="#0B0F11"/><path class="logo-dome" fill="#F4F5F6" d="M24 54C24 37.43 37.43 24 54 24s30 13.43 30 30H24Z"/><path class="logo-bar" fill="#F4F5F6" d="M22 66h64v6H22z"/></svg>';
const LETTERS = '<span class="letter">N</span><span class="letter">I</span><span class="letter">G</span><span class="letter">H</span><span class="letter">T</span>';

export function renderScene(stage, item) {
  stage.innerHTML =
    '<div class="stage-scene scene-' + item.scene + '">' +
      '<div class="portfolio-prop scene-prop"></div><div class="app-prop scene-prop"></div>' +
      '<div class="contact-shadow"></div>' +
      '<div class="brand-lockup"><div class="mark-shell">' + SVG + '</div><div class="wordmark">' + LETTERS + '</div></div>' +
      '<div class="fx-layer">' +
        '<i class="fx eclipse"></i><i class="fx scan"></i><i class="fx rim"></i><i class="fx horizon"></i><i class="fx cursor"></i>' +
        '<i class="fx particle p1"></i><i class="fx particle p2"></i><i class="fx particle p3"></i><i class="fx particle p4"></i><i class="fx particle p5"></i><i class="fx particle p6"></i>' +
        '<i class="fx trail t1"></i><i class="fx trail t2"></i><i class="fx slice slice-a"></i><i class="fx slice slice-b"></i>' +
      '</div>' +
    '</div>';
}

function targets(stage) {
  const one = (s) => stage.querySelector(s);
  return {
    scene: one('.stage-scene'), lock: one('.brand-lockup'), mark: one('.mark-shell'), svg: one('.night-mark'),
    dome: one('.logo-dome'), bar: one('.logo-bar'), word: one('.wordmark'), letters: [...stage.querySelectorAll('.letter')],
    shadow: one('.contact-shadow'), fx: one('.fx-layer'), eclipse: one('.eclipse'), scan: one('.scan'), rim: one('.rim'),
    horizon: one('.horizon'), cursor: one('.cursor'), particles: [...stage.querySelectorAll('.particle')],
    trails: [...stage.querySelectorAll('.trail')], slices: [...stage.querySelectorAll('.slice')],
    prop: one('.scene-prop' + (stage.querySelector('.portfolio-prop')?.offsetParent !== null ? '.portfolio-prop' : '.app-prop')) || one('.portfolio-prop'),
    portfolio: one('.portfolio-prop'), app: one('.app-prop')
  };
}

function runner(stage, speed, loop) {
  const list = [];
  const rate = Math.max(.1, speed || 1);
  const A = (el, keyframes, duration=700, delay=0, easing='cubic-bezier(.2,.75,.2,1)', iterations) => {
    if (!el) return null;
    const anim = el.animate(keyframes, {duration:duration/rate, delay:delay/rate, easing, fill:'both', iterations:iterations ?? (loop ? Infinity : 1)});
    list.push(anim); return anim;
  };
  A.list = list;
  return A;
}

function revealMotions(v,e,A) {
  const k = v % 12;
  if(k===0){A(e.shadow,[{opacity:0,transform:'translate(-50%,-50%) scale(.55)'},{opacity:1,transform:'translate(-50%,-50%) scale(1.25)'},{opacity:.58,transform:'translate(-50%,-50%) scale(1)'}],720);A(e.lock,[{opacity:0,filter:'blur(12px)',transform:'translate(-50%,-44%) scale(.94)'},{opacity:1,filter:'blur(0)',transform:'translate(-50%,-50%) scale(1)'}],760,140);}
  if(k===1){A(e.lock,[{clipPath:'circle(0% at 50% 50%)',opacity:.2},{clipPath:'circle(72% at 50% 50%)',opacity:1}],760);}
  if(k===2){e.slices.forEach(s=>s.style.opacity=1);A(e.slices[0],[{transform:'translateY(0)'},{transform:'translateY(-105%)'}],680);A(e.slices[1],[{transform:'translateY(0)'},{transform:'translateY(105%)'}],680);A(e.lock,[{opacity:.3,transform:'translate(-50%,-50%) scale(.96)'},{opacity:1,transform:'translate(-50%,-50%) scale(1)'}],680);}
  if(k===3){A(e.lock,[{clipPath:'inset(0 50% 50% 50%)'},{clipPath:'inset(0 0 50% 0)'},{clipPath:'inset(0 0 0 0)'}],800);}
  if(k===4){A(e.lock,[{clipPath:'inset(0 49% 0 49%)',filter:'brightness(.55)'},{clipPath:'inset(0 0 0 0)',filter:'brightness(1)'}],650);}
  if(k===5){A(e.lock,[{clipPath:'polygon(0 0,0 0,0 0,0 0)',transform:'translate(-51%,-49%) rotate(-2deg)'},{clipPath:'polygon(0 0,100% 0,100% 100%,0 100%)',transform:'translate(-50%,-50%) rotate(0)'}],780);}
  if(k===6){A(e.bar,[{transform:'scaleX(0)',transformOrigin:'left'},{transform:'scaleX(1)',transformOrigin:'left'}],430);A(e.dome,[{opacity:0,transform:'translateY(8px)'},{opacity:1,transform:'translateY(0)'}],460,280);A(e.word,[{opacity:0,letterSpacing:'.32em'},{opacity:1,letterSpacing:'.12em'}],520,360);}
  if(k===7){A(e.lock,[{clipPath:'polygon(0 0,22% 0,22% 100%,0 100%,0 0)'},{clipPath:'polygon(0 0,22% 0,22% 100%,47% 100%,47% 0,72% 0,72% 100%,100% 100%,100% 0,0 0)'}],320);A(e.lock,[{filter:'brightness(.7)'},{filter:'brightness(1)'}],520,260);}
  if(k===8){A(e.bar,[{opacity:0,transform:'translateX(-22px) scaleX(.5)'},{opacity:1,transform:'translateX(0) scaleX(1)'}],380);A(e.dome,[{opacity:0,transform:'translateY(-14px) scale(.86)'},{opacity:1,transform:'translateY(0) scale(1)'}],560,240);A(e.word,[{opacity:0},{opacity:1}],420,420);}
  if(k===9){A(e.word,[{clipPath:'inset(0 100% 0 0)',transform:'translateX(-24px)'},{clipPath:'inset(0 0 0 0)',transform:'translateX(0)'}],720);A(e.mark,[{opacity:.35,transform:'scale(.8)'},{opacity:1,transform:'scale(1)'}],520,180);}
  if(k===10){A(e.lock,[{opacity:.15,filter:'blur(18px) contrast(.7)',transform:'translate(-50%,-50%) scale(1.08)'},{opacity:1,filter:'blur(0) contrast(1)',transform:'translate(-50%,-50%) scale(1)'}],900);}
  if(k===11){e.horizon.style.opacity=1;A(e.horizon,[{transform:'translateX(-115%)',opacity:0},{transform:'translateX(0)',opacity:1},{transform:'translateX(115%)',opacity:0}],700);A(e.word,[{clipPath:'inset(100% 0 0 0)',transform:'translateY(6px)'},{clipPath:'inset(0 0 0 0)',transform:'translateY(0)'}],650,120);A(e.mark,[{opacity:0,transform:'translateY(8px)'},{opacity:1,transform:'translateY(0)'}],500,260);}
}
function physicalMotions(v,e,A){
  const k=v%12;
  if(k===0){A(e.lock,[{transform:'translate(-50%,-92%) scaleY(1)'},{transform:'translate(-50%,-48%) scaleY(.92)'},{transform:'translate(-50%,-52%) scaleY(1.035)'},{transform:'translate(-50%,-50%) scaleY(1)'}],680);A(e.shadow,[{transform:'translate(-50%,-50%) scale(.55)',opacity:.2},{transform:'translate(-50%,-50%) scale(1.18)',opacity:.72},{transform:'translate(-50%,-50%) scale(1)',opacity:.58}],680);}
  if(k===1)A(e.lock,[{transform:'translate(-50%,-10%) scale(.88)'},{transform:'translate(-50%,-57%) scale(1.06)'},{transform:'translate(-50%,-48%) scale(.985)'},{transform:'translate(-50%,-50%) scale(1)'}],760,0,'cubic-bezier(.18,.9,.25,1.15)');
  if(k===2){A(e.mark,[{transform:'translateX(-72px) rotate(-8deg)'},{transform:'translateX(4px) rotate(1deg)'},{transform:'translateX(0) rotate(0)'}],620);A(e.word,[{transform:'translateX(72px)',letterSpacing:'.24em'},{transform:'translateX(-3px)',letterSpacing:'.1em'},{transform:'translateX(0)',letterSpacing:'.12em'}],620);}
  if(k===3)A(e.mark,[{transformOrigin:'50% -35px',transform:'rotate(22deg)'},{transform:'rotate(-10deg)'},{transform:'rotate(4deg)'},{transform:'rotate(-1deg)'},{transform:'rotate(0)'}],950);
  if(k===4){A(e.lock,[{transform:'translate(-50%,-125%)'},{transform:'translate(-50%,-46%)'},{transform:'translate(-50%,-51.5%)'},{transform:'translate(-50%,-50%)'}],620,'0','cubic-bezier(.18,.82,.22,1)');A(e.shadow,[{opacity:.08,transform:'translate(-50%,-50%) scale(.5)'},{opacity:.68,transform:'translate(-50%,-50%) scale(1.28)'},{opacity:.58,transform:'translate(-50%,-50%) scale(1)'}],620);}
  if(k===5)A(e.lock,[{transform:'translate(-50%,-30%)',opacity:.35},{transform:'translate(-50%,-58%)',opacity:1},{transform:'translate(-50%,-50%)'}],860,0,'cubic-bezier(.25,.6,.25,1)');
  if(k===6)A(e.lock,[{transform:'translate(-105%,-50%) rotate(-3deg)'},{transform:'translate(-46%,-50%) rotate(1deg)'},{transform:'translate(-52%,-50%) rotate(-.5deg)'},{transform:'translate(-50%,-50%)'}],700);
  if(k===7)A(e.lock,[{transform:'translate(-50%,-50%) scaleX(.68) scaleY(1.18)'},{transform:'translate(-50%,-50%) scaleX(1.06) scaleY(.96)'},{transform:'translate(-50%,-50%) scale(1)'}],680);
  if(k===8)A(e.lock,[{transformOrigin:'0 0',transform:'translate(-18%,-18%) rotate(-12deg)'},{transform:'translate(-54%,-53%) rotate(3deg)'},{transform:'translate(-50%,-50%) rotate(0)'}],760);
  if(k===9){A(e.mark,[{transform:'translate(70px,-38px) rotate(140deg)'},{transform:'translate(18px,12px) rotate(325deg)'},{transform:'translate(0,0) rotate(360deg)'}],850);A(e.word,[{opacity:.25},{opacity:1}],500,300);}
  if(k===10)A(e.word,[{letterSpacing:'.55em',transform:'scaleX(1.14)'},{letterSpacing:'.08em',transform:'scaleX(.97)'},{letterSpacing:'.12em',transform:'scaleX(1)'}],720);
  if(k===11){A(e.lock,[{transform:'translate(-50%,-50%) rotateX(-80deg) translateY(-16px)'},{transform:'translate(-50%,-48%) rotateX(10deg)'},{transform:'translate(-50%,-50%) rotateX(0deg)'}],760);A(e.shadow,[{transform:'translate(-50%,-50%) scale(.45)',opacity:.15},{transform:'translate(-50%,-50%) scale(1.2)',opacity:.7},{transform:'translate(-50%,-50%) scale(1)',opacity:.58}],760);}
}
function depthMotions(v,e,A){
  const k=v%12;
  if(k===0)A(e.lock,[{transform:'translate(-50%,-50%) rotateX(18deg) rotateY(-26deg) translateZ(-60px)'},{transform:'translate(-50%,-50%) rotateX(0) rotateY(0) translateZ(0)'}],820);
  if(k===1)A(e.lock,[{transform:'translate(-50%,-50%) translateZ(-260px) scale(.72)',filter:'blur(4px)',opacity:.2},{transform:'translate(-50%,-50%) translateZ(24px) scale(1.04)',filter:'blur(0)',opacity:1},{transform:'translate(-50%,-50%) translateZ(0) scale(1)'}],880);
  if(k===2){A(e.shadow,[{transform:'translate(-85%,-10%) scale(.65)'},{transform:'translate(-50%,-50%) scale(1)'}],800);A(e.mark,[{transform:'translateX(-36px) translateZ(-70px)'},{transform:'translateX(0) translateZ(0)'}],800);A(e.word,[{transform:'translateX(50px) translateZ(60px)'},{transform:'translateX(0) translateZ(0)'}],800);}
  if(k===3)A(e.portfolio||e.lock,[{transform:'translate(-50%,-50%) rotateY(-84deg)'},{transform:'translate(-50%,-50%) rotateY(8deg)'},{transform:'translate(-50%,-50%) rotateY(0)'}],820);
  if(k===4){e.trails.forEach((t,i)=>{t.style.opacity=.45;A(t,[{transform:'translate(-50%,-50%) translate('+(36+i*18)+'px,'+(24+i*10)+'px)'},{transform:'translate(-50%,-50%) translate(0,0)',opacity:0}],760,i*40)});A(e.lock,[{transform:'translate(-54%,-54%)'},{transform:'translate(-50%,-50%)'}],760);}
  if(k===5){e.trails.forEach((t,i)=>{t.style.opacity=.35;A(t,[{transform:'translate(-50%,-50%) translateZ('+(-120-i*60)+'px) scale('+(1.3+i*.18)+')'},{transform:'translate(-50%,-50%) translateZ(0) scale(1)',opacity:0}],820,i*90)});A(e.lock,[{transform:'translate(-50%,-50%) translateZ(-90px)'},{transform:'translate(-50%,-50%) translateZ(0)'}],820);}
  if(k===6)A(e.mark,[{transform:'translateZ(12px) scale(1.05)',filter:'drop-shadow(8px 10px 4px #000)'},{transform:'translateZ(-10px) scale(.97)',filter:'drop-shadow(-2px -2px 1px rgba(255,255,255,.12))'},{transform:'translateZ(0) scale(1)',filter:'drop-shadow(0 7px 9px rgba(0,0,0,.28))'}],820);
  if(k===7){A(e.mark,[{transform:'translateY(18px) translateZ(-20px)'},{transform:'translateY(-8px) translateZ(30px)'},{transform:'translateY(0) translateZ(0)'}],760);A(e.shadow,[{filter:'blur(3px)',transform:'translate(-50%,-50%) scale(.72)'},{filter:'blur(12px)',transform:'translate(-50%,-50%) scale(1.18)'},{filter:'blur(7px)',transform:'translate(-50%,-50%) scale(1)'}],760);}
  if(k===8)A(e.lock,[{clipPath:'polygon(0 0,100% 0,100% 18%,0 18%)',transform:'translate(-50%,-50%) translateZ(-80px)'},{clipPath:'polygon(0 0,100% 0,100% 56%,0 56%)',transform:'translate(-50%,-50%) translateZ(20px)'},{clipPath:'inset(0)',transform:'translate(-50%,-50%) translateZ(0)'}],900);
  if(k===9){A(e.word,[{transform:'perspective(400px) rotateY(-52deg) skewY(8deg)',transformOrigin:'left'},{transform:'perspective(400px) rotateY(0) skewY(0)'}],760);A(e.mark,[{transform:'translateZ(18px)'},{transform:'translateZ(0)'}],760);}
  if(k===10){e.rim.style.opacity=.25;A(e.rim,[{transform:'translate(-50%,-50%) scale(2.8)',opacity:0},{transform:'translate(-50%,-50%) scale(1.6)',opacity:.18},{transform:'translate(-50%,-50%) scale(.85)',opacity:0}],900);A(e.lock,[{transform:'translate(-50%,-50%) scale(.65)'},{transform:'translate(-50%,-50%) scale(1)'}],900);}
  if(k===11){A(e.lock,[{clipPath:'polygon(0 0,50% 20%,50% 80%,0 100%,100% 100%,50% 80%,50% 20%,100% 0)',transform:'translate(-50%,-50%) rotateY(42deg)'},{clipPath:'polygon(0 0,50% 0,50% 100%,0 100%,100% 100%,50% 100%,50% 0,100% 0)',transform:'translate(-50%,-50%) rotateY(0)'}],860);}
}
function typographyMotions(v,e,A){
  const k=v%12, L=e.letters;
  if(k===0)L.forEach((l,i)=>A(l,[{opacity:0,transform:'translateY(18px)'},{opacity:1,transform:'translateY(-2px)'},{transform:'translateY(0)'}],430,i*70));
  if(k===1)A(e.word,[{letterSpacing:'-.08em',opacity:.55},{letterSpacing:'.2em',opacity:1},{letterSpacing:'.12em'}],720);
  if(k===2)L.forEach((l,i)=>A(l,[{transform:'scaleX(.35)',opacity:.4},{transform:'scaleX(1.08)',opacity:1},{transform:'scaleX(1)'}],560,i*35));
  if(k===3)L.forEach((l,i)=>A(l,[{clipPath:'inset(100% 0 0)',transform:'translateY(16px)'},{clipPath:'inset(0)',transform:'translateY(0)'}],520,i*55));
  if(k===4){L.slice(0,3).forEach(l=>A(l,[{transform:'translateX(-42px)',opacity:0},{transform:'translateX(0)',opacity:1}],620));L.slice(3).forEach(l=>A(l,[{transform:'translateX(42px)',opacity:0},{transform:'translateX(0)',opacity:1}],620));}
  if(k===5)L.forEach((l,i)=>A(l,[{clipPath:'inset(100% 0 0)'},{clipPath:'inset(0)'}],540,i*45));
  if(k===6)L.forEach((l,i)=>A(l,[{transform:'rotateY(-90deg)',opacity:.1},{transform:'rotateY(10deg)',opacity:1},{transform:'rotateY(0)'}],560,i*70));
  if(k===7)L.forEach((l,i)=>A(l,[{transform:'translateY(0)'},{transform:'translateY(-12px)'},{transform:'translateY(0)'}],520,i*70));
  if(k===8){L.forEach((l,i)=>A(l,[{opacity:0},{opacity:0},{opacity:1}],160,i*115,'steps(1,end)'));e.cursor.style.opacity=1;A(e.cursor,[{opacity:1},{opacity:0},{opacity:1}],240,0,'steps(1,end)',3);}
  if(k===9)L.forEach((l,i)=>A(l,[{opacity:0,transform:'translateY('+(i%2?-20:20)+'px)'},{opacity:1,transform:'translateY(0)'}],520,i*55));
  if(k===10)A(e.word,[{letterSpacing:'.52em'},{letterSpacing:'.24em'},{letterSpacing:'.09em'},{letterSpacing:'.12em'}],760);
  if(k===11)A(e.word,[{transform:'scale(1)',textShadow:'0 8px 15px rgba(0,0,0,.2)'},{transform:'scale(1.06)',textShadow:'0 12px 20px rgba(0,0,0,.55)'},{transform:'scale(1)',textShadow:'0 8px 15px rgba(0,0,0,.32)'}],620);
}
function logoMotions(v,e,A){
  const k=v%12;
  if(k===0){A(e.dome,[{opacity:0,transform:'translateY(-14px)'},{opacity:1,transform:'translateY(0)'}],520);A(e.bar,[{opacity:0,transform:'translateX(22px) scaleX(.35)'},{opacity:1,transform:'translateX(0) scaleX(1)'}],520,120);}
  if(k===1){A(e.dome,[{transform:'translateY(-30px)',opacity:0},{transform:'translateY(2px)',opacity:1},{transform:'translateY(0)'}],580);A(e.bar,[{transform:'translateX(-34px)',opacity:0},{transform:'translateX(0)',opacity:1}],420,220);}
  if(k===2)A(e.svg,[{clipPath:'inset(0 50% 0 0)',transform:'translateX(-16px)'},{clipPath:'inset(0 0 0 50%)',transform:'translateX(16px)'},{clipPath:'inset(0)',transform:'translateX(0)'}],780);
  if(k===3)A(e.mark,[{transform:'scaleX(.05) rotateY(82deg)'},{transform:'scaleX(1.06) rotateY(-8deg)'},{transform:'scaleX(1) rotateY(0)'}],720);
  if(k===4)A(e.mark,[{transform:'scale(.86)'},{transform:'scale(1.08)'},{transform:'scale(.99)'},{transform:'scale(1)'}],620);
  if(k===5){A(e.shadow,[{opacity:.9,filter:'blur(2px)',transform:'translate(-50%,-50%) scale(.75)'},{opacity:.58,filter:'blur(7px)',transform:'translate(-50%,-50%) scale(1)'}],740);A(e.mark,[{opacity:0,transform:'translateY(16px)'},{opacity:1,transform:'translateY(0)'}],740,120);}
  if(k===6){e.rim.style.opacity=1;A(e.rim,[{clipPath:'inset(0 100% 0 0)',opacity:1},{clipPath:'inset(0 0 0 0)',opacity:1},{opacity:0}],720);A(e.mark,[{opacity:.15},{opacity:1}],500,250);}
  if(k===7)A(e.mark,[{transform:'rotateY(180deg)',opacity:.15},{transform:'rotateY(0)',opacity:1}],720);
  if(k===8){A(e.bar,[{transform:'translateX(-46px)'},{transform:'translateX(0)'}],480);A(e.dome,[{transform:'translateX(-18px)',opacity:.25},{transform:'translateX(0)',opacity:1}],520,180);}
  if(k===9)A(e.mark,[{transform:'scaleY(.35)'},{transform:'scaleY(1.12)'},{transform:'scaleY(.98)'},{transform:'scaleY(1)'}],650);
  if(k===10){const qs=[[-18,-18],[18,-18],[-18,18],[18,18]];e.trails.forEach((t,i)=>{t.style.opacity=.42;t.style.width='32px';t.style.height='32px';A(t,[{transform:'translate(-50%,-50%) translate('+qs[i%4][0]+'px,'+qs[i%4][1]+'px)',opacity:.5},{transform:'translate(-50%,-50%) translate(0,0)',opacity:0}],620,i*55)});A(e.mark,[{opacity:.2},{opacity:1}],620,160);}
  if(k===11)A(e.mark,[{opacity:0},{opacity:1},{opacity:0},{opacity:1,transform:'translateY(2px)'},{opacity:1,transform:'translateY(0)'}],620,0,'linear');
}
function shadowMotions(v,e,A){
  const k=v%12;
  if(k===0){A(e.shadow,[{opacity:0,transform:'translate(-50%,-50%) scale(.2)'},{opacity:.75,transform:'translate(-50%,-50%) scale(1.2)'},{opacity:.58,transform:'translate(-50%,-50%) scale(1)'}],600);A(e.lock,[{opacity:0},{opacity:1}],430,260);}
  if(k===1){A(e.lock,[{transform:'translate(-50%,-44%)'},{transform:'translate(-50%,-56%)'},{transform:'translate(-50%,-50%)'}],740);A(e.shadow,[{filter:'blur(3px)',transform:'translate(-50%,-50%) scale(.78)'},{filter:'blur(13px)',transform:'translate(-50%,-50%) scale(1.2)'},{filter:'blur(7px)',transform:'translate(-50%,-50%) scale(1)'}],740);}
  if(k===2)A(e.shadow,[{transform:'translate(-95%,-20%) skewX(-28deg)'},{transform:'translate(-20%,-75%) skewX(20deg)'},{transform:'translate(-50%,-50%) skewX(0)'}],850);
  if(k===3){e.trails[0].style.opacity=.6;e.trails[0].style.background='rgba(0,0,0,.5)';e.trails[0].style.border='0';A(e.trails[0],[{width:'180px',transform:'translate(-15%,-30%) skewX(-28deg)',opacity:.65},{width:'70px',transform:'translate(-50%,-50%) skewX(0)',opacity:0}],780);}
  if(k===4){A(e.lock,[{transform:'translate(-50%,-70%)'},{transform:'translate(-50%,-47%)'},{transform:'translate(-50%,-51%)'},{transform:'translate(-50%,-50%)'}],620);A(e.shadow,[{transform:'translate(-50%,-50%) scale(.65)'},{transform:'translate(-50%,-50%) scale(1.28)'},{transform:'translate(-50%,-50%) scale(.92)'},{transform:'translate(-50%,-50%) scale(1)'}],620);}
  if(k===5)A(e.shadow,[{transform:'translate(-50%,-50%) scaleX(.35)'},{transform:'translate(-50%,-50%) scaleX(1.65)'},{transform:'translate(-50%,-50%) scaleX(1)'}],760);
  if(k===6){A(e.lock,[{filter:'brightness(.05)',opacity:.6},{filter:'brightness(.35)',opacity:.8},{filter:'brightness(1)',opacity:1}],820);A(e.shadow,[{opacity:.82},{opacity:.58}],820);}
  if(k===7){e.trails.forEach((t,i)=>{t.style.opacity=.4;t.style.background='rgba(0,0,0,.58)';t.style.border='0';A(t,[{transform:'translate(-50%,-50%) translateX('+(i?55:-55)+'px)'},{transform:'translate(-50%,-50%) translateX(0)',opacity:0}],720)});}
  if(k===8)A(e.shadow,[{transform:'translate(-50%,-50%) rotate(0deg) translateX(28px)'},{transform:'translate(-50%,-50%) rotate(180deg) translateX(28px)'},{transform:'translate(-50%,-50%) rotate(360deg) translateX(0)'}],900);
  if(k===9){A(e.shadow,[{opacity:0,filter:'blur(18px)',transform:'translate(-50%,-50%) scale(1.8)'},{opacity:.65,filter:'blur(12px)',transform:'translate(-50%,-50%) scale(1.2)'},{opacity:.58,filter:'blur(7px)',transform:'translate(-50%,-50%) scale(1)'}],760);A(e.lock,[{transform:'translate(-50%,-43%)',opacity:.2},{transform:'translate(-50%,-50%)',opacity:1}],760,100);}
  if(k===10){e.rim.style.opacity=.7;A(e.rim,[{clipPath:'inset(0 100% 80% 0)'},{clipPath:'inset(0 0 0 0)'},{opacity:0}],760);A(e.mark,[{filter:'drop-shadow(-8px 0 7px rgba(0,0,0,.75))'},{filter:'drop-shadow(8px 0 7px rgba(0,0,0,.75))'},{filter:'drop-shadow(0 7px 9px rgba(0,0,0,.28))'}],760);}
  if(k===11){A(e.shadow,[{transform:'translate(-20%,-20%) scale(.8)'},{transform:'translate(-80%,-80%) scale(1.1)'},{transform:'translate(-50%,-50%) scale(1)'}],760);A(e.lock,[{transform:'translate(-56%,-54%)'},{transform:'translate(-47%,-48%)'},{transform:'translate(-50%,-50%)'}],760);}
}
function darkMotions(v,e,A){
  const k=v%12;
  if(k===0)A(e.lock,[{opacity:.02,filter:'brightness(.1) contrast(.5)'},{opacity:.35,filter:'brightness(.45) contrast(.8)'},{opacity:1,filter:'brightness(1) contrast(1)'}],1000);
  if(k===1){e.eclipse.style.opacity=1;A(e.eclipse,[{transform:'translate(-140%,-50%)'},{transform:'translate(-50%,-50%)'},{transform:'translate(45%,-50%)'}],900);A(e.lock,[{opacity:.35},{opacity:1}],900);}
  if(k===2){e.rim.style.opacity=1;A(e.rim,[{transform:'translate(-50%,-50%) rotate(-90deg) scale(.7)',clipPath:'inset(0 70% 70% 0)'},{transform:'translate(-50%,-50%) rotate(270deg) scale(1)',clipPath:'inset(0)'}],880);A(e.lock,[{opacity:.2},{opacity:1}],620,260);}
  if(k===3)A(e.scene,[{clipPath:'circle(10% at 50% 50%)',filter:'brightness(.45)'},{clipPath:'circle(75% at 50% 50%)',filter:'brightness(1)'}],850);
  if(k===4){e.particles.forEach((p,i)=>{p.style.opacity=1;A(p,[{transform:'translate('+(i%2?-40:50)+'px,'+(i%3?-25:38)+'px)',opacity:0},{transform:'translate(0,0)',opacity:.75},{transform:'translate('+(45-i*8)+'px,'+(25-i*5)+'px)',opacity:0}],820,i*45)});A(e.lock,[{opacity:.1},{opacity:1}],700,240);}
  if(k===5)A(e.scene,[{filter:'brightness(1)'},{filter:'brightness(0)'},{filter:'brightness(0)'},{filter:'brightness(1)'}],430,0,'steps(1,end)');
  if(k===6){e.horizon.style.opacity=1;A(e.horizon,[{opacity:0,boxShadow:'0 0 0 rgba(255,255,255,0)'},{opacity:.85,boxShadow:'0 0 40px rgba(255,255,255,.28)'},{opacity:0,boxShadow:'0 0 8px rgba(255,255,255,0)'}],900);A(e.lock,[{filter:'brightness(.22)'},{filter:'brightness(1)'}],900);}
  if(k===7){e.rim.style.opacity=.8;e.rim.style.borderRadius='50%';A(e.rim,[{transform:'translate(-135%,-50%) scale(.9)',opacity:0},{transform:'translate(-50%,-50%) scale(1.1)',opacity:.9},{transform:'translate(45%,-50%) scale(.9)',opacity:0}],880);A(e.mark,[{filter:'brightness(.35)'},{filter:'brightness(1)'},{filter:'brightness(.8)'},{filter:'brightness(1)'}],880);}
  if(k===8)A(e.lock,[{opacity:.04,filter:'blur(2px)'},{opacity:.28,filter:'blur(1px)'},{opacity:1,filter:'blur(0)'}],950);
  if(k===9){A(e.scene,[{filter:'brightness(.72)'},{filter:'brightness(1.15)'},{filter:'brightness(.9)'},{filter:'brightness(1)'}],760);A(e.lock,[{transform:'translate(-50%,-50%) scale(.98)'},{transform:'translate(-50%,-50%) scale(1.025)'},{transform:'translate(-50%,-50%) scale(1)'}],760);}
  if(k===10)A(e.lock,[{opacity:.18,filter:'blur(6px) contrast(.7)'},{opacity:.65,filter:'blur(2px) contrast(.9)'},{opacity:1,filter:'blur(0) contrast(1)'}],780);
  if(k===11){e.rim.style.opacity=.35;A(e.rim,[{transform:'translate(-50%,-50%) scale(2.4)',opacity:.35},{transform:'translate(-50%,-50%) scale(.92)',opacity:.6},{transform:'translate(-50%,-50%) scale(.7)',opacity:0}],760);A(e.lock,[{opacity:.45},{opacity:1}],650);}
}
function effectMotions(v,e,A){
  const k=v%12;
  if(k===0)A(e.lock,[{filter:'blur(20px)',opacity:.2,transform:'translate(-50%,-50%) scale(1.12)'},{filter:'blur(0)',opacity:1,transform:'translate(-50%,-50%) scale(1)'}],850);
  if(k===1){e.trails.forEach((t,i)=>{t.style.opacity=.35;A(t,[{transform:'translate(-50%,-50%) translateX('+(-58-i*28)+'px) scale('+(1-i*.08)+')',opacity:.38},{transform:'translate(-50%,-50%) translateX(0)',opacity:0}],760,i*70)});A(e.lock,[{transform:'translate(-75%,-50%)'},{transform:'translate(-50%,-50%)'}],760);}
  if(k===2){A(e.lock,[{textShadow:'-5px 0 #7aa9c4,5px 0 #c98192',filter:'drop-shadow(-4px 0 #7aa9c4) drop-shadow(4px 0 #c98192)'},{textShadow:'-2px 0 #7aa9c4,2px 0 #c98192',filter:'drop-shadow(-1px 0 #7aa9c4) drop-shadow(1px 0 #c98192)'},{textShadow:'0 0 transparent',filter:'none'}],650);}
  if(k===3){e.trails.forEach((t,i)=>{t.style.opacity=.5;t.style.width=(110-i*15)+'px';A(t,[{transform:'translate(-80%,-50%) scaleX(2)',opacity:.45},{transform:'translate(-50%,-50%) scaleX(.5)',opacity:0}],680,i*45)});A(e.lock,[{transform:'translate(-70%,-50%)'},{transform:'translate(-50%,-50%)'}],680);}
  if(k===4){e.particles.forEach((p,i)=>{p.style.opacity=.55;A(p,[{transform:'scale(.5)',opacity:.1},{transform:'scale(1.6)',opacity:.8},{opacity:0}],420,i*55)});A(e.lock,[{filter:'contrast(.5) brightness(.4)',opacity:.15},{filter:'contrast(1.3) brightness(1.1)',opacity:.75},{filter:'none',opacity:1}],780);}
  if(k===5){e.scan.style.opacity=1;A(e.scan,[{top:'4%',opacity:0},{top:'50%',opacity:1},{top:'96%',opacity:0}],760);A(e.lock,[{clipPath:'inset(100% 0 0)'},{clipPath:'inset(0)'}],760);}
  if(k===6)A(e.lock,[{clipPath:'polygon(50% 48%,56% 40%,65% 47%,63% 60%,54% 67%,43% 62%,38% 51%,42% 42%)'},{clipPath:'polygon(7% 20%,75% 3%,98% 39%,90% 83%,45% 100%,5% 79%,0 43%,22% 8%)'},{clipPath:'inset(0)'}],850);
  if(k===7){e.particles.forEach((p,i)=>{p.style.opacity=1;A(p,[{transform:'translate('+(i%2?-90:90)+'px,'+(i%3?-55:65)+'px) scale(.3)',opacity:0},{transform:'translate(0,0) scale(1.5)',opacity:.85},{transform:'translate('+(45-i*9)+'px,'+(18-i*4)+'px) scale(.5)',opacity:0}],880,i*55)});A(e.lock,[{opacity:0},{opacity:1}],500,410);}
  if(k===8){e.trails.forEach((t,i)=>{t.style.opacity=.25;A(t,[{transform:'translate(-50%,-50%) scale('+(1.7+i*.35)+')',opacity:.3},{transform:'translate(-50%,-50%) scale(1)',opacity:0}],760,i*80)});A(e.lock,[{transform:'translate(-50%,-50%) scale(.82)'},{transform:'translate(-50%,-50%) scale(1)'}],760);}
  if(k===9)A(e.lock,[{filter:'blur(2px)',transform:'translate(-62%,-50%) scaleX(1.8)',opacity:.35},{filter:'blur(0)',transform:'translate(-50%,-50%) scaleX(1)',opacity:1}],700);
  if(k===10)A(e.lock,[{filter:'contrast(2)',clipPath:'polygon(0 0,22% 0,22% 20%,45% 20%,45% 0,72% 0,72% 30%,100% 30%,100% 100%,60% 100%,60% 74%,31% 74%,31% 100%,0 100%)'},{filter:'contrast(1)',clipPath:'inset(0)'}],760);
  if(k===11){e.rim.style.opacity=.7;e.rim.style.borderRadius='50%';A(e.rim,[{transform:'translate(-50%,-50%) scale(.2)',opacity:.8},{transform:'translate(-50%,-50%) scale(2.6)',opacity:0}],780);A(e.lock,[{filter:'blur(4px)'},{filter:'blur(0)'}],650,120);}
}
function microMotions(v,e,A){
  const k=v%10;
  if(k===0){A(e.lock,[{transform:'translate(-50%,-50%) translateY(0)'},{transform:'translate(-50%,-50%) translateY(-7px)'}],260);A(e.shadow,[{filter:'blur(7px)',transform:'translate(-50%,-50%) scale(1)'},{filter:'blur(11px)',transform:'translate(-50%,-50%) scale(1.12)'}],260);}
  if(k===1){A(e.lock,[{transform:'translate(-50%,-50%) translateY(0) scale(1)'},{transform:'translate(-50%,-50%) translateY(3px) scale(.98)'},{transform:'translate(-50%,-50%) translateY(0) scale(1)'}],320);A(e.shadow,[{transform:'translate(-50%,-50%) scale(1)'},{transform:'translate(-50%,-50%) scale(.82)'},{transform:'translate(-50%,-50%) scale(1)'}],320);}
  if(k===2){e.rim.style.opacity=1;A(e.rim,[{transform:'translate(-50%,-50%) scale(.82)',opacity:0},{transform:'translate(-50%,-50%) scale(1.08)',opacity:.8},{transform:'translate(-50%,-50%) scale(1)',opacity:.35}],420);A(e.lock,[{transform:'translate(-50%,-50%) scale(.98)'},{transform:'translate(-50%,-50%) scale(1.02)'},{transform:'translate(-50%,-50%) scale(1)'}],420);}
  if(k===3){e.particles[0].style.opacity=1;A(e.particles[0],[{offsetDistance:'0%',opacity:1},{offsetDistance:'100%',opacity:1}],900,0,'linear');e.particles[0].style.offsetPath='circle(42px at 50% 50%)';}
  if(k===4){A(e.portfolio||e.app,[{transform:'translate(-50%,-50%) scale(.35)',borderRadius:'28px',opacity:.3},{transform:'translate(-50%,-50%) scale(1)',borderRadius:'18px',opacity:1}],520);A(e.lock,[{transform:'translate(-50%,-50%) scale(.8)',opacity:.2},{transform:'translate(-50%,-50%) scale(1)',opacity:1}],520,80);}
  if(k===5){A(e.scene,[{transform:'scale(1)',opacity:1},{transform:'scale(.94)',opacity:.7},{transform:'scale(.72)',opacity:0}],480);}
  if(k===6){e.horizon.style.opacity=1;e.horizon.style.top='72%';A(e.horizon,[{left:'16%',right:'84%'},{left:'16%',right:'54%'},{left:'38%',right:'38%'}],380);}
  if(k===7){A(e.rim,[{opacity:0,transform:'translate(-50%,-50%) scale(.65)'},{opacity:.8,transform:'translate(-50%,-50%) scale(.82)'},{opacity:0,transform:'translate(-50%,-50%) scale(.78)'}],320);A(e.mark,[{transform:'scale(.96)'},{transform:'scale(1.03)'},{transform:'scale(1)'}],320);}
  if(k===8){e.rim.style.opacity=1;A(e.rim,[{clipPath:'polygon(0 0,0 0,0 0,0 0)',opacity:.8},{clipPath:'polygon(0 0,100% 0,100% 0,0 0)',opacity:.8},{clipPath:'inset(0)',opacity:.8},{opacity:0}],900);}
  if(k===9){e.rim.style.opacity=1;A(e.rim,[{transform:'translate(-50%,-50%) scale(.8)',opacity:0},{transform:'translate(-50%,-50%) scale(1.12)',opacity:.6},{transform:'translate(-50%,-50%) scale(1)',opacity:.28}],420);}
}
function portfolioMotions(v,e,A){
  const k=v%10,p=e.portfolio;
  if(k===0){A(p,[{transform:'translate(-50%,-44%) scale(.9) translateZ(-80px)',opacity:.2},{transform:'translate(-50%,-50%) scale(1) translateZ(0)',opacity:1}],780);A(e.lock,[{transform:'translate(-50%,-30%) translateZ(80px)',opacity:0},{transform:'translate(-50%,-50%) translateZ(0)',opacity:1}],720,150);}
  if(k===1){A(p,[{transform:'translate(-50%,-50%) rotateX(0) rotateY(0)'},{transform:'translate(-50%,-53%) rotateX(4deg) rotateY(-4deg) translateZ(18px)'}],360);A(e.shadow,[{opacity:.45,transform:'translate(-50%,-50%) scale(.9)'},{opacity:.62,transform:'translate(-50%,-50%) scale(1.1)'}],360);}
  if(k===2){A(p,[{transform:'translate(-50%,-50%) scale(.62)',borderRadius:'24px'},{transform:'translate(-50%,-50%) scale(1.05)',borderRadius:'16px'},{transform:'translate(-50%,-50%) scale(1)',borderRadius:'17px'}],700);A(e.lock,[{opacity:.4,transform:'translate(-50%,-50%) scale(.82)'},{opacity:1,transform:'translate(-50%,-50%) scale(1)'}],700,120);}
  if(k===3){e.slices.forEach(s=>s.style.opacity=1);A(e.slices[0],[{transform:'translateY(0)'},{transform:'translateY(-102%)'}],620);A(e.slices[1],[{transform:'translateY(0)'},{transform:'translateY(102%)'}],620);A(p,[{filter:'brightness(.5)'},{filter:'brightness(1)'}],620);}
  if(k===4){A(p,[{transform:'translate(-50%,-15%) scale(.82)',opacity:0},{transform:'translate(-50%,-54%) scale(1.03)',opacity:1},{transform:'translate(-50%,-50%) scale(1)'}],760);A(e.shadow,[{transform:'translate(-50%,-50%) scale(.4)',opacity:.1},{transform:'translate(-50%,-50%) scale(1.15)',opacity:.55},{transform:'translate(-50%,-50%) scale(1)',opacity:.45}],760);}
  if(k===5){A(e.word,[{letterSpacing:'.48em',opacity:.2},{letterSpacing:'.12em',opacity:1}],650);A(p,[{transform:'translate(-50%,-44%) rotateX(8deg)'},{transform:'translate(-50%,-50%) rotateX(0)'}],650);}
  if(k===6){e.trails.forEach((t,i)=>{t.style.opacity=.34;t.style.width='68%';t.style.height='42%';A(t,[{transform:'translate(-50%,-50%) translateY('+(60+i*35)+'px)',opacity:0},{transform:'translate(-50%,-50%) translateY('+(i*8)+'px)',opacity:.3},{opacity:0}],650,i*100)});A(p,[{transform:'translate(-50%,-30%)',opacity:.2},{transform:'translate(-50%,-50%)',opacity:1}],720);}
  if(k===7){A(p,[{transform:'translate(-70%,-50%) scale(.85)',opacity:0},{transform:'translate(-50%,-50%) scale(1)',opacity:1}],620);A(e.lock,[{transform:'translate(-25%,-50%) scale(1.1)',opacity:0},{transform:'translate(-50%,-50%) scale(1)',opacity:1}],620,80);}
  if(k===8)A(p,[{transformOrigin:'50% 100%',transform:'translate(-50%,-50%) rotateX(0) scale(1)',opacity:1},{transform:'translate(-50%,-50%) rotateX(-65deg) scale(.75)',opacity:.35}],620);
  if(k===9){A(p,[{filter:'blur(8px)',transform:'translate(-50%,-50%) scale(.78)',opacity:.45},{filter:'blur(0)',transform:'translate(-50%,-50%) scale(1)',opacity:1}],680);A(e.lock,[{transform:'translate(-50%,-50%) scale(.82)'},{transform:'translate(-50%,-50%) scale(1)'}],680);}
}
function appMotions(v,e,A){
  const k=v%12,p=e.app;
  if(k===0){A(e.mark,[{transform:'scale(.55) translateY(12px)',opacity:0},{transform:'scale(1.06) translateY(-2px)',opacity:1},{transform:'scale(1) translateY(0)'}],560);A(p,[{filter:'brightness(.35)'},{filter:'brightness(1)'}],560);}
  if(k===1){A(e.lock,[{transform:'translate(-50%,-50%) translateZ(-90px) scale(.82)',opacity:.2},{transform:'translate(-50%,-50%) translateZ(18px) scale(1.035)',opacity:1},{transform:'translate(-50%,-50%) translateZ(0) scale(1)'}],680);A(p,[{opacity:.25},{opacity:1}],520,180);}
  if(k===2){A(p,[{transform:'translate(-50%,-32%)',opacity:.2},{transform:'translate(-50%,-50%)',opacity:1}],520);A(e.lock,[{transform:'translate(-50%,-60%) scale(.92)'},{transform:'translate(-50%,-50%) scale(1)'}],520,100);}
  if(k===3){A(p,[{transform:'translate(-42%,-50%)',opacity:.7},{transform:'translate(-50%,-50%)',opacity:1}],420);A(e.lock,[{transform:'translate(-50%,-46%) scale(.96)'},{transform:'translate(-50%,-50%) scale(1)'}],420);}
  if(k===4){e.horizon.style.opacity=1;e.horizon.style.top='86%';A(e.horizon,[{transform:'translateX(-55px) scaleX(.6)'},{transform:'translateX(55px) scaleX(1.1)'},{transform:'translateX(0) scaleX(1)'}],420);A(e.lock,[{transform:'translate(-55%,-50%)'},{transform:'translate(-50%,-50%)'}],420);}
  if(k===5){A(p,[{clipPath:'inset(58% 0 0 0)',transform:'translate(-50%,-34%)'},{clipPath:'inset(0)',transform:'translate(-50%,-50%)'}],520);A(e.shadow,[{opacity:.2},{opacity:.62}],520);}
  if(k===6){e.particles[0].style.opacity=1;A(e.particles[0],[{transform:'translate(-80px,35px) scale(1)'},{transform:'translate(55px,-25px) scale(.45)',opacity:.2}],420);A(p,[{filter:'brightness(.6)'},{filter:'brightness(1)'}],420,180);A(e.lock,[{transform:'translate(-50%,-50%) scale(.98)'},{transform:'translate(-50%,-50%) scale(1.02)'},{transform:'translate(-50%,-50%) scale(1)'}],360,300);}
  if(k===7){e.particles.slice(0,3).forEach((p2,i)=>{p2.style.opacity=.8;p2.style.left=(42+i*8)+'%';p2.style.top='72%';A(p2,[{transform:'translateY(0)',opacity:.25},{transform:'translateY(-8px)',opacity:1},{transform:'translateY(0)',opacity:.25}],540,i*140)});}
  if(k===8){A(p,[{transform:'translate(-50%,-50%) scale(.48)',borderRadius:'26px'},{transform:'translate(-50%,-50%) scale(1)',borderRadius:'18px'}],560);A(e.lock,[{opacity:.35,transform:'translate(-50%,-50%) scale(.8)'},{opacity:1,transform:'translate(-50%,-50%) scale(1)'}],560);}
  if(k===9){A(p,[{transform:'translate(-50%,-50%) scale(1)',opacity:1},{transform:'translate(-50%,-50%) scale(.5)',opacity:.25}],520);A(e.lock,[{transform:'translate(-50%,-50%) scale(1)'},{transform:'translate(-50%,-50%) scale(.82)',opacity:.2}],520);}
  if(k===10){e.rim.style.opacity=1;e.rim.style.borderRadius='50%';A(e.rim,[{transform:'translate(-50%,-50%) scale(.5)',opacity:0},{transform:'translate(-50%,-50%) scale(1.35)',opacity:.55},{transform:'translate(-50%,-50%) scale(1)',opacity:.25}],680);A(p,[{filter:'brightness(.6)'},{filter:'brightness(1)'}],680);}
  if(k===11){e.rim.style.opacity=.45;e.rim.style.borderRadius='50%';A(e.rim,[{transform:'translate(-50%,-50%) scale(1)',opacity:.45},{transform:'translate(-50%,-50%) scale(.2)',opacity:0}],480);A(p,[{transform:'translate(-50%,-50%) scale(1)',opacity:1},{transform:'translate(-50%,-50%) scale(.92)',opacity:.55}],480);}
}

export function runMotion(stage, item, options={}) {
  stopMotion(stage);
  renderScene(stage,item);
  const e=targets(stage), A=runner(stage,options.speed||1,!!options.loop);
  if(item.category==='reveals') revealMotions(item.variant,e,A);
  else if(item.category==='physical') physicalMotions(item.variant,e,A);
  else if(item.category==='depth') depthMotions(item.variant,e,A);
  else if(item.category==='typography') typographyMotions(item.variant,e,A);
  else if(item.category==='logo') logoMotions(item.variant,e,A);
  else if(item.category==='shadow') shadowMotions(item.variant,e,A);
  else if(item.category==='dark') darkMotions(item.variant,e,A);
  else if(item.category==='effects') effectMotions(item.variant,e,A);
  else if(item.category==='micro') microMotions(item.variant,e,A);
  else if(item.category==='portfolio') portfolioMotions(item.variant,e,A);
  else if(item.category==='app') appMotions(item.variant,e,A);
  stage._nightAnimations=A.list;
  return A.list;
}
export function pauseMotion(stage){(stage._nightAnimations||[]).forEach(a=>a.pause());}
export function playMotion(stage){(stage._nightAnimations||[]).forEach(a=>a.play());}
export function stopMotion(stage){(stage._nightAnimations||[]).forEach(a=>a.cancel());stage._nightAnimations=[];}
