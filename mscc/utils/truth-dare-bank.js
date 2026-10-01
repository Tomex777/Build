const truthTopics = [
  'friendship','your closest friendship','family','childhood','school or work','money','crushes','dating',
  'jealousy','trust','loyalty','confidence','your appearance','bad habits','your phone','social media',
  'secrets','lies','regrets','fear','dreams','ambition','procrastination','anger','kindness','competition',
  'attention','compliments','embarrassment','first impressions','forgiveness','popularity','loneliness',
  'friendships that ended','mistakes','gossip','personal boundaries','self-image','responsibility','your future'
]

const truthTemplates = [
  topic => `What is something about ${topic} that you almost never admit out loud?`,
  topic => `What's the most unexpected thing ${topic} has taught you about yourself?`,
  topic => `When it comes to ${topic}, what do you wish you had handled differently?`,
  topic => `What's an opinion you have about ${topic} that people around you probably don't know?`,
  topic => `What's the biggest lie you've ever told yourself about ${topic}?`,
  topic => `What is the bravest thing you've done because of ${topic}?`,
  topic => `What's something involving ${topic} that still makes you cringe when you remember it?`,
  topic => `Who knows the most about how you really feel about ${topic}, and why them?`,
  topic => `What would you change about the way you deal with ${topic} if you could start over?`,
  topic => `If you had to be completely honest, what scares you most about ${topic}?`,
  topic => `What is one thing you secretly wish were different about ${topic}?`,
  topic => `What is the most selfish decision you've made involving ${topic}?`,
  topic => `What's the nicest thing you've done involving ${topic} without telling anyone?`,
  topic => `What's something about ${topic} you judge other people for even though you probably shouldn't?`,
  topic => `What is your biggest weakness when it comes to ${topic}?`,
  topic => `What's the hardest truth you've had to accept about ${topic}?`,
  topic => `What do you pretend not to care about when it comes to ${topic}?`,
  topic => `What's something you wish someone would ask you about ${topic}?`,
  topic => `What is the most impulsive thing you've done because of ${topic}?`,
  topic => `What is one rule you give other people about ${topic} but don't always follow yourself?`,
  topic => `What memory involving ${topic} do you replay in your head more than you'd like to admit?`,
  topic => `What is one thing about ${topic} you've changed your mind about completely?`,
  topic => `If nobody could judge you, what would you do differently about ${topic}?`,
  topic => `What's one thing you hope nobody ever misunderstands about you when it comes to ${topic}?`,
  topic => `What is the most honest sentence you can say about yourself and ${topic} right now?`,
]

const voices = [
  'like a dramatic movie trailer narrator','like a sleepy radio host','like a strict teacher','like a sports commentator',
  'like a cartoon villain','like a royal announcer','like an excited game-show host','like a detective in a mystery',
  'like a robot learning emotions','like a weather reporter'
]
const phrases = [
  'I have absolutely no idea what I am doing','this is my finest moment','I demand snacks immediately',
  'nobody warned me about this','today I choose chaos','I am suspiciously confident','please hold your applause',
  'this meeting could have been a message','I have made a terrible calculation','everything is under control'
]
const objects = ['a spoon','a shoe','a pillow','a charger','a cup','a pen','a sock','a water bottle','a chair','a key']
const animals = ['cat','dog','goat','chicken','owl','frog','duck','penguin','monkey','lion']
const moods = ['overly confident','dramatically offended','extremely suspicious','ridiculously excited','totally confused','very proud','sleepy','nervous','serious','mysterious']
const exercises = ['squats','standing knee raises','toe touches','arm circles','wall push-ups','jumping jacks','calf raises','side steps','shoulder rolls','slow lunges']
const counts = [5,6,7,8,9,10,12,14,15,16]
const selfieFaces = ['your most serious face','an exaggerated shocked face','a fake celebrity pose','your best villain face','a confused face','a huge grin','your best detective face','a dramatic side-eye','your best model pose','your sleepiest face']
const drawingSubjects = ['a cat wearing a crown','a haunted toaster','a superhero potato','a tiny dragon','a suspicious banana','a dancing robot','a flying fish','a fancy frog','a moon with sunglasses','a grumpy cloud']
const constraints = ['without lifting your pen','using your non-dominant hand','in under 20 seconds','with your eyes half closed','using only circles','using only straight lines','as tiny as possible','as badly as possible on purpose','while holding the paper sideways','using exactly ten strokes']
const improvProducts = ['a broken umbrella','one lonely sock','a spoon','a cardboard box','a banana','an empty bottle','a rubber band','a pillow','a pencil','a rock']
const emojiTopics = ['your day','your mood','your last meal','your weekend','your personality','your current problem','your dream holiday','your morning','your favorite movie','your biggest distraction']
const storyWords = [
  ['moon','shoe','secret'],['banana','bus','king'],['cat','phone','storm'],['pizza','ghost','exam'],['river','key','robot'],
  ['mirror','goat','money'],['rain','cake','detective'],['train','sock','dragon'],['teacher','alien','coffee'],['beach','clock','chicken']
]
const challenges = [
  'balance a spoon on your nose for 10 seconds','stand on one foot for 20 seconds','say the alphabet backward as far as you can',
  'name ten foods in 15 seconds','name ten countries in 20 seconds','hold your funniest pose for 15 seconds',
  'keep a completely straight face for 30 seconds','speak without using the word "I" for one minute',
  'say five tongue twisters as fast as you can','count from 30 down to 1 without making a mistake'
]

