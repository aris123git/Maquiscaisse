package com.maquis.caisse.ui.tables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.domain.model.DiningTable
import com.maquis.caisse.domain.repository.TableRepository
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TablesViewModel @Inject constructor(
    private val tableRepository: TableRepository,
) : ViewModel() {
    val tables: StateFlow<List<DiningTable>> = tableRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(number: String, name: String, capacity: Int) = viewModelScope.launch {
        if (number.isNotBlank()) tableRepository.add(number.trim(), name.trim(), capacity)
    }
}

@Composable
fun TablesScreen(viewModel: TablesViewModel = hiltViewModel()) {
    val tables by viewModel.tables.collectAsStateWithLifecycle()
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("4") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PageHeader(title = "Tables", subtitle = "Plan de salle du maquis")
        TextPill("${tables.size} tables actives", PillTone.CYAN)

        GlassCard {
            Text("Ajouter une table", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("N°") },
                    modifier = Modifier.weight(0.6f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = capacity,
                    onValueChange = { capacity = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Places") },
                    modifier = Modifier.weight(0.7f),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        viewModel.add(number, name, capacity.toIntOrNull() ?: 4)
                        number = ""
                        name = ""
                    },
                    enabled = number.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text("Ajouter") }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tables, key = { it.id }) { table ->
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(table.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            TextPill("${table.capacity} places", PillTone.INFO)
                        }
                        TextPill(
                            table.status,
                            when (table.status.uppercase()) {
                                "LIBRE" -> PillTone.SUCCESS
                                "OCCUPEE", "OCCUPÉE" -> PillTone.WARNING
                                else -> PillTone.NEUTRAL
                            },
                        )
                    }
                }
            }
        }
    }
}
