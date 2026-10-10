package com.midinero.control.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.midinero.control.MiDineroApp
import com.midinero.control.data.GoalContribution
import com.midinero.control.data.SavingsGoal
import com.midinero.control.domain.GoalProgress
import com.midinero.control.domain.Goals
import com.midinero.control.domain.dateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Metas de ahorro y sus aportaciones. */
class GoalsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as MiDineroApp).repository

    /** Metas con su progreso calculado. */
    val goals: StateFlow<List<GoalWithProgress>> = combine(repo.goals, repo.contributions) { goals, contributions ->
        val byGoal = contributions.groupBy { it.goalId }
        goals.map { g ->
            val list = byGoal[g.id].orEmpty()
            GoalWithProgress(
                goal = g,
                contributions = list,
                progress = Goals.progress(
                    g.targetCents,
                    g.deadlineEpochDay?.let { LocalDate.ofEpochDay(it) },
                    list.map { dateOf(it.timestamp) to it.amountCents },
                ),
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveGoal(goal: SavingsGoal) = viewModelScope.launch { repo.saveGoal(goal) }
    fun deleteGoal(goal: SavingsGoal) = viewModelScope.launch { repo.deleteGoal(goal) }
    fun contribute(goalId: Long, cents: Long, note: String = "") = viewModelScope.launch { repo.contribute(goalId, cents, note) }
    fun deleteContribution(c: GoalContribution) = viewModelScope.launch { repo.deleteContribution(c) }
}

/** Meta de ahorro con sus aportaciones y el progreso calculado. */
data class GoalWithProgress(
    val goal: SavingsGoal,
    val contributions: List<GoalContribution>,
    val progress: GoalProgress,
)

