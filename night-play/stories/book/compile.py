import json,re,pathlib
D=pathlib.Path(__file__).resolve().parent.parent;B=D/'book'
files={p.stem:p.read_text() for p in B.glob('[0-9]*.md')}
order=sorted(k for k in files if '-' not in k)+['30','31','32']
# Numbered editorial keys sort between their neighbouring chapters, not after them.
chapter_numbers={k:i+1 for i,k in enumerate(order)}
N={};chapter_titles={}
# Fixed paragraph boundaries preserve every v3 passage address across prose revisions.
PAGE_COUNTS=json.loads((B/'pagination-v3.json').read_text())
def paragraphs(text):
 out=[];when=None
 for p in re.split('\\n\\s*\\n',text.strip()):
  if p.startswith(':::when '):when={'var':p.split()[1],'eq':True};continue
  if p==':::end':when=None;continue
  out.append({'text':p.replace('\n',' '),**({'when':when} if when else {})})
 return out
def add(key,text,chapter,title=None,choices=None,ending=None):
 bs=paragraphs(text);pages=[]
 if key in PAGE_COUNTS:
  offset=0
  for count in PAGE_COUNTS[key]:pages.append(bs[offset:offset+count]);offset+=count
  if offset!=len(bs):raise ValueError(f'Paragraph boundary changed in {key}: {offset} != {len(bs)}; add a separate authored scene instead.')
 else:
  cur=[];words=0
  for beat in bs:
   n=len(beat['text'].split())
   if cur and words+n>195:pages.append(cur);cur=[];words=0
   cur.append(beat);words+=n
  if cur:pages.append(cur)
 for i,beats in enumerate(pages):
  ident=key if i==0 else key+'~'+str(i)
  following=key+'~'+str(i+1) if i+1<len(pages) else None
  for b in beats:
   if len(b['text'].split())<16:b['pause']=260
  N[ident]={'chapter':chapter_numbers[chapter],'title':title or chapter_titles.get(chapter,''),'beats':beats,'choices':[{'id':ident+':continue','label':'Continue','to':following,'continue':True}] if following else choices or []}
  if ending and not following:N[ident]['ending']=ending
 return key

def choice(id,label,to,effects=None,when=None):return {'id':id,'label':label,'to':to,**({'continue':True} if label=='Continue' else {}),**({'effects':effects} if effects else {}),**({'when':when} if when else {})}
def setv(k,v=True):return {'op':'set','key':k,'value':v}
def inc(k,v):return {'op':'add','key':k,'value':v}
def learn(k):return {'op':'learn','key':k}
def gain(k):return {'op':'gain','key':k}
def event(k,d):return {'op':'event','key':k,'detail':d}
def mem(c,k,d):return {'op':'remember','character':c,'key':k,'detail':d}
def flag(k,v=True):return {'var':k,'eq':v}
def lore(k):return {'lore':k}
for key in order:
 source=files.get(key,files.get(key+'-release',''));chapter_titles[key]=source.splitlines()[0][2:]
# Split scenes only at authored decision points; the remainder retains its own prose.
tails={}
for k in list(files):
 text=files[k];files[k]=text.split('\n',1)[1].strip()
for key,needle,tailkey in [('04','I look past him.','c04_honest'),('13',"I grab Julian's sleeve and pull.",'c13_discard'),('15','I put the phone away.','c15_discard')]:
 text=files[key];a=text.index(needle);tails[tailkey]=text[a:];files[key]=text[:a].rstrip()
