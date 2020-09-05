import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

//These are mutable public variables in case you want to modify them for special processing. Perhaps you want to include whacky old 6s koth maps like coalplant.
public var sixes_koth = setOf("product", "clearcut", "bagel")
public var not_sixes_modes = setOf("pl", "ultiduo", "bball")

private val offclasses = setOf("heavyweapons", "engineer", "sniper", "spy", "pyro")

//The response from http://logs.tf/api/v1/log/{id}
//Nonexhaustive. Some useless information included, some excluded.
@Serializable
class Log(
    val version: Int,
    val success: Boolean,
    val info: Info,
    val players: HashMap<String, Player>,
    val teams: HashMap<String, Team>
) {
    @Serializable
    data class Team(
        val score: Int,
        val charges: Int,
        val drops: Int,
        val firstcaps: Int
    )

    @Serializable
    data class Player(
        val team: String?,
        val class_stats: List<Class_stats>,
        val kills: Int,
        val deaths: Int,
        val assists: Int,
        val kapd: String,
        val kpd: String,
        val dmg: Int,
        val hr: Int,
        @SerialName("as")  // as is a reserved word in Kotlin
        val AS: Int,
        @SerialName("dapm") // Logs.tf records _da_mage per minute as dapm
        val dpm: Int,
        val ubers: Int,
        val drops: Int,
        val headshots: Int,
        val heal: Int,
        val cpc: Int
    )
    @Serializable
    data class Class_stats(
        val type: String,
        val kills: Int,
        val assists: Int,
        val deaths: Int,
        val dmg: Int,
        val total_time: Int
    )
    @Serializable
    data class Info(
        val map: String,
        val total_length: Int,
        val hasRealDamage: Boolean,
        val hasWeaponDamage: Boolean,
        val hasAccuracy: Boolean,
        val hasHP: Boolean,
        val hasHP_real: Boolean,
        val hasAS: Boolean,
        val date: Long
    )
    var id: Int? = null //starts null because log jsons dont actually carry this information. We will set it later.

    fun hasGoodInfo(l: List<Boolean> = listOf(this.info.hasRealDamage)): Boolean {
        return !l.any{ !it }
    }

    fun gameLength(p: Int): Boolean {
        return this.info.total_length > p
    }

    fun findFullTimeOffclass(threshold: Int = 1): Boolean {
        var oc = 0
        for (player in this.players.values) {
            var total = 0
            for (c in player.class_stats) {
                if (c.type in offclasses) {
                    total += c.total_time
                }
            }
            if ((player.class_stats.size == 1 && player.class_stats[0].type in offclasses) || total == this.info.total_length) {
                oc += 1
            }
        }
        return oc >= threshold
    }

    //Returns a pair of an int and a descriptor string. Int: -1 for errant log, 0 for not 6s, 1 for 6s
    fun isSixes(): Pair<Int, String> {
        if (this.info.map == "") {
            return Pair(-1, "No map name (likely a combined log) @ log ${this.id}")
        }
        try {
            val mode = this.info.map.split("_")[0]  //tried to do this with regex. Failed :)
            val map = this.info.map.split("_")[1]
            return when {
                mode in not_sixes_modes -> Pair(0, mode)
                this.players.size == 12 && (mode == "cp" || map in sixes_koth) -> Pair(1, "Ideal Conditions for 6s")
                mode == "koth" && map !in sixes_koth -> Pair(0, "Map is koth but not a map played in 6s")
                this.players.size <= 11 -> Pair(0, "Less than 12 players, might be Funny Mode (4s, Ultiduo, Bball)")
                this.players.size >= 17 -> Pair(0, "Highlander")
                findFullTimeOffclass() -> Pair(0, "Someone was offclassing for the entire game... likely Prolander/Highlander")
                else -> Pair(1, "Couldn't find anything clearly wrong with game. Likely 6s")
            }
        }
        catch (e: Exception) {
            return Pair(-1, "Unexpected Error: $e @ log ${this.id}")
        }
    }

    fun evalWin(team: String?): Boolean {
        return when {
            this.teams["Red"]!!.score > this.teams["Blue"]!!.score && team == "Red" -> true
            this.teams["Blue"]!!.score > this.teams["Red"]!!.score && team == "Blue" -> true
            else -> false //while you might think the first statement would be exhaustive, we need the second and third statement to exclude ties.
        }
    }

    fun getPlayerStats(id: bundleID): HashMap<String, Any> {
        //           this.players is a hashmap.          this array is player IDs         .first returns the first element that matches the lambda predicate
        val player = this.players[ arrayOf(id.steam64, id.steam3, id.steam1, id.swapUni()).first{ it in this.players } ]!!  //!! asserts this statement isn't null
        val stats = hashMapOf<String, Any>(
            "dpm" to player.dpm,
            "as" to player.AS,
            "kpd" to player.kpd,
            "isWin" to if (evalWin(player.team)) 1 else 0
        )
        return stats
    }

}

//The response from http://logs.tf/api/v1/log?{parameters}
@Serializable
data class LogList(
    val success: Boolean,
    val results: Int,
    val total: Int,
    val logs: Array<Info>
) {
    @Serializable
    data class Info(
        val id: Int,
        val title: String,
        val map: String,
        val date: Int,
        val players: Int
    )
}

var biglist = LogList(false,-9, -9, arrayOf())
fun getBigList(msg: String, url: String) {
    biglist = Json(JsonConfiguration(ignoreUnknownKeys = true)).parse(LogList.serializer(), msg)
}

fun parseLogFromRequest(msg: String, id: Int): Log {
    val result = Json(JsonConfiguration(ignoreUnknownKeys = true)).parse(Log.serializer(), msg)
    //result.id = Regex("log/(\\d+)").find(url)!!.groupValues[1].toInt()
    result.id = id
    return result
}