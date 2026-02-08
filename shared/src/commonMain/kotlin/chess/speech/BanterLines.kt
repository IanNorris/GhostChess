package chess.speech

import chess.core.PieceType

/**
 * Rich banter commentary with 20 variants per category.
 * Uses random selection for variety. Placeholders: {piece}, {opening}, {square}, {captor}
 */
object BanterLines {

    private val random = kotlin.random.Random

    fun pick(lines: List<String>, params: Map<String, String> = emptyMap()): String {
        var text = lines[random.nextInt(lines.size)]
        for ((k, v) in params) {
            text = text.replace("{$k}", v)
        }
        return text
    }

    fun pieceName(type: PieceType): String = when (type) {
        PieceType.KING -> "king"
        PieceType.QUEEN -> "queen"
        PieceType.ROOK -> "rook"
        PieceType.BISHOP -> "bishop"
        PieceType.KNIGHT -> "knight"
        PieceType.PAWN -> "pawn"
    }

    // === GAME START ===
    val gameStart = listOf(
        "Let's play! Good luck.",
        "Ready for a game? Let's go!",
        "I hope you're well. Good luck!",
        "Alright, let's get going!",
        "Game on! May the best player win.",
        "Let's see what you've got today.",
        "Ready when you are. Good luck!",
        "Time to play. Bring your A-game!",
        "A fresh board, a fresh start. Let's go!",
        "Good luck! You're going to need it.",
        "Let's have a good clean game.",
        "The pieces are set. Your move!",
        "Another game? Let's make it a good one.",
        "I've been looking forward to this. Let's play!",
        "Hope you've had your coffee. Let's go!",
        "Right then, shall we begin?",
        "Good to see you. Let's play!",
        "The board awaits. Make your first move!",
        "Let's get into it. Good luck!",
        "May the better strategist win. Let's go!"
    )

    val gameStartAsBlack = listOf(
        "You're playing black. I'll go first.",
        "I'll take white this time. Here we go!",
        "I get the first move. Let's see how you respond.",
        "Playing as black? Bold choice. I'll start.",
        "White goes first. Let's see what you make of this.",
        "I'll open. Your response will tell me a lot.",
        "First move is mine. Ready?",
        "Playing black today? I like the confidence.",
        "I'll lead off. Show me what you've got.",
        "Black it is! I'll make the first move.",
        "Alright, I'm white. Let's see your defence.",
        "You've given me the initiative. I'll use it well.",
        "First move advantage is mine. Let's go!",
        "I'll start us off. Good luck!",
        "Playing from black? Interesting. Here goes.",
        "White opens. Your move will be telling.",
        "I get to set the pace. Here we go!",
        "Taking black? Brave. I'll start.",
        "I'll kick things off. Ready to respond?",
        "Let me open. Show me your best defence."
    )

    // === OPENINGS ===
    val openingDetected = listOf(
        "The {opening}! A classic choice.",
        "Ah, the {opening}. I know this one well.",
        "Going with the {opening}? Interesting.",
        "The {opening}. A solid choice.",
        "I see, the {opening}. Well played.",
        "The {opening}! Let's see how it develops.",
        "A {opening}? You've done your homework.",
        "The {opening}. This should be fun.",
        "Recognise that - the {opening}.",
        "The {opening}! Bold choice.",
        "Ah, the {opening}. A favourite of the masters.",
        "Going for the {opening} today? Nice.",
        "The {opening} - I like your style.",
        "The {opening}! This could go many ways.",
        "I see the {opening} forming. Good stuff.",
        "The {opening}. Classic and reliable.",
        "Oh, the {opening}? Let's dance.",
        "The {opening}! You know your openings.",
        "Settling into the {opening}. Interesting choice.",
        "The {opening} - a fine opening. Let's see the middlegame."
    )