# Retain only the common aftermath after the distinct rescue choices.
tails['c13_tail']=tails['c13_discard'][tails['c13_discard'].index('Mum pushes me'):]
tails['c15_tail']=tails['c15_discard'][tails['c15_discard'].index('Afterwards, she takes'):]
files['11']=files['11'].replace('I slide my evidence across the desk.','I keep one hand on the recorder while I decide.')
# The correction exists only on routes which submitted evidence.
idx=files['12'].index('On the way out, Imani buys')
files['12']=':::when filed_evidence\n\n'+files['12'][:idx].strip()+'\n\n:::end\n\n:::when kept_evidence\n\nThe address on the public card takes longer to copy than I expect. Without a witness seal, every redirected mark has to be checked against the notebook. Julian helps without claiming I have proof I chose not to submit. I write the missing receipt on a separate line so nobody can mistake the address for one.\n\n:::end\n\n'+files['12'][idx:]
start=files['12'].index('At the kitchen door') if 'At the kitchen door' in files['12'] else -1
old="She studies the page. Then she looks at me.\n\n“You were going to keep this?”\n\n“I was checking it.”\n\n“With who?”\n\nI don't answer.\n\nShe gives the notebook back carefully, as if she'd rather not be holding it.\n\n“At least let me know what I'm walking into,” she says."
new=":::when lied_imani\n\nShe studies the page. Then she looks at me. “You said there wasn't anything else.” I start to explain about checking it. She gives the notebook back carefully. “Tell me what I'm walking into before I'm already there.”\n\n:::end\n\n:::when shared_imani\n\nShe compares the page with the copy she made at the gallery. “Same sequence,” she says. “Good. We can stop checking one another's handwriting.” She hands the notebook back with the relevant page still open.\n\n:::end"
files['12']=files['12'].replace(old,new)
# Conditional object continuity after different fare choices.
a=files['05'].index('Miso paws at the transfer');b=files['05'].index('Imani has been watching the river',a)
files['05']=files['05'][:a]+':::when transfer\n\n'+files['05'][a:b].strip()+"\n\n:::end\n\n:::when no_transfer\n\nI look at the place where a stamped transfer might have been. Miso follows my gaze. “We can still find the door,” he says. “Don't assume the route will remember us as invited.” I put the blank fare ticket in my pocket and leave the thought there with it.\n\n:::end\n\n"+files['05'][b:]
# Show the date rule before its eventual payoff.
files['09']+='\n\nAt the bottom of one routing sheet, a note specifies that diverted calls carry the next reserved receiving date, not the time of the speaker. I copy it because it may explain tomorrow on my phone. It does not yet explain whose voice I heard.'
# Replaced choices get their own scenes and persistent memories.
branch=json.loads((B/'branches.json').read_text())
branch['gate-refuse']['text']="I refuse the terms and take the upper stair with the others. Wepwawet does not stop me.\n\nMiso finds an old maintenance return halfway up, a route Adrian once used before the stone doors were installed. It is narrow, flooded and no longer held level. I can reach the lower station, but I won't arrive on a recognised road.\n\nThe first section is almost ordinary. Then the water rises to my knees. Imani carries the reader above her head while Julian keeps the receiving card dry inside his coat. I use two holding marks to cross a place where the floor has gone. One collapses before I finish. I strike my knee against a submerged step and keep moving because stopping would leave the person behind me without a place to put their foot.\n\nAt the lower end, Miso opens a service panel. I climb into the station soaked, late and unwitnessed. I did not have to accept the god's terms. I have paid for the other road with time and a knee that will not take my full weight.\n\nImani puts the reader down carefully. “We do not pretend that was free,” she says. I nod before trying to stand."
branch['gate-refuse']['to']='c28'
# Chapter order and normal continuation.
nextkeys={k:'c'+order[i+1] for i,k in enumerate(order[:-1])}
for k in order:
 if k in ['30','31','32']:continue
 opts=[choice('c'+k+':next','Continue',nextkeys[k])]
 for c in opts:c['continue']=True
 if k=='01':opts=[choice('c01:escape','Follow the cat down the fire escape','c02')]
 if k=='02':opts=[choice('c02:invite','Ask Imani to come; share what I know','b_include',[inc('imani',1),mem('Imani','asked_first','I asked for help instead of giving orders.')]),choice('c02:order','Tell her to keep up; explain later','b_dismiss',[inc('imani',-1),mem('Imani','ordered_alley','I ordered her to keep up in the alley.')])]
 if k=='04':opts=[choice('c04:truth','Tell the driver what I really left behind','c04_honest',[setv('transfer'),gain('transfer'),inc('divine_favour',1),learn('anubis'),event('honest_fare','I offered an honest fare at the crossing.')]),choice('c04:negotiate','Negotiate the terms before paying','b_fare-negotiate',[setv('transfer'),gain('transfer'),inc('divine_favour',1),learn('anubis'),mem('Anubis','defined_terms','I asked what the crossing would claim.')]),choice('c04:lie','Claim I left nothing; risk losing the transfer','b_fare-lie',[setv('no_transfer'),inc('suspicion',1),mem('Anubis','false_fare','I gave a false account at the crossing.')]),choice('c04:force','Push past the barrier; risk an injury','b_fare-force',[setv('no_transfer'),inc('injury',1),inc('divine_favour',-1),event('forced_crossing','I tried to force my way past the divine barrier.')]),choice('c04:refuse','Refuse the fare and use the longer exit','b_fare-refuse',[setv('no_transfer'),event('refused_fare','I refused the fare and accepted a longer road.')])]
 if k=='07':opts=[choice('c07:deliver','Keep the promise: take the letter to Martin','c07a',[setv('delivered_letter'),gain('letter'),event('letter_delivered','I delivered Celia Bell’s letter without reading it.')]),choice('c07:wait','Keep the letter safe; return to Leila first','c08',[gain('letter'),event('letter_waiting','Celia Bell’s letter is still waiting to be delivered.')])]
 if k=='10':opts=[choice('c10:share','Show Imani the copied numbers now','b_share',[setv('shared_imani'),inc('imani',1),mem('Imani','shared_voss','I shared Voss’s extra information before using it.')]),choice('c10:hide','Say he gave me nothing else; keep the clue','b_withhold',[setv('lied_imani'),inc('suspicion',1),inc('imani',-1),mem('Imani','lied_voss','I denied having Voss’s extra clue.')])]
 if k=='11':opts=[choice('c11:submit','Submit the evidence; receive a witness seal','b_submit',[setv('filed_evidence'),gain('witness_receipt'),learn('records'),event('evidence_filed','I submitted evidence to challenge the false entry.')]),choice('c11:keep','Keep Mum’s recorder; leave without a seal','b_archive-refuse',[setv('kept_evidence'),event('evidence_kept','I kept the evidence and accepted an uncertified route.')]),choice('c11:steal','Try to steal the book; risk the witness’s trust','b_steal',[setv('kept_evidence'),inc('divine_favour',-1),inc('suspicion',1),mem('Thoth','stole_book','I tried to steal the record I wanted corrected.')])]
 if k=='13':opts=[choice('c13:rescue','Pull Julian free; leave the spare connector','b_save-julian',[inc('julian',1),mem('Julian','rescued','I chose him over a useful component.'),event('saved_julian','I pulled Julian free at the first rescue.')]),choice('c13:component','Take the connector while Imani rescues Julian','b_take-connector',[gain('connector'),inc('injury',1),inc('julian',-1),mem('Julian','left_for_component','I chose the component while Imani pulled him free.')])]
 if k=='15':opts=[choice('c15:stay','Stay until Imani’s session ends','b_rink-stay',[inc('imani',1),mem('Imani','stayed_rink','I stayed to watch the session when the archive called.'),event('rink_stayed','I made time for Imani’s life outside the rescue.')]),choice('c15:leave','Leave to find the manual; tell her afterwards','b_rink-leave',[inc('imani',-1),setv('left_rink'),mem('Imani','left_rink','I left the rink without telling her directly.')])]
 if k=='16a':opts=[choice('c16a:pack','Pack the tested clay models for a defined task','c17',[gain('clay_models'),learn('release'),event('models_tested','I tested and corrected Nadia’s clay load holders.')]),choice('c16a:leave','Leave the models; keep the release notes','c17',[learn('release')])]
 if k=='19':opts=[choice('c19:preserve','Preserve Eva’s original call as evidence','c20',[gain('eva_recording'),learn('original_call'),event('eva_preserved','I kept the record of Eva choosing to leave.')]),choice('c19:clear','Clear the playback so it cannot lure anyone again','c20',[learn('original_call'),event('eva_erased','I cleared Eva’s playback after recording what I learned.')])]
 if k=='20':opts=[choice('c20:ask','Ask the girl what she wants; offer a way out','b_wren-ask',[inc('wren',1),mem('Wren','asked_workshop','I asked instead of ordering her to let me pass.')]),choice('c20:deceive','Lie that Voss authorised I to take the reader','b_wren-deceive',[inc('wren',-1),inc('suspicion',1),mem('Wren','lied_workshop','I used a false instruction from Voss.')]),choice('c20:attack','Strike at her hand with the torch','b_wren-attack',[inc('wren',-2),inc('injury',1),mem('Wren','attacked','I tried to strike her in the workshop.')]),choice('c20:refuse','Refuse a bargain and demand a path out','b_wren-refuse',[mem('Wren','refused_bargain','I refused to bargain and accepted her displeasure.')])]
 if k=='22':opts=[choice('c22:listen','Stay without asking Wren to be useful','c23',[inc('wren',1),mem('Wren','sat_quietly','I stayed without demanding another answer.')]),choice('c22:press','Press for answers; the deadline is getting closer','press_wren',[inc('wren',-1),mem('Wren','pressed','I pressed her for answers after she asked me to stop.')])]
 if k=='26':opts=[choice('c26:study','Study the names and terms of safe passage','c27',[learn('wepwawet'),event('name_studied','I learned which divine name and office to invoke.')]),choice('c26:rehearse','Practise the release sequence once more','c27',[inc('heka',1),event('rehearsed','I used the remaining time to rehearse the release.')])]
 if k=='27':opts=[choice('c27:negotiate','Negotiate passage and return for every willing traveller','b_gate-negotiate',[event('gate_negotiated','I negotiated passage without claiming the road.')]),choice('c27:name','Invoke Wepwawet’s office-name from Nadia’s notes','b_gate-name',[inc('divine_favour',1),event('name_invoked','Knowledge of Wepwawet opened a safer way.')],{'all':[lore('wepwawet'),{'var':'divine_favour','gte':0}]}),choice('c27:force','Try to force the opening; risk serious injury','b_gate-force',[inc('injury',2),inc('divine_favour',-1),event('gate_forced','I tried to force the road and were injured.')]),choice('c27:refuse','Refuse the terms; take the flooded maintenance route','b_gate-refuse',[inc('injury',1),event('gate_refused','I refused the divine terms and took the dangerous bypass.')])]
 if k=='29':
  release={'all':[{'any':[{'var':'suspicion','lte':1},flag('filed_evidence'),{'item':'eva_recording'}]},{'any':[{'var':'injury','lte':2},{'all':[{'var':'injury','lte':3},{'var':'heka','gte':3}]},{'all':[{'item':'clay_models'},{'var':'julian','gte':1}]}]},{'var':'wren','gte':1},lore('release'),{'any':[{'item':'witness_receipt'}, {'item':'transfer'}]}, {'any':[{'var':'imani','gte':1},{'item':'clay_models'},{'var':'julian','gte':1},{'all':[{'item':'connector'},{'var':'heka','gte':3}]}]}]}
  opts=[choice('c29:release','Separate the voices; use the trust and safeguards I earned','c30-release',[event('released_voices','I separated the voices and brought the others home.')],release),choice('c29:sever','Break the bell; save those present and lose unfinished messages','c30-sever',[event('severed_crossing','I severed the crossing and accepted the unfinished losses.')]),choice('c29:keeper','Take the receiving load; remain while the others leave','c30-keeper',[event('became_keeper','I accepted the receiving load under explicit conditions.')])]
 add('c'+k,files[k],k,choices=opts)
