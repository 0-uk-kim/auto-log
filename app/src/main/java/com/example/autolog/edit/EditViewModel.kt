package com.example.autolog.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.autolog.data.clip.ClipRepository
import com.example.autolog.navigation.Edit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class EditViewModel @Inject constructor(
    private val clipRepository: ClipRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Edit>()
    val date: LocalDate = LocalDate.parse(route.date)

    private val _uiState = MutableStateFlow<EditUiState>(EditUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val clip = clipRepository.clipsOn(date).firstOrNull { it.id == route.clipId }
            _uiState.value = clip?.let(EditUiState::Ready) ?: EditUiState.Missing
        }
    }
}
