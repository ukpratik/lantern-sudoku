// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.tools

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MainTest {
    private val lines = mutableListOf<String>()

    @Test
    fun validatePassesUntilTheBankExists() {
        assertThat(run(listOf("validate", "bank"), lines::add)).isEqualTo(0)
        assertThat(lines.single()).contains("OK")
    }

    @Test
    fun unimplementedCommandsFail() {
        assertThat(run(listOf("generate"), lines::add)).isEqualTo(1)
        assertThat(run(listOf("report"), lines::add)).isEqualTo(1)
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
}