add('c04_honest',tails['c04_honest'],'04',choices=[choice('fare:next','Continue','c05')])
add('c13_tail',tails['c13_tail'],'13',choices=[choice('rescue:next','Continue','c14')])
add('c15_tail',tails['c15_tail'],'15',choices=[choice('rink:next','Continue','c16')])
for k,v in branch.items():add('b_'+k,v['text'],v['chapter'],v['title'],[choice('b_'+k+':next','Continue',v['to'])])
add('press_wren','I ask another question. Wren looks at the empty glove on the floor. “I heard you,” she says. “I said not yet.” I begin to explain the deadline. Imani sets the cracker packet down hard enough that I stop. Nobody has to shout for the room to become uncomfortable. I back away from the question, but Wren keeps watching my hands for the rest of the test.','22',choices=[choice('press:next','Continue','c23')])
for ending in ['release','sever','keeper']:
 for k in ['30','31','32']:
  if k=='30' and ending=='release':
   files[k+'-'+ending]=files[k+'-'+ending].replace('She moves to Julian. Together they hold the brass box level.', 'She moves towards the reader, then stops where I need her. The support comes from the work I prepared and the person still willing to hold the other end.')
  add('c'+k+'-'+ending,files[k+'-'+ending],k,choices=[] if k=='32' else [choice('c'+k+'-'+ending+':next','Continue','c'+str(int(k)+1)+'-'+ending)],ending=ending if k=='32' else None)