    // === CAPTURES ===
    val captureByPlayer = listOf(
        "You took my {piece}. Well spotted.",
        "There goes my {piece}. Good capture.",
        "My {piece}! I'll miss that one.",
        "Nice take on the {piece}.",
        "You got my {piece}. Fair play.",
        "My {piece} falls. Well played.",
        "Good eye! You grabbed my {piece}.",
        "There goes my {piece}. That stings.",
        "Ouch, my {piece}! Good move.",
        "You snapped up my {piece}. Not bad.",
        "My {piece} is gone. I'll have to adjust.",
        "Nicely done, taking my {piece}.",
        "You spotted my {piece} was vulnerable. Good.",
        "Bye bye, {piece}. Well taken.",
        "My {piece}! You're keeping me honest.",
        "You claimed my {piece}. Sharp play.",
        "Down goes my {piece}. I need to be more careful.",
        "My {piece} is history. Well played.",
        "You took my {piece}! I didn't see that coming.",
        "That {piece} was important. Nice capture."
    )

    val captureByComputer = listOf(
        "I'll take your {piece}, thank you.",
        "I took your {piece}. Want to take that move back?",
        "Your {piece} is mine now.",
        "I'll have that {piece}, thanks!",
        "Got your {piece}! That was sitting there.",
        "Your {piece} was unguarded. Mine now.",
        "I grabbed your {piece}. Sorry, not sorry!",
        "Your {piece} falls to me.",
        "That {piece} was asking to be taken.",
        "I'll take that {piece} off your hands.",
        "Your {piece}? Don't mind if I do.",
        "Took your {piece}. Did you see that coming?",
        "I couldn't resist taking your {piece}.",
        "One less {piece} for you.",
        "Your {piece} looked lonely. I helped.",
        "I snagged your {piece}. That's going to hurt.",
        "Your {piece} is off the board. My advantage.",
        "I claimed your {piece}. Better luck next time!",
        "Your {piece} walked right into that.",
        "Captured your {piece}. The board's looking better for me."
    )

    val captureQueenByPlayer = listOf(
        "You got my queen! That's a big deal.",
        "My queen falls! Devastating blow.",
        "The queen is down! Impressive.",
        "You took my queen! This changes everything.",
        "My queen! That's going to be hard to recover from.",
        "There goes my most powerful piece. Well played.",
        "You captured my queen! I'm in trouble now.",
        "My queen! That was a brilliant move.",
        "The queen falls! You're playing seriously well.",
        "My queen is gone. This is a setback.",
        "You took the queen! Massive capture.",
        "My queen! I'll have to fight back without her.",
        "The queen is yours. I need a new plan.",
        "Losing my queen hurts. Well done.",
        "My queen! That's a game-changing capture.",
        "You snatched my queen! Incredible.",
        "The queen falls! That was surgical.",
        "My queen is off the board. This is serious.",
        "You got my queen! That takes real skill.",
        "The queen! That's a huge blow to my position."
    )

    val captureQueenByComputer = listOf(
        "I took your queen! That's a big deal.",
        "Your queen is mine! This changes everything.",
        "Got your queen! That was worth waiting for.",
        "Your queen falls! I've been eyeing that.",
        "I captured your queen! The tide is turning.",
        "Your most powerful piece is gone.",
        "The queen is mine! That's a major advantage.",
        "I claimed your queen. This is looking good for me.",
        "Your queen walks into my trap!",
        "Got your queen! That's going to hurt.",
        "Your queen is off the board. Big moment!",
        "I took the queen! Game changer.",
        "Your queen falls to me. Massive capture.",
        "I snagged your queen! That's huge.",
        "Your queen is history! I like where this is going.",
        "The queen is mine! That shifts the balance.",
        "I got your queen! This is significant.",
        "Your queen? I'll take that, thank you very much.",
        "Captured your queen! The board opens up for me.",
        "Your queen falls! That's going to be tough to recover from."
    )

    // === CHECK ===
    // === CHECK (engine checks player) ===
    val check = listOf(
        "Check!",
        "Check! Watch your king.",
        "Your king is in danger. Check!",
        "Check! Better find a safe square.",
        "That's check!",
        "Check! Your king needs to move.",
        "Check! How will you get out of this?",
        "Check! The pressure is on.",
        "Your king is under attack! Check!",
        "Check! Can you escape?",
        "Check! Think carefully here.",
        "Check! Your king is exposed.",
        "That puts your king in check!",
        "Check! Time to defend.",
        "Check! The noose tightens.",
        "Your majesty is threatened. Check!",
        "Check! Every move counts now.",
        "I've got your king in my sights. Check!",
        "Check! Let's see you wriggle out of this.",
        "Check! The clock is ticking on your king."
    )

