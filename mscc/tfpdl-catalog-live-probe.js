import { searchTfpdlCatalog, tfpdlHealth } from './providers/tfpdl-catalog.js'

const health = await tfpdlHealth()
if (!health.healthy) throw new Error('TFPDL catalog health failed: ' + JSON.stringify(health))
if (!(health.postsWithWrappers > 0)) throw new Error('TFPDL current posts exposed no wrapper links')

const publicDomain = await searchTfpdlCatalog('Night of the Living Dead', { year:1968, limit:100 })
const exact = publicDomain.filter(row => row.year === 1968 && row.titleScore >= 1.4)

console.log(JSON.stringify({
  health,
  publicDomainSearch:{
    returned:publicDomain.length,
    exactMatches:exact.length,
    top:publicDomain.slice(0,3).map(row => ({
      title:row.title,
      year:row.year,
      quality:row.quality,
      type:row.type,
      wrapperCount:row.wrapperCount,
      titleScore:row.titleScore,
    })),
  },
  complete:true,
},null,2))