# Opening retains the measured short passage before the chapter opens out.
N['start']={'chapter':1,'title':chapter_titles['01'],'beats':[{'text':x} for x in ['The hallway is empty.','At least, it was when I locked the door.','Then my phone vibrates.','No caller ID.','The call is dated tomorrow.']],'choices':[choice('start:answer','Answer the call','b_answer',[event('answered_first','I answered the first call.')]),choice('start:silence','Turn the phone off','b_silence',[event('silenced_first','I tried to silence the first call.')])]}
N['start']['beats'][-1].update({'delayBefore':520,'tone':'unease'})
# Seed discovered knowledge and items at the moment they are actually acquired.
effects={'c01':[gain('notebook'),gain('chalk'),learn('call')],'c03':[learn('holding')],'c06':[inc('heka',1),gain('recorder'),learn('deadline')],'c07a':[learn('acknowledgement'),{'op':'lose','key':'letter'}],'c09a':[event('protected_ella','I helped Ella’s household refuse a diverted call.')],'c12a':[inc('heka',1),learn('routes')],'c16':[learn('release')],'c17':[learn('ren'),event('loosened_collar','I asked before loosening Miso’s collar.')],'c19':[learn('eva')],'c21':[learn('wren')],'c24':[learn('receiving_record'),gain('wren_record')],'c24a':[learn('reeds')],'c28':[learn('true_mark')]}
for key,fx in effects.items():
 candidates=[k for k in N if k==key or k.startswith(key+'~')]
 target=max(candidates,key=lambda x:int(x.split('~')[1]) if '~' in x else 0)
 N[target]['effects']=fx