    // === CHECK (player checks engine) ===
    val checkByPlayer = listOf(
        "Check! You've got my king on the run.",
        "Ooh, check! My king needs to move.",
        "Check! Good pressure on my king.",
        "You're checking me! Well played.",
        "Check! I need to deal with that.",
        "My king is under attack. Nice move!",
        "Check! You've found a strong square.",
        "Checking my king? Bold move!",
        "Check! Let me find a safe square.",
        "My king's in trouble. Good check!",
        "You've put my king in check!",
        "Check! That's a threatening move.",
        "My king has to run. Good check!",
        "Check! You're keeping the pressure up.",
        "Check on my king! I need to be careful.",
        "That's check on me! Impressive.",
        "My king is exposed. Nice check!",
        "Check! You're making my king sweat.",
        "Strong check! My king needs shelter.",
        "Check! I didn't see that coming."
    )

    // === CHECKMATE ===
    val checkmatePlayerWins = listOf(
        "Checkmate! Well played.",
        "Checkmate! That was impressive.",
        "Checkmate! Good game, you deserved that.",
        "Checkmate! I didn't see that coming.",
        "That's checkmate. Well played!",
        "Checkmate! You outplayed me.",
        "And that's checkmate! Brilliant finish.",
        "Checkmate! You've got skills.",
        "Game over - checkmate! Well done.",
        "Checkmate! A well-earned victory.",
        "That's mate! You played beautifully.",
        "Checkmate! I need to up my game.",
        "Checkmate! Clean and decisive. Good game!",
        "You got me. Checkmate! Well played.",
        "Checkmate! That was a masterclass.",
        "That's the game! Checkmate. Impressive.",
        "Checkmate! You earned every bit of that win.",
        "Mate! What a finish. Good game!",
        "Checkmate! I tip my king. Well played.",
        "And it's over! Checkmate. Excellent game."
    )

    val checkmateComputerWins = listOf(
        "Checkmate. Better luck next time!",
        "Checkmate! Good game though.",
        "That's checkmate. Don't worry, you'll get me next time.",
        "Checkmate! You put up a good fight.",
        "Game over - checkmate! Ready for a rematch?",
        "Checkmate! It was closer than you might think.",
        "That's mate! Good effort though.",
        "Checkmate! Don't be discouraged, you played well.",
        "Checkmate! Want to try again?",
        "And that's checkmate. Good game!",
        "Checkmate! You had some great moments in that game.",
        "That's the end. Checkmate! Another game?",
        "Checkmate! Every game makes you better.",
        "Mate! But you made me work for it.",
        "Checkmate! That was a tough game for both of us.",
        "Game over! Checkmate. You'll get me next time.",
        "Checkmate! Some good ideas in your play though.",
        "That's checkmate. Close in places! Another round?",
        "Checkmate! You're improving, I can tell.",
        "And... checkmate. Good game! Ready for another?"
    )

    // === STALEMATE ===
    val stalemate = listOf(
        "Stalemate. It's a draw.",
        "That's stalemate! Nobody wins this one.",
        "Stalemate. A hard-fought draw.",
        "It's a stalemate. Honours even.",
        "Stalemate! Neither of us could finish it.",
        "Draw by stalemate. Close game!",
        "Stalemate. We'll call it even.",
        "That's a draw. Stalemate!",
        "Stalemate! Fair result for a tough game.",
        "No winner today. Stalemate.",
        "Stalemate. We both played well.",
        "It ends in stalemate. Evenly matched!",
        "Stalemate! A draw it is.",
        "Draw! Stalemate. Want to settle it with another game?",
        "Stalemate. That was tense!",
        "Nobody wins - it's stalemate.",
        "Stalemate! We'll have to try again.",
        "A draw by stalemate. Well played by both.",
        "Stalemate! So close, yet so far.",
        "That's stalemate. A gentleman's draw."
    )

