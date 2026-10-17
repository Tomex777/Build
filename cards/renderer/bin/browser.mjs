import {chromium} from 'playwright';
import fs from 'node:fs';
export async function launch(){
 const explicit=process.env.NIGHT_BROWSER_PATH;
 const local='/tmp/night-browser/chrome-headless-shell-linux64/chrome-headless-shell';
 return chromium.launch({headless:true,...(explicit?{executablePath:explicit}:fs.existsSync(local)?{executablePath:local}:{}),args:['--no-sandbox','--font-render-hinting=none']});
}
