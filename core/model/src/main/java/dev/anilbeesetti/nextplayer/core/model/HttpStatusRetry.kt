package dev.anilbeesetti.nextplayer.core.model

private const val HTTP_STATUS_MIN = 100
private const val HTTP_STATUS_MAX = 599
private val STATUS_CODE_SEPARATOR = Regex("[,\\s]+")

fun parseRetryHttpStatusCodes(value: String): List<Int> {
    return value
        .split(STATUS_CODE_SEPARATOR)
        .mapNotNull { it.toIntOrNull() }
        .filter { it in HTTP_STATUS_MIN..HTTP_STATUS_MAX }
        .distinct()
        .sorted()
}

fun formatRetryHttpStatusCodes(codes: Collection<Int>): String {
    return codes
        .filter { it in HTTP_STATUS_MIN..HTTP_STATUS_MAX }
        .distinct()
        .sorted()
        .joinToString(separator = ", ")
}