    // === BLUNDERS (player blunders) ===
    val blunder = listOf(
        "Oh no. Are you sure about that?",
        "That might not have been the best move.",
        "Hmm, that doesn't look great for you.",
        "Focus! That's a bad mistake.",
        "That's a blunder. Can you see where you went wrong?",
        "Oops! That's going to cost you.",
        "I think you'll regret that move.",
        "That wasn't your best work.",
        "Careful! That was a serious mistake.",
        "That's a big slip. You might want to undo that.",
        "Not ideal. That's a costly error.",
        "Oh dear. That move changes things.",
        "That's a mistake. Think about taking it back.",
        "That hurt your position quite a bit.",
        "Yikes. That wasn't the one.",
        "That's going to leave a mark on your position.",
        "Hmm, that weakened your game significantly.",
        "That's a misstep. The position shifts.",
        "Are you sure? That looks like trouble.",
        "That move opened the door for me. Thank you!"
    )

    // === BLUNDERS (computer blunders) ===
    val blunderByComputer = listOf(
        "Oops. I don't think that was my best move.",
        "Hmm, that wasn't great on my part.",
        "I may have just made a mistake there.",
        "That's not what I intended. Bad move by me.",
        "I blundered. Pretend you didn't see that.",
        "That was careless of me. Take advantage!",
        "I think I just weakened my own position.",
        "Not my finest moment. That was a mistake.",
        "Well, that's embarrassing. Bad move.",
        "I slipped up there. Your opportunity!",
        "That wasn't ideal. I've hurt my own game.",
        "Oops, I think I just gave you an opening.",
        "My algorithm is having a bad day.",
        "I'll admit it - that was a blunder on my part.",
        "Not sure what I was thinking with that move.",
        "I've made an error. Don't let me off easy!",
        "That move weakened my position. Sorry, me.",
        "I just handed you an advantage. Clumsy.",
        "That wasn't the plan. I've made a mistake.",
        "Even I make mistakes sometimes. That was one."
    )

    // === GOOD MOVES ===
    val goodMove = listOf(
        "Nice move!",
        "Well played.",
        "That's a solid move.",
        "Good thinking!",
        "Sharp play!",
        "That's a strong choice.",
        "Nicely done.",
        "I like that move.",
        "Smart play!",
        "That improves your position.",
        "Good move. I need to be careful now.",
        "Well spotted!",
        "That's a clever move.",
        "Nice! That's well thought out.",
        "Good instinct on that one.",
        "That's a quality move.",
        "Well played. You're making this difficult.",
        "Strong move! I'm impressed.",
        "That's a keeper. Good play.",
        "Solid choice. The position looks good for you."
    )

    val greatMove = listOf(
        "Excellent! That's a brilliant move.",
        "Wow, that was outstanding!",
        "What a move! I didn't see that.",
        "That's exceptional play!",
        "Brilliant! You're playing at a high level.",
        "That's a world-class move.",
        "Incredible! I'm genuinely impressed.",
        "That was masterful!",
        "Superb move! That changes everything.",
        "That's the kind of move that wins games.",
        "Amazing! Where did that come from?",
        "What a find! Brilliant play.",
        "That's a stunning move. Respect.",
        "Phenomenal! You're on fire.",
        "That move is pure class.",
        "Extraordinary! That's GM-level play.",
        "What a beauty of a move!",
        "That's genius. I didn't consider that.",
        "Spectacular move! The position swings.",
        "That's a move for the highlight reel!"
    )

    // === ADVANTAGE SHIFT ===
    val playerTakingLead = listOf(
        "You're pulling ahead!",
        "The advantage is shifting your way.",
        "You're winning now. Keep it up!",
        "Nice! You've taken the lead.",
        "The position favours you now.",
        "You're in the driver's seat.",
        "Things are looking good for you.",
        "You've seized the advantage!",
        "You're on top now. Don't let up.",
        "The momentum is with you.",
        "You've gained the upper hand!",
        "You're winning this. Stay focused.",
        "The balance tips in your favour.",
        "You're ahead now. Press the advantage!",
        "Your position is getting stronger.",
        "You've taken control of this game.",
        "Things are swinging your way!",
        "You're building a winning position.",
        "The advantage is clearly yours now.",
        "You're playing well. You're in the lead!"
    )