# Immediate consequences and delayed character memory, shown only if they occurred.
callbacks=[('c12','Imani', 'lied_voss','Imani taps the place where I copied the numbers. “You said there was nothing else. Next time tell me before the locked door.”'),('c23','Imani','ordered_alley','“Just keep up,” Imani repeats from the alley. “That was the first thing you asked of me. I noticed.”'),('c26','Julian','rescued','Julian checks the reader, then meets my eyes. “You pulled me out. I can hold this end.”'),('c26','Julian','left_for_component','Julian looks at the spare connector, then at his bandaged hand. “Tell me before you choose a piece of equipment over the person holding it.”'),('c24','Wren','lied_workshop','Wren keeps her receiving card turned away from me. “You said he gave you permission. I remember.”'),('c24','Wren','attacked','Wren moves to Imani’s other side when I reach for the door. The broken torch is still in my bag.'),('c32-release','Imani','left_rink','When Imani talks about selection, she reminds me she looked for me after the first session. I listen before offering to come to the next one.')]
N['c27']['beats'].insert(0,{'text':'The guardian’s gaze stops on the marks of my earlier forced crossing. Knowing a name will not make him overlook how I treated the last road. I will need another way to ask.','when':{'var':'divine_favour','lte':-1}})
N['c30-release']['beats'].insert(0,{'text':'I fit the spare connector into the reader’s empty brace. The component I took gives the load another point of support. Julian checks it carefully before trusting his injured hand near it.','when':{'item':'connector'}})
N['c28']['beats'].insert(0,{'text':'My knee will not take my full weight. Imani carries the reader while I use the wall. Forcing the earlier crossing has not stayed behind at the doors.','when':{'var':'injury','gte':2}})
N['c30-release']['beats'].insert(0,{'text':'I put the tested clay figures along the reader’s supports. Each carries one defined segment, then stops. The rehearsal gives me a way to share a load nobody could safely hold alone.','when':{'all':[{'item':'clay_models'},{'var':'imani','lte':0}]}})
for node,char,mid,txt in callbacks:N[node]['beats'].insert(0,{'text':txt,'when':{'character':char,'memory':mid}})
# More than a label: acquired models can replace the missing person's support.
for n in N.values():
 if n['title']==chapter_titles['30'] and any('Imani moves to Julian' in b['text'] for b in n['beats']):pass
