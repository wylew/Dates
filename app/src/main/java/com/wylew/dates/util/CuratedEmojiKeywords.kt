package com.wylew.dates.util

/**
 * Extra search keywords for common event emojis ("anniversary" finds hearts, "deadline" finds the
 * alarm clock), merged into every library on top of the Unicode names and keywords.
 */
object CuratedEmojiKeywords {
    val byEmoji: Map<String, List<String>> = mapOf(
        "🎂" to listOf("birthday", "cake", "party", "celebration", "sweet", "candle", "age"),
        "🎉" to listOf("party", "popper", "confetti", "celebration", "cheer", "event", "new year"),
        "🥳" to listOf("party", "face", "hat", "horn", "celebration", "happy", "joy"),
        "🎈" to listOf("balloon", "party", "celebration", "red", "decor"),
        "🎁" to listOf("gift", "present", "box", "birthday", "christmas", "surprise"),
        "🎄" to listOf("christmas", "tree", "holiday", "xmas", "winter", "december"),
        "🎆" to listOf("fireworks", "sparks", "celebration", "july 4", "new year"),
        "🥂" to listOf("cheers", "champagne", "toast", "drink", "wedding", "party"),
        "💍" to listOf("ring", "diamond", "wedding", "marriage", "engagement", "love"),
        "🎃" to listOf("halloween", "pumpkin", "spooky", "october"),
        "💻" to listOf("laptop", "computer", "tech", "work", "coding", "sync", "office"),
        "🤝" to listOf("meeting", "handshake", "deal", "business", "sync", "agreement"),
        "🏠" to listOf("house", "home", "rent", "mortgage", "living", "real estate"),
        "🎓" to listOf("graduation", "school", "degree", "college", "education", "diploma"),
        "🏆" to listOf("trophy", "winner", "award", "achievement", "first", "gold"),
        "🚀" to listOf("rocket", "launch", "space", "startup", "fast", "goal"),
        "💼" to listOf("briefcase", "work", "job", "office", "business"),
        "📝" to listOf("note", "memo", "paper", "write", "task", "todo"),
        "💡" to listOf("idea", "bulb", "light", "bright", "smart", "brain"),
        "✈️" to listOf("airplane", "flight", "travel", "vacation", "trip", "holiday", "fly"),
        "🏋️" to listOf("gym", "workout", "fitness", "weights", "exercise", "lifting"),
        "🏃" to listOf("run", "runner", "marathon", "jog", "sprint", "sport"),
        "⚽" to listOf("soccer", "football", "ball", "sport", "game", "match"),
        "🏀" to listOf("basketball", "ball", "sport", "hoops", "game"),
        "🚴" to listOf("bike", "bicycle", "cycling", "ride", "sport", "outdoors"),
        "🧘" to listOf("yoga", "meditation", "relax", "zen", "wellness", "mindfulness"),
        "🏖️" to listOf("beach", "vacation", "summer", "sand", "sea", "ocean", "sun"),
        "🏔️" to listOf("mountain", "hike", "nature", "climb", "trip", "snow"),
        "🚗" to listOf("car", "drive", "roadtrip", "auto", "vehicle", "travel"),
        "🛳️" to listOf("cruise", "ship", "boat", "ocean", "travel", "sailing"),
        "🍕" to listOf("pizza", "food", "dinner", "slice", "cheese", "italian"),
        "☕" to listOf("coffee", "cafe", "espresso", "morning", "drink", "latte"),
        "🍷" to listOf("wine", "drink", "glass", "dinner", "alcohol", "night"),
        "🍣" to listOf("sushi", "japanese", "fish", "food", "dinner"),
        "🍔" to listOf("burger", "fastfood", "hamburger", "lunch", "food"),
        "🍦" to listOf("icecream", "dessert", "sweet", "summer", "treat"),
        "⏰" to listOf("alarm", "clock", "time", "deadline", "reminder", "morning"),
        "🩺" to listOf("doctor", "health", "hospital", "checkup", "medical"),
        "❤️" to listOf("heart", "love", "romance", "red", "valentine", "anniversary"),
        "⭐" to listOf("star", "favorite", "gold", "shine", "milestone", "top"),
        "🔥" to listOf("fire", "hot", "streak", "flame", "lit", "popular"),
        "💰" to listOf("money", "cash", "finance", "pay", "dollar", "wealth")
    )
}