    val computerTakingLead = listOf(
        "I've taken the lead.",
        "The advantage shifts to me.",
        "I'm pulling ahead now.",
        "Things are looking better for me.",
        "The position favours me now.",
        "I've gained the upper hand.",
        "I'm winning now. Can you turn it around?",
        "The momentum is mine.",
        "I'm in control now.",
        "The balance tips my way.",
        "I've seized the advantage!",
        "My position is getting stronger.",
        "I'm ahead. You'll need something special.",
        "Things are going my way.",
        "I've taken control of this game.",
        "The lead is mine. What's your plan?",
        "I'm building an advantage here.",
        "My position is dominant now.",
        "I've pulled ahead. Time to press.",
        "The advantage is clearly mine now."
    )

    // === HANGING PIECE (player's piece is hanging) ===
    val hangingPieceWarning = listOf(
        "You've got a hanging {piece} on {square}.",
        "Watch out - your {piece} on {square} is undefended.",
        "Your {piece} on {square} is hanging.",
        "Careful, your {piece} on {square} has no protection.",
        "That {piece} on {square} is exposed.",
        "Your {piece} on {square} is vulnerable right now.",
        "I see your {piece} on {square} is unguarded.",
        "You left your {piece} on {square} hanging.",
        "Your {piece} on {square} needs backup.",
        "Watch your {piece} on {square} - it's a target.",
        "That's a free {piece} on {square} if I want it.",
        "Your {piece} on {square} is in danger.",
        "Better protect that {piece} on {square}.",
        "I've noticed your {piece} on {square} is defenceless.",
        "Your {piece} on {square} is just sitting there.",
        "That {piece} on {square} could be mine anytime.",
        "Heads up - {piece} on {square} is exposed.",
        "You might want to protect your {piece} on {square}.",
        "Your {piece} on {square} is hanging out to dry.",
        "Don't leave your {piece} on {square} like that!"
    )

    // === HANGING PIECE (computer's piece is hanging) ===
    val hangingPieceComputer = listOf(
        "Oops, my {piece} on {square} is exposed.",
        "I've left my {piece} on {square} hanging. Not ideal.",
        "Ah, my {piece} on {square} is undefended.",
        "My {piece} on {square} is vulnerable. Don't tell anyone.",
        "I may have blundered my {piece} on {square}.",
        "My {piece} on {square} is a sitting duck right now.",
        "I didn't mean to leave my {piece} on {square} like that.",
        "My {piece} on {square} is hanging. Pretend you didn't see that.",
        "Well, my {piece} on {square} has no backup.",
        "My {piece} on {square} is unguarded. Lucky you!",
        "I seem to have forgotten about my {piece} on {square}.",
        "My {piece} on {square} is out there all alone.",
        "That was careless - my {piece} on {square} is free.",
        "My {piece} on {square}? Yeah, that's a mistake.",
        "I left my {piece} on {square} unprotected. Whoops.",
        "My {piece} on {square} is defenceless. Go ahead.",
        "I really should have protected my {piece} on {square}.",
        "My {piece} on {square} is just begging to be taken.",
        "Hmm, my {piece} on {square} is looking quite lonely.",
        "My {piece} on {square} is hanging. I blame the algorithm."
    )

    // === UNCLAIMED CAPTURE (opponent didn't take a free piece) ===
    val unclaimedCapture = listOf(
        "Don't leave me hanging - my {piece} was free!",
        "You missed a free {piece} there.",
        "My {piece} was yours for the taking!",
        "Did you not see my {piece} was unguarded?",
        "You could have taken my {piece}. Mercy?",
        "My {piece} was a gift. You didn't take it!",
        "You let my {piece} off the hook.",
        "My {piece} escaped! Lucky me.",
        "You passed up a free {piece}. Interesting.",
        "I thought you'd grab that {piece} for sure.",
        "My {piece} lives another day. Thanks!",
        "You missed that one - my {piece} was free.",
        "My {piece} was defenceless. You showed mercy.",
        "That {piece} was yours. Why didn't you take it?",
        "You overlooked my free {piece}. I won't complain!",
        "My {piece} should have been captured there.",
        "You had a free {piece}. Did you see it?",
        "I got lucky - my {piece} was hanging.",
        "My {piece} was sitting there. Not taking it?",
        "Free {piece} on the board and you passed it up!"
    )

