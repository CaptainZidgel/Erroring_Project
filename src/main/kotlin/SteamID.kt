import kotlin.math.floor

class SteamIDException(f: String): Exception("Bad format. Should be a Steam64 (76561198098770013), Steam3 ([U:1:138504285]) or Steam1(STEAM_0:1:69252142). You provided $f")

//Adopted from https://github.com/12pt/steamid-converter/blob/master/js/converter.js
//Further reading (that I didn't find very helpful) https://developer.valvesoftware.com/wiki/SteamID
//Other module I tried to copy from but couldn't comprehend https://github.com/ValvePython/steam/blob/master/steam/steamid.py
const val BASE = 76561197960265728L

//3 or 64 to 1
//Universe is 0 for orange box games
fun toSteam1(id: String, universe: Int = 1): String {
    return if (id[0] == '7') {
        val Lid: Long = id.toLong()
        val rem = Lid.rem(2)
        val int = Lid - rem - BASE
        "STEAM_" + universe + ":" + rem + ":" + int / 2 // "STEAM_X:Y:Z"
    } else if (id[0] == '[') {
        val split = id.split(":")
        val last = split[2].substring(0, split[2].length - 1)
        "STEAM_" + universe + ":" + last.toInt() % 2 + ":" + floor(last.toDouble() / 2)
    } else if (id[0] == 'S') {
        id
    } else {
        throw SteamIDException(id)
    }
}

//64 or 1 to 3
fun toSteam3(id: String, uni: Int = 1): String {
    return if (id[0] == '[') {
        id
    } else if (id[0] == 'S' || id[0] == '7') {
        val _id: String = if (id[0] == '7') toSteam1(id, uni) else id
        val split = _id.split(":")
        "[U:1:" + (1 + split[2].toInt() * 2) + "]"
    } else {
        throw SteamIDException(id)
    }
}

//1 or 3 to 64
fun toSteam64(S: String): String {
    return if (S[0] == '7') {
        S
    } else if (S[0] == 'S' || S[0] == '[') {
        var _id: String = if (S[0] == '[') toSteam1(S) else S
        val split = _id.split(":")
        (BASE + split[2].toInt() * 2 + split[1].toInt()).toString()
    } else {
        throw SteamIDException(S)
    }
}

//provide any single format, create a class of values
//my use of private setters and try/catch inside init is overcomplicated but I wanted to keep this class "safe"
data class bundleID(
    val starter: String,
    val universe: Int = 1
) {
    var steam64: String = ""
        private set
    var steam3: String = ""
        private set
    var steam1: String = ""
        private set
    init {
        try {
            steam64 = toSteam64(starter)
            steam1 = toSteam1(starter, universe)
            steam3 = toSteam3(starter, universe)
        } catch (e: Exception) {
            throw SteamIDException(starter)
        }
    }

    fun swapUni(): String {
        return when(universe) {
            1 -> steam1.replaceFirst('1', '0')
            0 -> steam1.replaceFirst('0', '1')
            else -> throw SteamIDException("$steam1 : Is this a user universe?")
        }
    }
}

//Each output of x from y should match canon.
fun __test() {
    val s3Canon = "[U:1:138504285]"
    val s64Canon = "76561198098770013"
    val s1Canon = "STEAM_0:1:69252142"
    println("1 from 64: ${toSteam1(s64Canon, 0)} 1 from 3: ${toSteam1(s3Canon, 0)}, canon: ${s1Canon}")
    println("3 from 64: ${toSteam3(s64Canon)}, 3 from 1: ${toSteam3(s1Canon)}, canon: ${s3Canon}")
    println("64 from 1: ${toSteam64(s1Canon)}, 64 from 3: ${toSteam64(s3Canon)}, canon: ${s64Canon}")
    println("${bundleID(s64Canon)}")
    println("${bundleID(s1Canon)}")
    println("${bundleID(s3Canon)}")
    val x = bundleID(s1Canon)
    println("Swap: Original: ${s1Canon} to other: ${x.swapUni()}")
}