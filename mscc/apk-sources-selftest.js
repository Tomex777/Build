import aptoide from './sources/android/aptoide.js'
import fdroid from './sources/android/fdroid.js'

const aptSearch = {
  id:71289867,
  name:'Aptoide',
  package:'cm.aptoide.pt',
  developer:{ name:'Aptoide' },
  file:{
    vername:'9.22.5.3',
    vercode:12060,
    filesize:20072145,
    path:'https://pool.apk.aptoide.com/example/app.apk',
    malware:{ rank:'TRUSTED' },
  },
}
const normalized = aptoide._test.normalizeSearchRow(aptSearch)
if (normalized?.packageName !== 'cm.aptoide.pt' || normalized?.version !== '9.22.5.3') {
  throw new Error('Aptoide search normalization failed')
}

const aptVersions = aptoide._test.collectVersions({
  nodes:{
    versions:{
      datalist:{
        list:[
          { file:{ vername:'9.22.5.3', vercode:12060, path:'https://cdn.test/a.apk' } },
          { file:{ vername:'9.22.4.1', vercode:12050, path:'https://cdn.test/b.apk' } },
        ],
      },
    },
    meta:{ data:aptSearch },
  },
}, normalized)
if (aptVersions.length !== 2 || aptVersions[0].versionCode !== '12060' || !aptVersions[0].path.endsWith('a.apk')) {
  throw new Error('Aptoide version parsing failed')
}

const fdHtml = `
<div>
  <a href="https://f-droid.org/en/packages/com.termux/">Termux</a>
  <a href="/en/packages/com.termux.api/">Termux:API</a>
</div>
`
const fdItems = fdroid._test.parseSearch(fdHtml)
if (fdItems.length !== 2 || fdItems[0].packageName !== 'com.termux' || fdItems[1].packageName !== 'com.termux.api') {
  throw new Error('F-Droid search parsing failed')
}

const fdVersions = fdroid._test.parseVersions({
  packageName:'com.termux',
  suggestedVersionCode:1002,
  packages:[
    { versionName:'0.119.0-beta.3', versionCode:1022 },
    { versionName:'0.118.3', versionCode:1002 },
  ],
})
if (fdVersions.length !== 2 || fdVersions[1].suggested !== true) {
  throw new Error('F-Droid version parsing failed')
}
if (fdroid._test.apkUrl({ packageName:'com.termux' }, fdVersions[0]) !== 'https://f-droid.org/repo/com.termux_1022.apk') {
  throw new Error('F-Droid APK URL construction failed')
}

console.log('PASS APK source parsers and download contracts')