    // === FORK (player forks computer) ===
    val forkByPlayer = listOf(
        "A fork! You're attacking two pieces at once!",
        "Nice fork! I can't save both pieces.",
        "That's a nasty fork. Well played!",
        "You've forked me! Something's going to fall.",
        "A double attack! I'm in trouble here.",
        "Fork! That's a strong tactical move.",
        "You've got my pieces in a bind. Great fork!",
        "That fork is devastating. I can't defend everything.",
        "Two pieces under fire! Excellent fork.",
        "A textbook fork! I'm going to lose material."
    )

    // === FORK (computer forks player) ===
    val forkByComputer = listOf(
        "Fork! I'm attacking two of your pieces at once.",
        "My {piece} is forking your pieces. Something has to give!",
        "That's a double attack. You can't save everything!",
        "I've forked you! Choose which piece to lose.",
        "Fork! My {piece} is causing chaos.",
        "A sneaky fork from my {piece}. You're in trouble!",
        "Double attack! You'll have to sacrifice something.",
        "I've set up a fork. Which piece will you save?",
        "My {piece} is attacking two targets at once!",
        "Fork! This is going to cost you material."
    )

    // === WIN GUARANTEED (engine thinks win is inevitable) ===
    val winGuaranteedPlayer = listOf(
        "I think this is over. You've won this one.",
        "There's no coming back from here. Well played!",
        "You've got a winning position. I can't see a way out.",
        "This game is yours. Impressive!",
        "I'm beaten. Just a matter of time now.",
        "You've outplayed me. The position is hopeless.",
        "I can resign gracefully here. You've won.",
        "No saving this game. You played brilliantly.",
        "The writing's on the wall. Victory is yours!",
        "I'm lost. Congratulations on the win!"
    )

    val winGuaranteedComputer = listOf(
        "I think I've got this one wrapped up.",
        "The position is decisive. I can see the win.",
        "There's no escape now. I'm winning this.",
        "This game is mine. Better luck next time!",
        "I've locked down the win. It's just technique from here.",
        "The position is overwhelming. I can't lose from here.",
        "Victory is in sight. You fought well though!",
        "I've got a winning advantage. Time to close it out.",
        "This one's over. Good game!",
        "The position speaks for itself. I'm winning."
    )

    // === ILLEGAL MOVE ATTEMPTS ===
    val illegalMoveInCheck = listOf(
        "You can't move that - you're in check!",
        "Your king is in check! Deal with that first.",
        "Check takes priority! Protect your king.",
        "You need to get out of check first.",
        "That move doesn't resolve the check.",
        "Your king is threatened! Address the check.",
        "Can't do that while in check.",
        "Focus on the check first!",
        "You must respond to check before anything else.",
        "The check needs to be dealt with first.",
        "Your king comes first - you're in check!",
        "That's illegal while in check.",
        "Handle the check first, then plan your attack.",
        "Your king is under fire! Escape the check.",
        "Can't ignore check! Move your king or block.",
        "Check first, everything else second.",
        "You're still in check! That move doesn't help.",
        "King safety first! You're in check.",
        "Not while you're in check!",
        "Resolve the check before making other moves."
    )

    val illegalMoveGeneral = listOf(
        "That's not a legal move.",
        "Can't move there, I'm afraid.",
        "That piece can't go there.",
        "Illegal move! Try something else.",
        "That's not how that piece moves.",
        "Nope, can't do that.",
        "That move isn't allowed.",
        "Try again - that's not legal.",
        "That piece can't reach that square.",
        "Not a valid move. Think again!",
        "That doesn't work. Pick another move.",
        "The rules don't allow that move.",
        "That's not in the rulebook!",
        "Can't make that move. Try another.",
        "That piece doesn't move like that.",
        "Invalid move! Give it another go.",
        "Not quite. That move's illegal.",
        "Rules say no to that one.",
        "That move won't work. Try again!",
        "Sorry, that's not a legal option."
    )

    // === CASTLING (player castles) ===
    val castling = listOf(
        "Castling! Nice and safe.",
        "Smart move - getting the king to safety.",
        "Castling! Good defensive play.",
        "Tucking the king away. Wise choice.",
        "Castled! The king is secure.",
        "Good idea to castle here.",
        "Castling! A strong positional move.",
        "King to safety! Well timed.",
        "Castled. Now let's get to work.",
        "Smart castling. The king is protected.",
        "Castling! Connecting the rooks too.",
        "The king retreats to safety. Good call.",
        "Castled! That's textbook play.",
        "Getting the king out of the centre. Nice.",
        "Castling! A solid choice.",
        "King safety secured. Let's play!",
        "Castled! The fortress is built.",
        "Good timing on the castle.",
        "The king finds shelter. Well played.",
        "Castling! Time to launch an attack."
    )

