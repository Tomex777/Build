/**
 * Preserved PlayJigsaw.net MIT-licensed procedural themes, plus NIGHT Play's
 * original cel-shaded Sakura Dawn and Chromatic abstract artworks.
 * Artwork is local canvas drawing, not remotely sourced.
 */
export function renderThemeArt(board,theme) {
    const ctx = board.sourceCtx;
    const w = board.sourceCanvas.width;
    const h = board.sourceCanvas.height;
    ctx.clearRect(0, 0, w, h);

    if (theme === 'nature') {
      const skyGrad = ctx.createLinearGradient(0, 0, 0, h * 0.6);
      skyGrad.addColorStop(0, '#1e3a8a');
      skyGrad.addColorStop(0.5, '#60a5fa');
      skyGrad.addColorStop(1, '#fed7aa');
      ctx.fillStyle = skyGrad;
      ctx.fillRect(0, 0, w, h * 0.6);

      ctx.fillStyle = '#ffedd5';
      ctx.beginPath();
      ctx.arc(w * 0.75, h * 0.35, 45, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = '#334155';
      ctx.beginPath();
      ctx.moveTo(0, h * 0.6);
      ctx.lineTo(w * 0.2, h * 0.25);
      ctx.lineTo(w * 0.4, h * 0.55);
      ctx.lineTo(w * 0.65, h * 0.18);
      ctx.lineTo(w * 0.85, h * 0.5);
      ctx.lineTo(w, h * 0.3);
      ctx.lineTo(w, h * 0.6);
      ctx.closePath();
      ctx.fill();

      ctx.fillStyle = '#ffffff';
      ctx.beginPath();
      ctx.moveTo(w * 0.2, h * 0.25);
      ctx.lineTo(w * 0.16, h * 0.32);
      ctx.lineTo(w * 0.24, h * 0.32);
      ctx.closePath();
      ctx.fill();

      ctx.beginPath();
      ctx.moveTo(w * 0.65, h * 0.18);
      ctx.lineTo(w * 0.58, h * 0.28);
      ctx.lineTo(w * 0.72, h * 0.28);
      ctx.closePath();
      ctx.fill();

      const waterGrad = ctx.createLinearGradient(0, h * 0.6, 0, h);
      waterGrad.addColorStop(0, '#0284c7');
      waterGrad.addColorStop(1, '#0f172a');
      ctx.fillStyle = waterGrad;
      ctx.fillRect(0, h * 0.6, w, h * 0.4);

      ctx.fillStyle = '#064e3b';
      for (let i = 0; i < 30; i++) {
        const tx = (i * 28) + (i % 3) * 5;
        const ty = h * 0.58 + (i % 4) * 8;
        const th = 40 + (i % 5) * 10;
        ctx.beginPath();
        ctx.moveTo(tx, ty);
        ctx.lineTo(tx - 12, ty + th);
        ctx.lineTo(tx + 12, ty + th);
        ctx.closePath();
        ctx.fill();
      }
    } else if (theme === 'sunset') {
      const grad = ctx.createLinearGradient(0, 0, 0, h);
      grad.addColorStop(0, '#701a75');
      grad.addColorStop(0.3, '#c026d3');
      grad.addColorStop(0.55, '#f97316');
      grad.addColorStop(0.7, '#fde047');
      grad.addColorStop(0.85, '#0284c7');
      grad.addColorStop(1, '#082f49');
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, w, h);

      ctx.fillStyle = '#fff7ed';
      ctx.beginPath();
      ctx.arc(w * 0.5, h * 0.58, 60, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = '#090d16';
      ctx.beginPath();
      ctx.moveTo(w * 0.15, h);
      ctx.quadraticCurveTo(w * 0.25, h * 0.5, w * 0.32, h * 0.3);
      ctx.lineTo(w * 0.35, h * 0.3);
      ctx.quadraticCurveTo(w * 0.27, h * 0.5, w * 0.2, h);
      ctx.closePath();
      ctx.fill();

      const cx = w * 0.33, cy = h * 0.3;
      for (let angle = 0; angle < Math.PI * 2; angle += 0.8) {
        ctx.beginPath();
        ctx.ellipse(cx + Math.cos(angle) * 70, cy + Math.sin(angle) * 35, 75, 16, angle, 0, Math.PI * 2);
        ctx.fill();
      }
    } else if (theme === 'aurora') {
      const night = ctx.createLinearGradient(0, 0, 0, h);
      night.addColorStop(0, '#030712');
      night.addColorStop(1, '#0f172a');
      ctx.fillStyle = night;
      ctx.fillRect(0, 0, w, h);

      ctx.fillStyle = '#ffffff';
      for (let i = 0; i < 120; i++) {
        const sx = (i * 97) % w;
        const sy = (i * 61) % (h * 0.7);
        ctx.beginPath();
        ctx.arc(sx, sy, (i % 3 === 0) ? 1.5 : 0.8, 0, Math.PI * 2);
        ctx.fill();
      }

      const ribbon1 = ctx.createLinearGradient(0, h * 0.2, w, h * 0.4);
      ribbon1.addColorStop(0, 'rgba(16, 185, 129, 0)');
      ribbon1.addColorStop(0.3, 'rgba(52, 211, 153, 0.7)');
      ribbon1.addColorStop(0.6, 'rgba(6, 182, 212, 0.8)');
      ribbon1.addColorStop(1, 'rgba(168, 85, 247, 0.1)');
      ctx.fillStyle = ribbon1;
      ctx.beginPath();
      ctx.moveTo(0, h * 0.4);
      ctx.bezierCurveTo(w * 0.3, h * 0.1, w * 0.7, h * 0.5, w, h * 0.2);
      ctx.lineTo(w, h * 0.35);
      ctx.bezierCurveTo(w * 0.7, h * 0.65, w * 0.3, h * 0.25, 0, h * 0.55);
      ctx.closePath();
      ctx.fill();

      ctx.fillStyle = '#1e293b';
      ctx.beginPath();
      ctx.moveTo(0, h * 0.8);
      ctx.quadraticCurveTo(w * 0.3, h * 0.7, w * 0.6, h * 0.85);
      ctx.quadraticCurveTo(w * 0.85, h * 0.95, w, h * 0.78);
      ctx.lineTo(w, h);
      ctx.lineTo(0, h);
      ctx.closePath();
      ctx.fill();
    } else if (theme === 'castle') {
      const sky = ctx.createLinearGradient(0, 0, 0, h);
      sky.addColorStop(0, '#312e81');
      sky.addColorStop(0.6, '#9333ea');
      sky.addColorStop(1, '#f472b6');
      ctx.fillStyle = sky;
      ctx.fillRect(0, 0, w, h);

      ctx.fillStyle = 'rgba(254, 240, 138, 0.9)';
      ctx.beginPath();
      ctx.arc(w * 0.8, h * 0.25, 70, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = '#1e1b4b';
      ctx.fillRect(w * 0.38, h * 0.45, w * 0.24, h * 0.4);
      ctx.fillRect(w * 0.28, h * 0.35, w * 0.08, h * 0.5);
      ctx.fillRect(w * 0.64, h * 0.35, w * 0.08, h * 0.5);
    } else if (theme === 'cats') {
      const bg = ctx.createLinearGradient(0, 0, w, h);
      bg.addColorStop(0, '#fef08a');
      bg.addColorStop(1, '#fed7aa');
      ctx.fillStyle = bg;
      ctx.fillRect(0, 0, w, h);

      ctx.fillStyle = '#ea580c';
      ctx.beginPath();
      ctx.ellipse(w * 0.5, h * 0.58, 120, 90, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.beginPath();
      ctx.arc(w * 0.5, h * 0.4, 75, 0, Math.PI * 2);
      ctx.fill();
    } else if (theme === 'anime') {
      // Original cel-shaded Sakura Dawn; procedurally rendered, no remote artwork.
      const sky=ctx.createLinearGradient(0,0,0,h);
      sky.addColorStop(0,'#536fce');sky.addColorStop(.48,'#efb5d9');sky.addColorStop(1,'#fce5bb');
      ctx.fillStyle=sky;ctx.fillRect(0,0,w,h);
      ctx.fillStyle='#fff0dc';ctx.beginPath();ctx.arc(w*.75,h*.26,69,0,Math.PI*2);ctx.fill();
      ctx.fillStyle='#7782bc';ctx.beginPath();ctx.moveTo(0,h*.7);ctx.lineTo(w*.19,h*.42);ctx.lineTo(w*.35,h*.69);
      ctx.lineTo(w*.56,h*.38);ctx.lineTo(w*.82,h*.74);ctx.lineTo(w,h*.5);ctx.lineTo(w,h);ctx.lineTo(0,h);ctx.fill();
      ctx.fillStyle='#485b90';ctx.beginPath();ctx.moveTo(0,h*.86);ctx.lineTo(w*.26,h*.63);ctx.lineTo(w*.43,h*.8);
      ctx.lineTo(w*.72,h*.56);ctx.lineTo(w,h*.83);ctx.lineTo(w,h);ctx.lineTo(0,h);ctx.fill();
      const lake=ctx.createLinearGradient(0,h*.78,0,h);lake.addColorStop(0,'#dcaeca');lake.addColorStop(1,'#586fa8');
      ctx.fillStyle=lake;ctx.fillRect(0,h*.81,w,h*.19);
      ctx.strokeStyle='#3b3759';ctx.lineWidth=19;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(0,0);ctx.bezierCurveTo(90,88,80,144,245,190);ctx.stroke();
      ctx.lineWidth=8;ctx.beginPath();ctx.moveTo(117,121);ctx.quadraticCurveTo(210,90,270,73);ctx.moveTo(150,159);ctx.quadraticCurveTo(260,195,364,167);ctx.stroke();
      for(let i=0;i<115;i++){const px=((i*79)%400)+(i%5)*3,py=((i*47)%225);
       ctx.fillStyle=['#f9d7ea','#ff8dbb','#ffd7ef','#faafd1'][i%4];ctx.beginPath();ctx.ellipse(px,py,6+(i%3)*2,4+(i%2)*2,(i%7)*.45,0,Math.PI*2);ctx.fill();
      }
    } else if (theme === 'abstract') {
      const bg=ctx.createLinearGradient(0,0,w,h);bg.addColorStop(0,'#19385c');bg.addColorStop(.55,'#63448c');bg.addColorStop(1,'#ec7b84');
      ctx.fillStyle=bg;ctx.fillRect(0,0,w,h);
      for(let i=0;i<16;i++){
       const px=((i*157)%880)-40,py=((i*91)%700)-40,rad=24+(i%5)*27;
       ctx.fillStyle=['#a9efd9','#ffd48f','#f687a3','#6b9ee7','#d0b0f7'][i%5];
       ctx.globalAlpha=.78;ctx.save();ctx.translate(px,py);ctx.rotate(i*.71);ctx.beginPath();
       if(i%3===0){ctx.roundRect(-rad,-rad*.54,rad*2,rad*1.08,rad*.22);}
       else{ctx.ellipse(0,0,rad,rad*.68,0,0,Math.PI*2);}ctx.fill();ctx.restore();
      }
      ctx.globalAlpha=1;ctx.strokeStyle='#ffffff70';ctx.lineWidth=5;
      for(let i=0;i<7;i++){ctx.beginPath();ctx.arc(180+i*79,180+i%3*140,32+i*9,0,Math.PI*1.35);ctx.stroke();}
    }

    return board.sourceCanvas.toDataURL('image/jpeg',0.86);
  }
