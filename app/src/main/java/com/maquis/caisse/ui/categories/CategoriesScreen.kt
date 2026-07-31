package com.maquis.caisse.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.domain.model.Category
import com.maquis.caisse.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {
    val categories: StateFlow<List<Category>> = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) categoryRepository.add(name.trim())
    }

    fun rename(id: Long, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) categoryRepository.rename(id, name.trim())
    }

    fun deactivate(id: Long) = viewModelScope.launch {
        categoryRepository.deactivate(id)
    }
}

@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Catégories", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Sélection toujours via liste déroulante ailleurs dans l'app.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Nouvelle catégorie") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Button(
                onClick = {
                    viewModel.add(newName)
                    newName = ""
                },
                enabled = newName.isNotBlank(),
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text("Ajouter") }
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(categories, key = { it.id }) { cat ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        cat.name + if (!cat.isActive) " (inactive)" else "",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (cat.isActive) {
                        TextButton(onClick = { viewModel.deactivate(cat.id) }) {
                            Text("Désactiver")
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
