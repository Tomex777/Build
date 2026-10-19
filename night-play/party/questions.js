/* NIGHT Play Party question packs inspired by Nomi's game types.
 * Original NIGHT Play curated prompt library. */
(function(root){
const pairs=(type,rows)=>rows.map((options,i)=>({id:type+'-'+i,prompt:type==='wyr'?'Which would you choose?':'Choose one.',options}));
const prompts=(type,rows)=>rows.map((prompt,i)=>({id:type+'-'+i,prompt}));
root.NIGHTPartyPacks={
 wyr:pairs('wyr',[
 ['Explore the ocean floor','Explore the far side of the moon'],['Always know when someone is joking','Always know when someone is bluffing'],
 ['Travel ten years into the future','Visit any day from the past'],['Have unlimited free flights','Have free accommodation everywhere'],
 ['Understand every language','Play every musical instrument'],['Live in a floating city','Live in a mountain village'],
 ['Never lose your keys','Never forget a face'],['Have a pet dragon','Have a friendly robot'],
 ['Master any sport','Master any art form'],['Be famous for a great invention','Be famous for an amazing discovery']
 ]),
 tot:pairs('tot',[
 ['Sunrise','Sunset'],['Mountains','Beaches'],['Call','Text'],['Rain','Snow'],['Movie night','Game night'],
 ['Space','Ocean'],['Coffee','Tea'],['Comics','Novels'],['City','Countryside'],['Summer','Winter']
 ]),
 mlt:prompts('mlt',[
 'Who is most likely to become a famous inventor?','Who is most likely to make everyone laugh?',
 'Who is most likely to arrive an hour early?','Who is most likely to forget where they parked?',
 'Who is most likely to adopt three pets?','Who is most likely to explore a new country?',
 'Who is most likely to write a bestselling book?','Who is most likely to discover a secret shortcut?',
 'Who is most likely to win a cooking contest?','Who is most likely to organize a surprise party?'
 ]),
 nhie:prompts('nhie',[
 'Stayed awake to finish an entire series.','Sung along to a song with the wrong lyrics.',
 'Forgot someone’s name just after meeting them.','Tried a recipe without reading it all.',
 'Waved back at somebody who was waving to someone else.','Lost my place in a book I was reading.',
 'Missed a stop while daydreaming.','Kept a souvenir from a special trip.',
 'Watched the same film twice in one day.','Started laughing during a quiet moment.'
 ]),
 trivia:[
 ['How many hearts does an octopus have?',['1','2','3','8'],2],['Which planet has the shortest day?',['Earth','Mars','Jupiter','Mercury'],2],
 ['What is the capital of Japan?',['Kyoto','Osaka','Tokyo','Sapporo'],2],
 ['How many sides does a dodecagon have?',['10','11','12','14'],2],['Which organ pumps blood around the human body?',['Liver','Lung','Heart','Kidney'],2],
 ['Which animal is fastest on land?',['Lion','Cheetah','Horse','Greyhound'],1],
 ['Which continent is the Sahara Desert mostly in?',['Asia','Africa','Australia','South America'],1],
 ['What is H2O commonly called?',['Salt','Water','Oxygen','Hydrogen'],1],
 ['Which metal has symbol Cu?',['Gold','Copper','Silver','Iron'],1],
 ['Which planet is known for its prominent rings?',['Earth','Venus','Saturn','Mars'],2]
 ].map(([prompt,options,correct],i)=>({id:'trivia-'+i,prompt,options,correct}))
};
})(typeof window!=='undefined'?window:globalThis);