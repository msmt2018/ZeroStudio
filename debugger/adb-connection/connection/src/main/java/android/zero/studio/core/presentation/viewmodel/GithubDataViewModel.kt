package android.zero.studio.core.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.zero.studio.core.domain.repository.GithubDataRepository
import android.zero.studio.core.utils.isNetworkAvailable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GithubDataViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: GithubDataRepository
) : ViewModel() {

    val stats = repository.observeRepoStats()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null
        )

    init {
        refreshIfPossible()
    }

    fun refreshIfPossible() {
        viewModelScope.launch {
            if (!isNetworkAvailable(context)) return@launch
            repository.refreshRepoStats()
        }
    }
}
