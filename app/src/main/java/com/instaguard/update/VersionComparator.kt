package com.instaguard.update

object VersionComparator {
    fun isRemoteNewer(remote: String, local: String): Boolean {
        val remoteParts = parse(remote)
        val localParts = parse(local)
        val size = maxOf(remoteParts.size, localParts.size)

        for (i in 0 until size) {
            val remotePart = remoteParts.getOrElse(i) { 0 }
            val localPart = localParts.getOrElse(i) { 0 }
            if (remotePart > localPart) return true
            if (remotePart < localPart) return false
        }
        return false
    }

    private fun parse(value: String): List<Int> {
        return value
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .split('.')
            .map { token -> token.filter { it.isDigit() }.ifBlank { "0" }.toInt() }
    }
}
