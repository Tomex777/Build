"""Revision 4 integration. Prose and cinematic cues live in revision-scenes.json.
All mechanics compile to the generic engine contract; no reader special cases.
"""
import json,pathlib,re
B=pathlib.Path(__file__).parent

def enhance(story):
 n=story['nodes'];story['legacyInitial']=dict(story['initial']);story['revision']=4
 story['identity']={'firstName':'Sam','surname':'Mansour'}
 story['shortEdition']={'href':'/stories/short-edition.html','saveKey':'night-play-last-call-v2'}
 story['initial'].update({'knowledge_route':0,'underworld_route':0,'divine_route':0,'independent_route':0,'promised_boundary':False,'boundary_broken':False,'accepted_parent':False})
 attrs={'observation':('Observation','Clues, danger and evidence'),'nerve':('Nerve','Fear and supernatural pressure'),'speed':('Speed','Reaction, escape and agility'),'strength':('Strength','Force, endurance and carrying others'),'cunning':('Cunning','Persuasion, deception and improvisation'),'magic':('Magic','Learning and controlling spells')}
 abilities={
  'seal':('Scribe Seal','scribe',['Sense where a drawn seal leaks. Hold one small, named connection for a few breaths.','Correct a moving inscription while someone else holds its source.','Share a seal across two prepared surfaces. Neither can hold a person indefinitely.'],'Numb fingers and lost concentration. A seal cannot command a living person.'),
  'sight':('Spirit Sight','underworld',['Sense a nearby spirit through one existing connection.','Distinguish a remembered voice from a present speaker; ask one clear question.','Anchor a consenting spirit briefly to a prepared receiving point.'],'Listening carries both ways. A spirit may notice the listener; hearing does not establish consent.'),
  'sunward':('Sunward','solar',['Borrow existing light to show an obscured mark.','Turn that light into a brief screen against a false reflection.','Hold the screen while another person crosses. It still needs a light source.'],'After-images and a severe headache. It neither revives the dead nor burns through a god.'),
  'fracture':('Broken Reflection','chaos',['Split a false signal into two imperfect decoys.','Choose which decoy carries the louder return.','Interrupt a claim for a few seconds while another person releases it.'],'Every use leaves a recognisable echo. Forcing an occupied line can consume a return heart.')}
 story['rules']={'startingPoints':4,'attributes':{k:{'title':a,'description':b,'start':1,'max':5} for k,(a,b) in attrs.items()},'resources':{'hearts':{'title':'Return hearts','icon':'heart','start':3,'max':3,'failureNode':'last-return','lossText':'One heart lost','restoreText':'One heart restored','description':'Chances to survive a broken supernatural connection. Ordinary injuries do not spend them.'}},'abilityLimit':2,'abilities':{k:{'title':v[0],'discipline':v[1],'levels':v[2],'cost':v[3],'maxLevel':3,'thresholds':[2,4]} for k,v in abilities.items()}}
 story['interludes']=['heritage-late']
 story['mainEndings']=['release','sever','keeper'];story['endings']['lost']='The Line Goes Quiet';story['failureEndings']=['lost']
 story['migration']={'revisions':[3],'revelationNode':'c24','catchupNode':'heritage-late','interludeLabel':'Read the newly opened envelope','abilities':[{'key':'seal','when':{'lore':'routes'}}]}
 story['knowledge'].update({
  'returns':{'title':'Three returns','text':'Mum knotted three return protections into my coat. A broken claim can spend one to bring me back. A cut or bruise cannot. The stitches are a working model, not three anatomical hearts.'},
  'birth_clue':{'title':'The missing second signature','text':'My birth-registration copy has a covered second signature. Leila thought it was a privacy measure. The ink continues under the covering.'},
  'repeated_refusal':{'title':'The copied agreement','text':'The same instruction treats an earlier answer as continuing agreement. I copied the fault without reopening its return.'},
  'counterledger':{'title':'The counter-ledger','text':'The station records some refusals twice, then treats the repetition as renewed agreement. A separate list can demonstrate the alteration.'},
  'spirit_warning':{'title':'The woman under the stair','text':'A waiting operator remembers a socket that Adrian hid. Listening let her recognise my return address too.'},
  'sun_trace':{'title':'The route under the paint','text':'Borrowed light made the workshop’s earlier route visible. The light revealed an alteration; it did not erase it.'},
  'self_terms':{'title':'A road without a patron','text':'Dennis and I recorded a return by number, with no divine office signing for me. The work takes longer and needs another person watching the ordinary end.'},
  'heritage':{'title':'The sealed birth record','text':'Thoth is my divine father. Mum and he concealed his signature behind a witness seal to hide my return from the Exchange. Leila trusted the covered copy. His absence was a decision, not proof he could not help.'},
  'true_name':{'title':'The unwritten part of my name','text':'My ordinary name is {{fullName}}. Beneath it the register holds a private spoken cadence Mum used when I was small. I recognised it; I did not choose it in a menu. In this story, giving that complete cadence to an occupied line would expose its return. Egyptian ideas about ren inspired the rule; the register and its mechanics are fictional.'},
  'myth_note':{'title':'Notes in Mum’s margin','text':'Thoth’s writing, healing and recording offices and Egyptian traditions about names inform the work. The Toronto Exchange, demigod family, return hearts and spell disciplines are our story’s inventions. The people on the line are not specimens of one uniform ancient Egyptian belief.'}})
 story['items'].update({'tested_torch':'A checked torch and spare batteries','ordinary_return':'Dennis’s numbered return agreement','office_notice':'A limited crossing-office undertaking','counterledger':'The separate counter-ledger of refusals'})
 def last(key):return max((k for k in n if k==key or k.startswith(key+'~')),key=lambda k:int(k.split('~')[1]) if '~' in k else 0)
 def redirect(key,destination):
  for c in n[last(key)]['choices']:c['to']=destination
 def effect(op,key,**more):return {'op':op,'key':key,**more}
 def event(key,detail):return effect('event',key,detail=detail)
 def point(key):return effect('points',key,value=1,once=key)
 def practice(key,at):return effect('practice',key,once=at+':'+key)
 def addfx(key,fx):n[last(key)].setdefault('effects',[]).extend(fx)
 source=json.loads((B/'revision-scenes.json').read_text())
 # Paginate new scenes separately; established v3 addresses never move.
 for key,scene in source.items():
  beats=scene.get('beats') or [{'text':p} for p in re.split(r'\n\s*\n',scene['text'].strip())]
  pages=[];cur=[];words=0
  for beat in beats:
   size=len(beat['text'].split())
   if cur and (words+size>195 or beat.get('pageBreak')):pages.append(cur);cur=[];words=0
   cur.append(beat);words+=size
  if cur:pages.append(cur)
  chapter=next(c['number'] for c in story['chapters'] if c['key']==scene.get('chapter','24')) if scene.get('chapter','24')!='failure' else 0
  for i,page in enumerate(pages):
   ident=key if i==0 else key+'~'+str(i)
   node={'chapter':chapter,'title':scene['title'],'beats':page,'choices':[{'id':ident+':continue','label':'Continue','to':key+'~'+str(i+1),'continue':True}] if i+1<len(pages) else scene.get('choices',[{'id':key+':next','label':'Continue','to':scene.get('to','c24a'),'continue':True}])}
   if scene.get('ending') in story['failureEndings']:node['failureGroup']=key
   if i==0 and scene.get('effects'):node['effects']=scene['effects']
   if i+1==len(pages) and scene.get('endEffects'):node.setdefault('effects',[]).extend(scene['endEffects'])
   if i+1==len(pages) and scene.get('ending'):node['ending']=scene['ending'];node['choices']=[]
   n[ident]=node
 redirect('c03','three-returns');redirect('c06','first-lesson');redirect('c08','covered-signature')
 # Refusing a fare leaves the tram. All original passage addresses remain; only the
 # shared landing/payphone is read on the unwitnessed approach.
 for key in ['b_fare-lie','b_fare-force','b_fare-refuse']:redirect(key,'unwitnessed-stairs')
 reached_landing=False
 for key,node in n.items():
  if key=='c05' or key.startswith('c05~'):
   for beat in node['beats']:
    if beat['text'].startswith('At the top of the stairs I find'):reached_landing=True
    if not reached_landing:beat['when']={'all':([beat['when']] if beat.get('when') else [])+[{'var':'no_transfer','eq':False}]}
 n['c05']['beats'].insert(1,{'text':'The driver has put his cap on the dashboard. Above his seat, a brass card reads ANUBIS. I look back out at the water before deciding to ask another question.','when':{'var':'no_transfer','eq':False}})
 # Knowledge, spirits, patronage and independence are accumulated decisions, never a route-selection menu.
 redirect('c09a','archive-after-hours');redirect('c12a','water-aftermath');redirect('c16a','second-lesson');redirect('c24','heritage-record');redirect('c24a','field-return');n['c28~2']['choices'][0]['to']='mother-answer'
 # c16a has a real pack/leave decision: retain it, route each continuation through later training.
 for k in ['c16a']:
  for c in n[last(k)]['choices']:c['to']='second-lesson'
 n[last('heritage-late')]['choices'][0]['returnToBookmark']=True
 # New physical and social alternatives enter distinct scenes and leave distinct evidence/memories.
 def insert_choice(key,c):n[last(key)]['choices'].insert(-1,c)
 insert_choice('c04',{'id':'c04:cunning','label':'Name the limits of the fare before he can define them','to':'fare-cunning','require':{'attribute':'cunning','gte':3},'requirement':'Cunning 3','effects':[effect('set','transfer',value=True),effect('gain','transfer'),effect('add','independent_route',value=1),effect('remember','precise_fare',character='Anubis',detail='I made him define the limits before giving an account.')]})
 insert_choice('c13',{'id':'c13:strength','label':'Brace the panel and get Julian out without the connector','to':'panel-brace','require':{'attribute':'strength','gte':3},'requirement':'Strength 3','effects':[effect('add','julian',value=1),event('saved_julian','I held the panel while Julian withdrew his hand.'),effect('remember','braced_panel',character='Julian',detail='I took the weight so he could choose when to move.')]})
 insert_choice('c20',{'id':'c20:speed','label':'Reach the window before the cable; ask her to follow','to':'window-speed','require':{'attribute':'speed','gte':3},'requirement':'Speed 3','effects':[effect('add','wren',value=1),effect('remember','left_window',character='Wren',detail='I opened an exit and left it available to her.')]})
 insert_choice('c27',{'id':'c27:nerve','label':'Hold his gaze and state a refusal without surrendering the return','to':'gate-nerve','require':{'all':[{'attribute':'nerve','gte':3},{'item':'ordinary_return'}]},'requirement':'Nerve 3 · an ordinary return','effects':[effect('add','independent_route',value=1),event('self_return','I held the ordinary return open without taking a divine guarantee.')]})
 insert_choice('c20',{'id':'c20:light','label':'Screen the false reflection with borrowed light','to':'workshop-light','require':{'ability':'sunward','gte':2},'requirement':'Sunward 2','effects':[effect('add','wren',value=1)]})
 insert_choice('c20',{'id':'c20:fracture','label':'Split the occupied reflection — lose one heart for a clear exit','to':'workshop-fracture','require':{'ability':'fracture'},'requirement':'Broken Reflection','risk':'One return heart · a recognisable echo','effects':[effect('resource','hearts',value=-1,once='workshop-return',detail='The occupied reflection takes a return through the decoy.'),effect('add','wren',value=-1)]})
 # Powerful magic supplements the established release requirements, rather than erasing consent.
 n[last('c29')]['choices'].insert(0,{'id':'c29:seal','label':'Share the release seal across two prepared readers','to':'final-seal','require':{'all':[{'ability':'seal','gte':3},{'attribute':'magic','gte':3},{'var':'wren','gte':1},{'lore':'release'},{'any':[{'item':'transfer'},{'item':'witness_receipt'}]}]},'requirement':'Scribe Seal 3 · Magic 3 · Wren’s cooperation · witnessed return','effects':[practice('seal','final-seal'),event('released_voices','I shared the release load across the two prepared readers.'),event('scribe_resolution','My trained seal carried the load my injured hand could not.')]})
 n[last('c29')]['choices'].insert(1,{'id':'c29:spirit','label':'Ask the consenting spirits to identify their own returns','to':'final-spirit','require':{'all':[{'ability':'sight','gte':3},{'lore':'spirit_warning'},{'var':'wren','gte':1},{'lore':'release'}]},'requirement':'Spirit Sight 3 · the operator’s warning · Wren’s cooperation','effects':[event('released_voices','I let the waiting senders identify their own returns.'),event('spirit_resolution','The waiting operator helped the senders choose their own roads.')]})
 n[last('c29')]['choices'].insert(2,{'id':'c29:independent','label':'Use the ordinary return Dennis agreed to hold','to':'final-independent','require':{'all':[{'event':'independent_return'},{'item':'ordinary_return'},{'attribute':'nerve','gte':3},{'var':'wren','gte':1},{'var':'imani','gte':1},{'lore':'release'}]},'requirement':'Nerve 3 · an ordinary return · Imani and Wren’s cooperation','effects':[event('released_voices','We released the voices through our own witnessed return.')]})
 n[last('c29')]['choices'].insert(3,{'id':'c29:evidence','label':'Use the counter-ledger to remove the copied refusals','to':'final-evidence','require':{'all':[{'item':'counterledger'},{'lore':'counterledger'},{'attribute':'observation','gte':4},{'var':'wren','gte':1},{'lore':'release'},{'any':[{'item':'transfer'},{'item':'witness_receipt'}]}]},'requirement':'Observation 4 · counter-ledger · Wren’s cooperation · witnessed return','effects':[event('released_voices','The original refusals reduced the final receiving load.')]})
 # Critical risks are explicit; ordinary cuts remain in the original injury variable.
 force=next(c for c in n[last('c27')]['choices'] if c['id']=='c27:force');force['label']='Force the opening — lose one heart and risk serious injury';force['risk']='One return heart';force.setdefault('effects',[]).append(effect('resource','hearts',value=-1,once='forced-gate',detail='The road takes one of the coat’s return knots.'))
 addfx('c12a',[point('water-practice')]);addfx('c16',[point('release-practice')]);addfx('mother-answer',[point('family-truth')])
 # Each mastery increment names a separate use, so revisiting/re-rendering cannot train or pay twice.
 uses={
 'c09a':{'seal':'I check each refusal mark for a leak before Leila calls the next household. The last card holds without my hand beside it.','sight':'I listen only long enough to tell whether the caller is still present, then close my end. Ella’s mother has not agreed to carry another voice.','sunward':'I borrow the desk lamp to find the overwritten digit on the final refusal card. I wait for the after-image to fade before checking it with Julian.','fracture':'I give the empty test request a decoy and close it before Leila uses the real number.'},
 'c12a':{'seal':'I feel along the seal for the break while Julian closes the far end. The pressure leaves my thumb numb. I tell him before my grip fails.','sight':'Before we close the unassigned return, I listen for a present speaker. There is only a repeating request. I close my listening stroke first.','sunward':'I turn the torch’s light along the wet route. It exposes the doubled edge so Julian can close the actual return instead of its reflection. I look away before the after-image becomes blinding.','fracture':'I split the water’s empty reflection just long enough to find which edge is following the real address. I close both decoys before the route shuts.'},
 'c16a':{'seal':'I check the models’ instructions by feeling where the seal catches. It is slower than trusting the copied shape, and this time I notice the closed return before activating it.','sight':'I listen for a present speaker in the models’ borrowed voices. There is none. That does not make an endless instruction safe; I close the listening end before we practise the release.','sunward':'I hold the desk lamp’s light under the models so Julian can compare the earlier mark with its correction. We move the lamp instead of making anyone stare into the reflection.','fracture':'I split one empty test signal between two unused cards, then release both. Neither is allowed to borrow a waiting person.'},
 'c26':{'seal':'In the dry run I share the small seal across two prepared surfaces. When Julian changes the load I name the change aloud before correcting it.','sight':'I practise identifying the present speaker, then closing my end while Wren is still allowed to say stop. She says it twice to check I mean it.','sunward':'I put the light screen between the false receiving mark and the ordinary reader. Imani changes the lamp’s angle until she can see without glare.','fracture':'I rehearse the decoy with an empty line while everyone watches. When it begins using a familiar voice, I close it instead of trying to make the imitation better.'}}
 for passage,by_ability in uses.items():
  for a,prose in by_ability.items():n[last(passage)]['beats'].append({'text':prose,'when':{'ability':a}})
  addfx(passage,[practice(a,passage) for a in abilities])
 # Rehearsing is itself another use; research can reveal a different gate rather than the same reward.
 for c in n[last('c26')]['choices']:
  if c['id']=='c26:rehearse':c.setdefault('effects',[]).extend([practice(a,'rehearsal') for a in abilities])
 # Later conversations remember boundaries and suspicious magic without dispensing friendship prizes.
 n['c23']['beats'].insert(0,{'text':'Wren takes the chair beside the window. “You can listen. That doesn’t mean you get to repeat what I haven’t said out loud.” I put the recorder away before sitting down.','when':{'ability':'sight'}})
 n['c26']['beats'].insert(0,{'text':'Imani lays the torch between us. “The reflection trick got us out. I still want a warning before you make a second version of someone I’m trying to follow.” She leaves her hand on the ordinary torch until I answer.','when':{'ability':'fracture'}})
 n['c28']['beats'].insert(0,{'text':'I can feel which inscription is under strain. I still read Mum’s page twice. Being quick at a task is a particularly efficient way to do it wrong before anyone can stop me.','when':{'ability':'seal'}})
 n['c25']['beats'].insert(0,{'text':'I set the earlier route beside Adrian’s new calibration. The light showed an overwritten receiving end. Julian makes him explain that change before accepting the next number.','when':{'lore':'sun_trace'}})
 n['c26']['beats'].insert(0,{'text':'Wren asks which reflection is supposed to carry the next decoy. “A blank one,” I say. She waits while Julian verifies that it is actually blank.','when':{'event':'chaos_trace'}})
 for end in story['mainEndings']:
  n['c32-'+end]['beats'].insert(-1,{'text':'Dennis gives me the scores from the match he missed. I bring him a printout and stay while he points out which child is his grandson. The return line cost him a Saturday. It is a small enough debt to remember without turning it into a legend.','when':{'event':'independent_return'}})
  n['c32-'+end]['beats'].insert(-1,{'text':'Julian and I finish the crossing report before its end date. If I cannot leave the station, he takes my recorded account. The divine office gets the reader or a written account of why we still need it. Anubis sends back a receipt, not a new obligation.','when':{'event':'patron_terms'}})
  n['c32-'+end]['beats'].insert(-1,{'text':'The counter-ledger stays in the archive, available to families challenging the calls they refused. I do not get to decide which of them will use it.','when':{'lore':'counterledger'}})
  n['c32-'+end]['beats'].insert(-1,{'text':'Mum and I close every copy of my private cadence that the station could still reach. Wren listens only as long as she agrees to. I broke one promise about that name; repairing the damage does not make her pretend I kept it.','when':{'event':'name_exposed'}})
 for end in story['mainEndings']:
  n['c32-'+end]['beats'].insert(-1,{'text':'The return log has all our signatures, including Dennis’s. Nobody can renew the agreement by copying his yes. We file its completed end beside the beginning.','when':{'event':'independent_resolution'}})
  n['c32-'+end]['beats'].insert(-1,{'text':'The counter-ledger comes back with scorch marks on its cover. The entries are intact. Julian catalogues the original and the copy separately, so nobody can mistake the correction for the thing it corrected.','when':{'event':'evidence_resolution'}})
 # Explain differing responses to the revelation in every completed ending.
 for end in story['mainEndings']:
  node=n['c31-'+end]
  node['beats'].insert(0,{'text':'Thoth’s letter is folded beside the notebook. I have not answered it. Mum asks whether I want it put away. “Leave it there,” I say. I want deciding to take longer than one frightened weekend.','when':{'all':[{'lore':'heritage'},{'not':{'var':'accepted_parent','eq':True}}]}})
  node['beats'].insert(0,{'text':'I asked Thoth for one lesson after this, in a room with an ordinary exit. I still mean to go. Accepting the lesson has not forgiven the years he missed, and he has finally stopped confusing the two.','when':{'var':'accepted_parent','eq':True}})
  node['beats'].append({'text':'The private cadence in the register stays off every recording. When Mum uses my name across a room, she uses {{firstName}}. It is the name I turn towards.','when':{'lore':'true_name'}})
 story['recap'].extend([
  {'when':{'lore':'counterledger'},'text':'I preserved the counter-ledger; other families can challenge the repeated refusals.'},
  {'when':{'lore':'spirit_warning'},'text':'I heard the waiting operator and accepted that she could hear me back.'},
  {'when':{'event':'independent_return'},'text':'Dennis held an ordinary return for me without a divine sponsor.'},
  {'when':{'event':'patron_terms'},'text':'I accepted a limited obligation to the crossing office, with an end date.'},
  {'when':{'event':'scribe_resolution'},'text':'My mastered Scribe Seal changed how the final load was carried.'},
  {'when':{'event':'spirit_resolution'},'text':'The dead chose their own receiving roads.'},
  {'when':{'lore':'heritage'},'text':'I learned whose child I was. I kept the name {{fullName}}.'},
  {'when':{'var':'boundary_broken','eq':True},'text':'I broke my promise about the private cadence. Mum and Wren remembered.'},
  {'when':{'resource':'hearts','eq':1},'text':'One return heart remained.'},
  {'when':{'resource':'hearts','eq':0},'text':'The last return broke. My original playthrough remains available beside any chapter replay.'}])
 # Author-specified speaker changes and interruptions use the same reader component as new scenes.
 for node in n.values():
  for beat in node['beats']:
   if beat['text']=='“Thoth?” I ask.':beat.update(type='dialogue',speaker='{{firstName}}',pause=450)
   elif beat['text']=='“Among other ways of asking for my attention.”':beat.update(type='reveal',speaker='The witness',revealName='Thoth',pause=720)
   elif beat['text']=='The line goes dead.':beat.update(type='interruption',pause=500)
   elif beat['text']=='“Dad says you can’t take her yet.”' or beat['text']=="“Dad says you can't take her yet.”":beat.update(type='dialogue',speaker='???',pause=500)
 # Make the unknown caller a separate conversation beat before the first long scene.
 for key in ['b_answer','b_silence']:
  for b in n[key]['beats']:
   if 'The voice says' in b['text'] or 'A voice from the black phone says' in b['text']:
    b.update(type='dialogue',speaker='???',pause=420)
 for key in ['b_answer','b_silence']:
  for beat in n[key]['beats']:
   if beat.get('speaker')=='???':beat['text']='“{{firstName}}?”' if key=='b_answer' else '“{{firstName}}? Please. I know you can hear it.”'
 story['recap'].extend([{'when':{'event':'independent_resolution'},'text':'We used an ordinary witnessed return; no god inherited our decisions.'},{'when':{'event':'evidence_resolution'},'text':'The counter-ledger changed the final load instead of becoming another item in my bag.'}])
 # The ordinary personal name is never the discovered supernatural cadence.
 # A delivered letter never reappears in the protagonist's inventory or epilogue.
 for node in n.values():
  for beat in list(node['beats']):
   if 'The letter in my notebook grows warm.' in beat['text']:
    beat['when']={'not':{'lore':'acknowledgement'}}
    node['beats'].insert(node['beats'].index(beat)+1,{'text':'The completed address in my notebook grows warm. Martin has already received his letter. I hear Celia’s name once, then give her call room to finish without me.','when':{'lore':'acknowledgement'}})
   if 'The letter from Mrs. Bell lies beside the cards.' in beat['text']:
    beat['when']={'not':{'lore':'acknowledgement'}}
    node['beats'].insert(node['beats'].index(beat)+1,{'text':'Celia Bell’s address is copied on the first card. I delivered her letter before the bell broke. I mark that promise complete and leave room for the ones we could not finish.','when':{'lore':'acknowledgement'}})
 story['sheetSections']=[{'title':'The sealed record','text':story['knowledge']['heritage']['text'],'when':{'lore':'heritage'}}]
 for node in n.values():node['atmosphere']='threshold' if 33<=node['chapter']<=35 else 'unease' if node['chapter']<4 else 'quiet'
 story['characters']={'protagonist':{'name':'{{fullName}}','text':'I live in Toronto. Before Friday, the biggest unfinished thing in my life was Mum’s disappearance.'},'Imani':{'name':'Imani','when':{'memory':'asked_first','character':'Imani'},'text':'Goalie. Third floor. Her own Saturday matters.'},'Wren':{'name':'Wren','when':{'lore':'wren'},'text':'She chose her name. She is not Eva.'}}