loredefs={
 'call':('The call dated tomorrow','The first call reached {{firstName}} before its speaker could make it. The speaker is still unknown.'),
 'holding':('The temporary hold','A precisely defined connection can be held temporarily. It cannot safely turn a person into a component.'),
 'deadline':('The thirteenth interval','The accumulating messages will complete their circuit at first light on Sunday.'),
 'anubis':('Anubis and the crossing','Anubis keeps a particular road and its passengers. His crossing is a choice, not universal authority over every part of the Duat.'),
 'records':('A correction leaves evidence','The original false entry remains visible beside the record challenging it.'),
 'acknowledgement':('Received without continuation','Celia Bell’s message finished without claiming its receiver.'),
 'routes':('Connections are directional','A route, an address and a person are different subjects for a holding mark.'),
 'release':('The release sequence','Two defined ends and an open refusal: loosen the agreement instead of cutting the body carrying it.'),
 'ren':('Names and identity','A name matters to a person’s identity. Miso’s chosen present name can make room beside a name used to bind him.'),
 'eva':('Eva’s first call','The first call had an end. Later imitations do not establish the same person.'),
 'original_call':('The call she chose to finish','Eva left a message and chose to leave. Voss kept reopening its route.'),
 'wren':('Wren','She chose her own name. She carries intercepted voices but is not Eva Voss.'),
 'receiving_record':('An independent receiving record','Wren can be recognised separately from the messages she carries.'),
 'reeds':('A field with work to do','The Field of Reeds is encountered as a lived place, not a waiting room for somebody else’s grief.'),
 'wepwawet':('Wepwawet: Opener of the Ways','Knowing which divine name and office to address can open an informed request. It does not confer ownership of a god.'),
 'true_mark':('Nadia’s original receiving mark','The false copy has a reversed final stroke. The original remains scratched underneath it.')}
items={'notebook':'Nadia’s blue notebook','chalk':'Chalk and holding marks','transfer':'A witnessed crossing transfer','letter':'Celia Bell’s letter','recorder':'A copy of Nadia’s research recordings','witness_receipt':'A certified witness receipt','connector':'The spare connector from the first rescue','clay_models':'Three tested clay load holders','eva_recording':'Eva’s original call recording','wren_record':'Wren’s independent receiving record'}
recap=[('answered_first','I answered the first call.'),('silenced_first','I tried to silence the first call.'),('letter_delivered','I delivered a private letter without reading it.'),('letter_waiting','I left a letter waiting while I pursued the rescue.'),('saved_julian','I chose Julian over a useful component.'),('evidence_filed','I put the false record on the record.'),('evidence_kept','I kept my mother’s recording instead of obtaining a witness seal.'),('rink_stayed','I stayed for Imani’s selection session.'),('eva_preserved','I preserved Eva’s original goodbye.'),('eva_erased','I cleared the playback that could be used to imitate Eva.'),('name_invoked','Knowledge opened a road that force could not.'),('gate_forced','Trying to force the road left an injury.'),('gate_refused','I refused divine terms and took the flooded bypass.'),('released_voices','I returned the voices and brought my mother home.'),('severed_crossing','I saved those present and cut off unfinished messages.'),('became_keeper','I stayed to receive the voices under terms I chose.')]
story={'id':'the-last-call','revision':3,'title':'The Last Call','author':'NIGHT','genre':'Egyptian mythology · Contemporary fantasy','start':'start','initial':{'imani':0,'julian':0,'wren':0,'injury':0,'heka':0,'suspicion':0,'divine_favour':0,'shared_imani':False,'lied_imani':False,'transfer':False,'no_transfer':False,'filed_evidence':False,'kept_evidence':False,'delivered_letter':False,'left_rink':False},'chapters':[{'number':chapter_numbers[k],'key':k,'title':chapter_titles[k]} for k in order],'knowledge':{k:{'title':v[0],'text':v[1]} for k,v in loredefs.items()},'items':items,'endings':{'release':'An Ordinary Morning','sever':'What Silence Holds','keeper':'The Person Who Answers'},'recap':[{'when':{'event':k},'text':t} for k,t in recap]+[{'when':{'memory':'lied_voss','character':'Imani'},'text':'Imani remembered the clue I denied having.'},{'when':{'memory':'asked_workshop','character':'Wren'},'text':'I asked Wren for help before treating her as a tool.'}],'nodes':N}
# Hash keys let v3 recorded pages keep their exact conditional paragraphs in the new perspective.
legacy_keys=json.loads((B/'legacy-text-keys.json').read_text())
story['legacyText']={h:N[k]['beats'][i]['text'] for k,hashes in legacy_keys.items() for i,h in enumerate(hashes)}
from enhancement import enhance
enhance(story)
# Exact authored prose counts; renderer output and editorial notes are not added.
words=sum(len(b['text'].split()) for n in N.values() for b in n['beats'])
story['wordCount']=words;story['estimatedMinutes']=145
(D/'last-call.json').write_text(json.dumps(story,ensure_ascii=False,separators=(',',':')))
print('Compiled',len(N),'passages,',len(order),'chapters,',words,'authored words across all branches.')