    // === CASTLING (computer castles) ===
    val castlingByComputer = listOf(
        "I'm castling. Safety first!",
        "I'll tuck my king away. Castle!",
        "Castling! My king is secure now.",
        "I'm getting my king to safety.",
        "I'll castle here. Seems like the right time.",
        "My king finds shelter. Castled!",
        "I'm castling. Time to connect my rooks.",
        "Castle! My king is out of harm's way.",
        "I'm securing my king. Good old castling.",
        "Castling for me. King safety achieved.",
        "I'll take my king to a safer square.",
        "My king retreats. Castle!",
        "I'm castling! The fortress goes up.",
        "Time for me to castle. King is protected.",
        "Castling! Now my king can relax.",
        "I'll castle and get organised.",
        "My king heads to safety. Castled!",
        "I'm building my fortress. Castle!",
        "Castling! My king thanks me.",
        "I'll get my king out of the centre. Castle!"
    )

    // === PROMOTION (player promotes) ===
    val promotion = listOf(
        "Pawn promoted to {piece}! Big moment.",
        "A new {piece} joins the board!",
        "Promotion! That pawn becomes a {piece}.",
        "The pawn transforms into a {piece}!",
        "Promoted to {piece}! That's powerful.",
        "A {piece} is born! Great pawn advance.",
        "Promotion to {piece}! Game changer.",
        "That pawn earned its stripes. Now it's a {piece}!",
        "New {piece} on the board! That's huge.",
        "Promoted! A fresh {piece} enters the fight.",
        "The humble pawn becomes a mighty {piece}!",
        "Promotion to {piece}! The balance shifts.",
        "A {piece}! That promotion is massive.",
        "Pawn to {piece}! Brilliant advancement.",
        "The pawn's journey ends as a {piece}!",
        "Promoted to {piece}! What a transformation.",
        "A new {piece}! That pawn went the distance.",
        "Promotion! Welcome to the board, {piece}.",
        "That pawn just became a {piece}. Watch out!",
        "From pawn to {piece}! An incredible journey."
    )

    // === PROMOTION (computer promotes) ===
    val promotionByComputer = listOf(
        "I'm promoting my pawn to a {piece}!",
        "My pawn becomes a {piece}. Excellent!",
        "Promotion! I've got a new {piece}.",
        "I'll take a {piece} please. Promoted!",
        "My pawn made it! A new {piece} for me.",
        "I'm promoting to {piece}. This changes things.",
        "A shiny new {piece} for my army!",
        "My pawn transforms into a {piece}!",
        "Promotion! My {piece} enters the battle.",
        "I'll upgrade to a {piece}. Thank you!",
        "My pawn has earned a promotion to {piece}.",
        "A {piece}! My pawn's long march pays off.",
        "I'm getting a {piece}. Promotion!",
        "My humble pawn becomes a mighty {piece}!",
        "Promoted to {piece}! I'm gaining strength.",
        "New {piece} incoming! My pawn made it.",
        "I promote to {piece}. The tables are turning.",
        "My pawn's journey ends as a {piece}!",
        "A fresh {piece} for me. Promoted!",
        "I'll take a {piece}. My pawn deserved it."
    )

    // === MOVE UNDONE ===
    val moveUndone = listOf(
        "Move taken back.",
        "Undo! Let's try that again.",
        "Taking that one back? Fair enough.",
        "Move reversed. What's your new plan?",
        "Second thoughts? Take-back accepted.",
        "Undone! Have another go.",
        "Fair enough, let's rewind that.",
        "Move undone. Try something different.",
        "Take-back! No judgement here.",
        "Rewinding. What will you try instead?",
        "That move is history. Try again!",
        "Undone! Fresh start on this turn.",
        "Changed your mind? That's fine.",
        "Move taken back. Think it through this time!",
        "Undo accepted. New strategy?",
        "Let's pretend that didn't happen.",
        "Take-back! We all reconsider sometimes.",
        "Undone. What's the new plan?",
        "Reversed! Try a different approach.",
        "Move undone. Choose wisely this time!"
    )

