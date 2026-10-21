package com.night.offlinegames

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal val ink = Color(0xFF172822)
internal val muted = Color(0xFF66746E)
internal val backdrop = Color(0xFFF7F8F5)
internal val accent = Color(0xFF276346)

data class GameEntry(val id: String, val title: String, val category: String, val detail: String)
internal val games = listOf(
    GameEntry("2048", "2048", "Puzzle", "Slide and combine tiles"),
    GameEntry("mines", "Minesweeper", "Puzzle", "Reveal safe cells"),
    GameEntry("blocks", "Block Drop", "Arcade", "Build lines, clear the board"),
    GameEntry("bird", "Wing Hop", "Arcade", "Navigate the moving gaps"),
    GameEntry("maze", "Maze Chase", "Arcade", "Collect dots, dodge chasers"),
    GameEntry("sudoku", "Sudoku", "Puzzle", "Complete the number grid"),
    GameEntry("connect", "Connect Four", "Board", "Outsmart the computer"),
    GameEntry("ttt", "Tic-Tac-Toe", "Board", "Challenge the perfect computer"),
    GameEntry("reversi", "Reversi", "Board", "Flip your opponent's discs"),
    GameEntry("dots", "Dots & Boxes", "Board", "Complete squares and claim points"),
    GameEntry("mancala", "Mancala", "Board", "Sow seeds and capture stones"),
    GameEntry("hex", "Hex", "Board", "Connect your opposite sides"),
    GameEntry("battleship", "Battleship", "Board", "Find the hidden fleet"),
    GameEntry("pentago", "Pentago", "Board", "Place and rotate to connect five"),
    GameEntry("lights", "Lights Out", "Puzzle", "Switch every light off"),
    GameEntry("sliding", "Sliding Puzzle", "Puzzle", "Restore the scrambled 15 tiles"),
    GameEntry("hanoi", "Tower of Hanoi", "Puzzle", "Transfer five disks legally"),
    GameEntry("colorsort", "Water Sort Puzzle", "Puzzle", "Pour matching colours into the same tubes"),
    GameEntry("nonogram", "Nonogram", "Puzzle", "Decode the picture from clues"),
    GameEntry("wordsearch", "Word Search", "Puzzle", "Find six words in the grid"),
    GameEntry("simon", "Simon", "Memory", "Repeat a growing colour pattern"),
    GameEntry("pattern", "Pattern Recall", "Memory", "Remember the highlighted tiles"),
    GameEntry("nim", "Take Away", "Strategy", "Plan your last move"),
    GameEntry("higher", "Higher or Lower", "Card", "Guess the next card"),
    GameEntry("war", "War", "Card", "Reveal cards and compare ranks"),
    GameEntry("snap", "Snap", "Card", "Spot identical ranks"),
    GameEntry("crazyeights", "Crazy Eights", "Card", "Match cards against the CPU"),
    GameEntry("pong", "Pong Classic", "Arcade", "Outplay the computer paddle"),
    GameEntry("breakout", "Breakout", "Arcade", "Clear every brick"),
    GameEntry("invaders", "Space Invaders", "Arcade", "Defend against descending invaders"),
    GameEntry("fruit", "Fruit Slash", "Action", "Slice fruit without hitting hazards"),
    GameEntry("snake", "Snake", "Arcade", "Grow longer, swipe to turn"),
    GameEntry("frogger", "Frogger", "Arcade", "Dodge the moving traffic"),
    GameEntry("stack", "Stack Tower", "Endless", "Time your moving layers"),
    GameEntry("whack", "Whack-a-Bunny", "Action", "Tap the popping bunnies"),
    GameEntry("quickdraw", "Quick Draw", "Action", "React when green appears"),
    GameEntry("coins", "Coin Catcher", "Action", "Catch coins, dodge falling rocks"),
    GameEntry("bubble", "Bubble Pop", "Action", "Pop rising bubbles against time"),
    GameEntry("gravity", "Gravity Flip", "Endless", "Switch between floor and ceiling"),
    GameEntry("go", "Go 5 × 5", "Board", "Capture stones and claim territory"),
    GameEntry("morris", "Nine Men's Morris", "Board", "Form mills and capture pieces"),
    GameEntry("golf", "Golf Solitaire", "Card", "Clear cards in ascending runs"),
    GameEntry("pyramid", "Pyramid Solitaire", "Card", "Remove pairs adding to thirteen"),
    GameEntry("tripeaks", "Tri Peaks", "Card", "Uncover three card peaks"),
    GameEntry("freecell", "FreeCell", "Card", "Four cells, eight cascades"),
    GameEntry("solitaire", "Solitaire", "Card", "Classic draw-one Klondike"),
    GameEntry("mazerunner", "Maze Runner", "Puzzle", "Navigate a new carved labyrinth"),
    GameEntry("darts", "Darts", "Sports", "Time your dart throws"),
    GameEntry("basketball", "Basketball Shoot", "Sports", "Flick a ball into the basket"),
    GameEntry("penalty", "Penalty Shootout", "Sports", "Beat the diving goalkeeper"),
    GameEntry("ski", "Ski Slalom", "Sports", "Stay between the racing gates"),
    GameEntry("bomb", "Bomb Diffuser", "Action", "Cut the correct wires in time"),
    GameEntry("car", "Car Dodge", "Racing", "Weave between speeding traffic"),
    GameEntry("zigzag", "Zigzag Path", "Endless", "Survive the winding path"),
    GameEntry("laser", "Laser Dodge", "Action", "Evade timed sweeping beams"),
    GameEntry("archery", "Archery", "Sports", "Aim against shifting wind"),
    GameEntry("golfputt", "Golf Putt", "Sports", "Play three precision holes"),
    GameEntry("bowling", "Bowling", "Sports", "Ten frames, strikes and spares"),
    GameEntry("swimming", "Swimming Race", "Sports", "Race fifty meters with rhythm"),
    GameEntry("checkers", "Checkers", "Board", "Capture, crown, and chain-jump"),
    GameEntry("chess", "Chess", "Board", "Full legal chess against the CPU"),
    GameEntry("drag", "Drag Race", "Racing", "Shift perfectly through four hundred meters"),
    GameEntry("biketrail", "Bike Trail", "Racing", "Jump rocky hazards"),
    GameEntry("boat", "Boat Race", "Racing", "Navigate floating race gates"),
    GameEntry("drifthunt", "Drift Hunt", "Racing", "Build drifting combos"),
    GameEntry("horse", "Horse Derby", "Racing", "Alternate strides to beat the CPU"),
    GameEntry("memory", "Memory Match", "Memory", "Match illustrated animals, vehicles and space cards"),
    GameEntry("speedboat", "Speed Boat", "Racing", "Boost past river rocks"),
    GameEntry("jetsprint", "Jet Sprint", "Racing", "Navigate a narrowing canyon"),
    GameEntry("spacerace", "Space Race", "Racing", "Collect beacons by inertia"),
    GameEntry("cycle", "Cycle Sprint", "Racing", "Alternate left and right pedals"),
    GameEntry("kart", "Kart Circuit", "Racing", "Race three laps"),
    GameEntry("rally", "Rally Cross", "Racing", "Steer on loose gravel"),
    GameEntry("driftking", "Drift King", "Racing", "Choose the correct corner drift"),
    GameEntry("football", "Football Kick", "Sports", "Curve free kicks past defenders"),
    GameEntry("tabletennis", "Table Tennis", "Sports", "Return balls to eleven"),
    GameEntry("boxing", "Boxing Tap", "Sports", "Read and counter incoming punches"),
    GameEntry("longjump", "Long Jump", "Sports", "Perfect the takeoff"),
    GameEntry("weightlifting", "Weight Lifting", "Sports", "Control five lifts"),
    GameEntry("dartwheel", "Dart Wheel", "Sports", "Place darts on a spinning wheel"),
    GameEntry("tapshooter", "Tap Shooter", "Action", "Hit quick moving targets"),
    GameEntry("zombies", "Zombie Zapper", "Action", "Defend the barricade"),
    GameEntry("ninja", "Ninja Stars", "Action", "Avoid previously placed stars"),
    GameEntry("colorclash", "Color Clash", "Action", "Tap the requested colour"),
    GameEntry("shield", "Shield Block", "Action", "Rotate toward incoming projectiles"),
    GameEntry("balloonguard", "Balloon Guard", "Action", "Protect a rising balloon"),
    GameEntry("towerdefense", "Tower Defense", "Strategy", "Build towers against ten waves"),
    GameEntry("bikerunner", "Bike Runner", "Endless", "Duck and jump over trail hazards"),
    GameEntry("colorswitch", "Color Switch", "Endless", "Match a spinning hoop"),
    GameEntry("ballbounce", "Ball Bounce", "Endless", "Climb bouncing platforms"),
    GameEntry("helix", "Helix Jump", "Endless", "Align gaps in the rotating tower"),
    GameEntry("tapdash", "Tap Tap Dash", "Endless", "Tap at the highlighted corners"),
    GameEntry("paperplane", "Paper Plane", "Endless", "Glide between floating rings"),
    GameEntry("cats", "Cat Collector", "Endless", "Sort cats into coloured shelters"),
    GameEntry("wavesurfer", "Wave Surfer", "Endless", "Lean into the waves"),
    GameEntry("tunnel", "Tunnel Rush", "Endless", "Dodge approaching tunnel panels"),
    GameEntry("pinball", "Pinball Wizard", "Arcade", "Responsive flippers and bumpers"),
    GameEntry("asteroids", "Asteroids", "Arcade", "Thrust, aim and clear drifting rocks"),
    GameEntry("blackjack", "Blackjack", "Card", "Reach twenty-one against the dealer"),
    GameEntry("pokerdice", "Poker Dice", "Card", "Roll and hold five dice combinations"),
    GameEntry("speed", "Speed", "Card", "Race the CPU on two center piles"),
    GameEntry("patternlock", "Pattern Lock", "Puzzle", "Remember the connected-dot sequence"),
    GameEntry("brickbuilder", "Brick Builder", "Puzzle", "Clear complete lines and squares"),
    GameEntry("centipede", "Centipede", "Arcade", "Shoot descending segmented enemies"),
    GameEntry("galaga", "Galaga Blitz", "Arcade", "Survive five diving attack formations"),
    GameEntry("shark", "Shark Survival", "Action", "Swim freely, eat prey and evade ocean predators")
)
internal val categories = listOf("All", "Favourites", "Arcade", "Puzzle", "Board", "Memory", "Strategy", "Card", "Action", "Endless", "Sports", "Racing")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.statusBars())
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = accent, background = backdrop, surface = Color.White)) {
                OfflineHub()
            }
        }
    }
}

