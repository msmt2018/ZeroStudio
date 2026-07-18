package android.zero.studio.settings.presentation.page.changelog.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import android.zero.studio.settings.domain.model.ChangelogItem
import android.zero.studio.settings.domain.usecase.GetAllChangelogsUseCase
import kotlinx.coroutines.launch
import javax.inject.Inject


@Stable
@HiltViewModel
class ChangelogViewModel @Inject constructor(
    private val getAllChangelogsUseCase: GetAllChangelogsUseCase
) : ViewModel() {

    private val _changelogs = mutableStateOf<List<ChangelogItem>>(emptyList())
    val changelogs: State<List<ChangelogItem>> = _changelogs

    init {
        viewModelScope.launch {
            _changelogs.value = getAllChangelogsUseCase()
        }
    }
}