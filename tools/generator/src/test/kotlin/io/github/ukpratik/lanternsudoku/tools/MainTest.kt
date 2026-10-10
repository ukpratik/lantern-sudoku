// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MainTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val lines = mutableListOf<String>()

    @Test
    fun validateFailsWithoutABank() {
        assertThat(run(listOf("validate", tmp.root.resolve("nothing").path), lines::add)).isEqualTo(1)
        assertThat(lines.joinToString("\n")).contains("does not exist")
    }

    @Test
    fun unknownCommandPrintsUsageAndFails() {
        assertThat(run(listOf("frobnicate"), lines::add)).isEqualTo(2)
        assertThat(lines.single()).isEqualTo(USAGE)
    }

    @Test
    fun noCommandPrintsUsage() {
        assertThat(run(emptyList(), lines::add)).isEqualTo(0)
    }

    @Test
    fun badOptionsAreAUsageError() {
        assertThat(run(listOf("generate", tmp.root.path, "--only", "newgame-nope"), lines::add)).isEqualTo(2)
    }

    @Test
    fun generateAppendsAndTheResultValidatesExceptForShortDailies() {
        val dir = tmp.newFolder("bank")
        val counts = listOf("--newgame", "2") + listOf("daily-easy", "daily-medium", "daily-hard", "daily-expert").flatMap { listOf("--count", "$it=1") }
        assertThat(run(listOf("generate", dir.path) + counts, lines::add)).isEqualTo(0)
        // Appending more keeps the first records byte-identical.
        val first = dir.resolve("newgame-hard.txt").readBytes()
        assertThat(run(listOf("generate", dir.path, "--newgame", "3", "--only", "newgame-hard"), lines::add)).isEqualTo(0)
        assertThat(dir.resolve("newgame-hard.txt").readBytes().copyOf(first.size)).isEqualTo(first)
        lines.clear()
        assertThat(run(listOf("validate", dir.path), lines::add)).isEqualTo(1)
        val failures = lines.filter { it.trim().startsWith("FAIL") }
        assertThat(failures).hasSize(4)
        assertThat(failures.all { it.contains("the daily mapping needs") }).isTrue()
    }
}