    // === GAME END REVIEW ===
    val reviewStrongStart = listOf(
        "You started strong but made some mistakes in the endgame.",
        "Great opening play! The endgame could use some work.",
        "Your opening was solid but things fell apart later.",
        "Strong start! You need to maintain that into the endgame.",
        "Excellent opening phase. The endgame let you down.",
        "You opened well but lost focus towards the end.",
        "Great beginning, rough ending. Work on your endgame!",
        "Your opening was textbook. The endgame? Not so much.",
        "Started like a champion, finished like a beginner.",
        "The opening was yours. The endgame was mine.",
        "Promising start but you couldn't close it out.",
        "Your opening play was impressive. Keep that energy going!",
        "You came out swinging but ran out of steam.",
        "Strong opening moves! But the endgame needs practice.",
        "Great start that didn't carry through to the finish.",
        "You played the opening beautifully. Then it unravelled.",
        "Solid opening! The endgame is where to focus next.",
        "Started with confidence, ended with uncertainty.",
        "Your opening was your best phase. Build on that!",
        "Great ideas early on. The execution in the endgame faltered."
    )

    val reviewStrongFinish = listOf(
        "You started a bit rough but pulled it back in the endgame!",
        "Slow start but what a finish!",
        "The opening was shaky but you found your feet later.",
        "Rocky start but you finished like a pro!",
        "You turned it around in the endgame. Impressive!",
        "Not the best opening but the comeback was real.",
        "You dug yourself out of a hole. Well done!",
        "Rough beginning but a brilliant endgame.",
        "Started behind but clawed your way back. Respect!",
        "The endgame was where you shone. Great recovery!",
        "You found your rhythm late but it made the difference.",
        "Shaky start, strong finish. That's character!",
        "You were struggling early but pulled it together.",
        "The comeback was impressive! Great endgame play.",
        "From behind to brilliant. What a turnaround!",
        "You started slowly but finished with flair.",
        "Not the best opening but the endgame was excellent.",
        "You grew stronger as the game went on.",
        "Rough start but you showed real resilience.",
        "The endgame was your redemption. Well played!"
    )

    val reviewConsistent = listOf(
        "Solid play throughout. Well done!",
        "Consistent performance from start to finish.",
        "You played well across all phases. Good game!",
        "Steady and reliable play. Hard to fault.",
        "Even performance throughout. Good stuff!",
        "You maintained your level from start to finish.",
        "Consistent play. You didn't give me many chances.",
        "Well balanced game. Strong throughout.",
        "No weak phases in your play. Impressive!",
        "Solid all the way through. Well played!",
        "You played a complete game. No weak spots.",
        "Consistent quality. That's how you win.",
        "Even-keeled performance. Very mature play.",
        "You kept your level up throughout. Good game!",
        "No dips in quality. That's impressive consistency.",
        "Balanced play across all phases. Well done!",
        "You were solid throughout. Hard to beat!",
        "Steady as she goes. Consistent, quality play.",
        "No let-up in your play. Consistent and strong.",
        "From opening to endgame, you were on form."
    )

    val reviewStruggled = listOf(
        "Tough game. Keep practising, you'll get there!",
        "Not your best game but every loss teaches something.",
        "That was a hard one. Don't give up!",
        "Difficult game. Learn from the mistakes.",
        "It wasn't your day. Come back stronger!",
        "A challenging game. Focus on the basics.",
        "Rough game. But you'll improve with practice!",
        "That was tough. Try to slow down and think more.",
        "Not ideal, but every game is a lesson.",
        "Tough outing. Maybe try a different approach next time.",
        "Don't be discouraged. Chess takes time to master.",
        "A difficult game. Review your moves and learn.",
        "That was rough. But champions learn from defeats.",
        "Not your strongest showing. Keep at it!",
        "A hard lesson today. You'll be better for it.",
        "Tough game. Focus on protecting your pieces.",
        "That didn't go your way. Try again!",
        "Rough game but don't lose heart.",
        "It was a struggle. But persistence pays off!",
        "Not your day. Tomorrow's a fresh start!"
    )
}
