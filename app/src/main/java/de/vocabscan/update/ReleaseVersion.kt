package de.vocabscan.update

import java.math.BigInteger

/** Compares release tags numerically, including SemVer prerelease identifiers. */
object ReleaseVersion {
    private val pattern = Regex("^[vV]?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")

    fun compare(left: String, right: String): Int? {
        val a = pattern.matchEntire(left.trim()) ?: return null
        val b = pattern.matchEntire(right.trim()) ?: return null
        val aNumbers = a.groupValues[1].split('.').map { it.toBigInteger() }
        val bNumbers = b.groupValues[1].split('.').map { it.toBigInteger() }
        for (index in 0 until maxOf(aNumbers.size, bNumbers.size)) {
            val difference = (aNumbers.getOrNull(index) ?: BigInteger.ZERO)
                .compareTo(bNumbers.getOrNull(index) ?: BigInteger.ZERO)
            if (difference != 0) return difference
        }
        val aPre = a.groupValues[2]
        val bPre = b.groupValues[2]
        if (aPre.isEmpty() || bPre.isEmpty()) return when {
            aPre == bPre -> 0
            aPre.isEmpty() -> 1
            else -> -1
        }
        val aParts = aPre.split('.')
        val bParts = bPre.split('.')
        for (index in 0 until minOf(aParts.size, bParts.size)) {
            val x = aParts[index]
            val y = bParts[index]
            val xNumber = x.toBigIntegerOrNull()
            val yNumber = y.toBigIntegerOrNull()
            val difference = when {
                xNumber != null && yNumber != null -> xNumber.compareTo(yNumber)
                xNumber != null -> -1
                yNumber != null -> 1
                else -> x.compareTo(y)
            }
            if (difference != 0) return difference
        }
        return aParts.size.compareTo(bParts.size)
    }
}