const dareFamilies = [
  i => `Say "${phrases[i % phrases.length]}" ${voices[Math.floor(i / 10) % voices.length]}.`,
  i => `Pretend ${objects[i % objects.length]} is a priceless luxury product and give it a 20-second sales pitch in a ${moods[Math.floor(i / 10) % moods.length]} mood.`,
  i => `Imitate a ${animals[i % animals.length]} for ${10 + (Math.floor(i / 10) % 5) * 5} seconds without saying what animal it is.`,
  i => `Do ${counts[i % counts.length]} ${exercises[Math.floor(i / 10) % exercises.length]} right now.`,
  i => `Take a selfie with ${selfieFaces[i % selfieFaces.length]}, ${['with no smile','with one raised eyebrow','with your hand under your chin','with a peace sign','with your best side-eye'][Math.floor(i / 10) % 5]}, and keep it completely unedited.`,
  i => `Draw ${drawingSubjects[i % drawingSubjects.length]} ${constraints[Math.floor(i / 10) % constraints.length]} and show the result.`,
  i => `Make a 20-second advertisement for ${improvProducts[i % improvProducts.length]} as if it costs ${['$10','$100','$1,000','$10,000','$1 million'][Math.floor(i / 10) % 5]}.`,
  i => `Describe ${emojiTopics[i % emojiTopics.length]} using exactly ${[3,4,5,6,7][Math.floor(i / 10) % 5]} emojis, then let everyone guess what you meant.`,
  i => { const w=storyWords[i % storyWords.length]; return `Tell a 30-second story that includes the words "${w[0]}", "${w[1]}", and "${w[2]}", and make the ending ${['funny','dramatic','mysterious','ridiculous','surprisingly wholesome'][Math.floor(i / 10) % 5]}.` },
  i => `${challenges[i % challenges.length]} You have ${[15,20,25,30,35][Math.floor(i / 10) % 5]} seconds to do it.`,
  i => `Give a 15-second acceptance speech for winning the award for "${['Most Likely to Lose Their Charger','Best Accidental Comedian','Champion Procrastinator','Most Dramatic Reaction','Best Snack Finder'][i % 5]}" while acting ${moods[Math.floor(i / 5) % moods.length]}.`,
  i => `Choose ${objects[i % objects.length]} and invent a completely new use for it. Demonstrate your invention for 20 seconds while acting ${['like a serious inventor','like an overexcited salesperson','like a confused scientist','like a celebrity launching a product','like a teacher explaining a breakthrough'][Math.floor(i / 10) % 5]}.`,
  i => `Speak for 30 seconds about ${['breakfast','weekends','phones','rain','sleep','music','school','money','movies','food'][i % 10]} without using the letter "${['a','e','i','o','u'][Math.floor(i / 10) % 5]}" in any word if you can.`,
  i => `Do your best impression of someone who is ${moods[i % moods.length]} because they just discovered ${['their phone is at 1%','the food is finished','they won a tiny trophy','their alarm never went off','a goat is following them'][Math.floor(i / 10) % 5]}.`,
  i => `Create a new dance move called "${['The Lost Charger','The Midnight Snack','The Wrong Bus','The Tiny Victory','The Suspicious Wi-Fi'][i % 5]}" and perform it for ${10 + (Math.floor(i / 5) % 10) * 5} seconds.`,
  i => `Give ${objects[i % objects.length]} a name, personality, and life story in under 30 seconds. It must be ${['dramatic','funny','mysterious','heroic','ridiculous'][Math.floor(i / 10) % 5]}.`,
  i => `Hum a well-known tune for ${10 + (i % 5) * 5} seconds while acting ${moods[Math.floor(i / 5) % moods.length]}, without using any words, and let the others guess it.`,
  i => `Make up a headline about yourself beginning with "${['Breaking News','Exclusive','Unbelievable','Just In','Update'][i % 5]}:" and read it like ${voices[Math.floor(i / 5) % voices.length]}.`,
  i => `Pick the nearest harmless object and hold it like a microphone while giving a ${15 + (i % 5) * 5}-second speech about why ${['sleep','snacks','weekends','music','good friends'][i % 5]} deserves more respect, ${voices[Math.floor(i / 5) % voices.length]}.`,
  i => `For the next ${30 + (i % 5) * 15} seconds, answer every question you are asked with exactly ${counts[Math.floor(i / 5) % counts.length]} words.`,
]

function buildTruthBank() {
  const out = []
  for (let t = 0; t < truthTemplates.length; t += 1) {
    for (let p = 0; p < truthTopics.length; p += 1) {
      out.push({ id:`truth-${String(out.length + 1).padStart(4, '0')}`, text:truthTemplates[t](truthTopics[p]) })
    }
  }
  return Object.freeze(out)
}

function buildDareBank() {
  const out = []
  for (let family = 0; family < dareFamilies.length; family += 1) {
    for (let i = 0; i < 50; i += 1) {
      out.push({ id:`dare-${String(out.length + 1).padStart(4, '0')}`, text:dareFamilies[family](i) })
    }
  }
  return Object.freeze(out)
}

export const TRUTH_QUESTIONS = buildTruthBank()
export const DARE_QUESTIONS = buildDareBank()
