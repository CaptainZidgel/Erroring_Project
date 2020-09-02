import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement
import kotlinx.coroutines.*
import kotlinx.coroutines.async
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLElement
import kotlin.js.Date

suspend fun getBigList(ids: bundleID) {
    window.fetch("https://logs.tf/api/v1/log?player=${ids.steam64}&limit=10000")
            .then { Response -> Response.text() }
            .then { Data -> parseBigList(Data) } //I believe this is what they call "callback hell"
}

//Earliest and latest - inclusive time windows for logs to include. Months indexed at 0, for some reason.
fun parseBigList(data: String, earliest: Date = Date(2000, 0, 1), latest: Date = Date(2040, 11, 1)) {
    val json = Json{ignoreUnknownKeys = true}.decodeFromString<LogList>(data)
    var dateIndexed: MutableList<Pair<String, Number>> = mutableListOf()
    GlobalScope.launch {
        val stat = "dpm"
        for (log in json.logs.slice(1..10)) {
            observeRateLimitAsync(300) {
                val date: Long = log.date.toLong() * 1000
                if (earliest.getTime() <= date && date <= latest.getTime()) {
                    window.fetch("https://logs.tf/api/v1/log/${log.id}")
                        .then { Response -> Response.text() }
                        .then { Data ->
                            val log = parseLogFromRequest(Data, log.id)
                            val stats = log.getPlayerStats(playerIDs!!)
                            dateIndexed.add(Pair(log.info.date.toString(), stats[stat]!! as Float))
                        }
                }
            }
        }
        dateIndexed.sortBy{ it.first }
        val (indexes, values) = expandingMean(dateIndexed)
        val element =
                document.getElementById("plot") as? HTMLElement ?: error("Element with id 'plot' not found on page")
        println("Plotting has infihedm akellgely")
    }

}

var playerIDs: bundleID? = null
suspend fun main() {
    val button = document.getElementById("mybutton") as HTMLButtonElement
    val input = document.getElementById("myinput") as HTMLInputElement
    button.addEventListener("click", {
        GlobalScope.launch {
            playerIDs = try {
                bundleID(input.value, 0)
            } catch (e: SteamIDException) {
                console.log("Bad format: ${input.value}")
                null
            } ?: return@launch //if playerIDs returns null, finish the event listener.
            val list = getBigList(playerIDs!!)
        }
    })
}

//https://medium.com/@nieldw/observing-rate-limits-with-coroutines-in-koltin-clients-73c7e0067d69
private suspend fun <T> observeRateLimitAsync(onePerMillis: Long, block: () -> T): Deferred<T> = withContext(Dispatchers.Default) {
    launch {
        delay(onePerMillis)
        println("Rate limit observed: 1 request per $onePerMillis ms")
    }

    async {
        block()
    }
}

//this function is very bad not good
//I mean, I'm fine the math part https://gist.github.com/CaptainZidgel/0d648936ee96854bcfcd535ee4de7a31 , that's easy and simple.
//but the whole indexing part... yikes
//Provide a list of pairs of indexes to numerical values (ideally floats)
//Returns a list of pairs of indexes to expanding mean value.
fun expandingMean (listOfPairs: List<Pair<Any, Number>>): Pair<Array<Any>, Array<Float>> {
    var indexes = mutableListOf<Any>()
    var values = mutableListOf<Float>()
    for ((index, value) in listOfPairs) {
        indexes.add(index)
        values.add(value.toFloat())
    }
    var result = mutableListOf<Float>()
    var i = 0
    for (v in values) {
        result.add(values.slice(0..i).average().toFloat())
        i += 1
    }
    return Pair(indexes.toTypedArray(), result.toTypedArray())
}
