// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The device's local calendar date (PRD §5 "Today's daily"), read on demand: on every ON_RESUME, tab switch, date or
 * time-zone broadcast and whenever a game starts or ends (design `state-daily-date-changed`). No alarm, no background
 * work (NFR-008). [override] is the debug-only `dbg_date` hook; release builds pass none.
 */
class Today(
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val override: () -> LocalDate? = { null },
) {
    private val _date = MutableStateFlow(read())

    /** The date as of the last [refresh]. Screens observe this; it only changes when something calls [refresh]. */
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    /** Re-reads the date and publishes it if it changed. Returns the current date. */
    fun refresh(): LocalDate = read().also { _date.value = it }

    private fun read(): LocalDate = override() ?: LocalDate.now(clock.withZone(zone()))
}
