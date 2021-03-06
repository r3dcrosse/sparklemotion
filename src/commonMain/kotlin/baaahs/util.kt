package baaahs

import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.random.Random

fun <E> List<E>.random(): E? = if (size > 0) get(Random.nextInt(size)) else null

fun <E> List<E>.random(random: Random): E? = if (size > 0) get(random.nextInt(size)) else null

fun toRadians(degrees: Float) = (degrees * PI / 180).toFloat()

suspend fun randomDelay(timeMs: Int) {
    delay(Random.nextInt(timeMs).toLong())
}

expect fun getTimeMillis(): Long
expect fun doRunBlocking(block: suspend () -> Unit)

expect fun getResource(name: String): String