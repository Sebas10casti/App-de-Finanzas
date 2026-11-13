package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SavingsPocket
import com.example.ui.components.PocketVisuals
import com.example.ui.viewmodel.SavingsUiSummary
import com.example.ui.viewmodel.SavingsViewModel
import kotlinx.coroutines.launch

@Composable
fun SavingsScreen(
    viewModel: SavingsViewModel,
    onOpenSettings: (() -> Unit)? = null
) {
    val pockets by viewModel.pockets.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val selectedPocket by viewModel.selectedPocket.collectAsState()
    val movements by viewModel.selectedPocketMovements.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val feedbackMessage by viewModel.feedbackMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showSelectPocketToInvite by remember { mutableStateOf(false) }
    var invitePocketId by remember { mutableStateOf<String?>(null) }

    val currentUserId = viewModel.currentUserId()
    val ownedPockets = remember(pockets, currentUserId) {
        pockets.filter { it.ownerId == currentUserId }
    }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(errorMessage) {
        if (!showJoinDialog && errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage!!)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val activePocket = selectedPocket
            if (activePocket != null) {
                PocketDetailScreen(
                    pocket = activePocket,
                    movements = movements,
                    currentUserId = viewModel.currentUserId(),
                    isProcessing = isProcessing,
                    viewModel = viewModel,
                    onBack = { viewModel.selectPocket(null) },
                    onCopiedCode = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Código copiado al portapapeles")
                        }
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Ahorros & Bolsillos",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Bolsillos independientes y metas compartidas con tu pareja",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (onOpenSettings != null) {
                                IconButton(
                                    onClick = onOpenSettings,
                                    modifier = Modifier.testTag("btn_open_settings_shortcut")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Abrir Ajustes"
                                    )
                                }
                            }
                        }
                    }

                    item {
                        SavingsSummaryCard(
                            summary = summary,
                            hasOwnedPockets = ownedPockets.isNotEmpty(),
                            formatCurrency = viewModel::formatCurrency,
                            onCreateClick = { showCreateDialog = true },
                            onInviteClick = {
                                when {
                                    ownedPockets.isEmpty() -> {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Crea primero un bolsillo para poder invitar a alguien.")
                                        }
                                    }
                                    ownedPockets.size == 1 -> {
                                        invitePocketId = ownedPockets.first().id
                                    }
                                    else -> {
                                        showSelectPocketToInvite = true
                                    }
                                }
                            },
                            onJoinClick = {
                                viewModel.clearMessages()
                                showJoinDialog = true
                            }
                        )
                    }

                    if (pockets.isEmpty()) {
                        item {
                            EmptySavingsCard(
                                onCreateClick = { showCreateDialog = true },
                                onJoinClick = {
                                    viewModel.clearMessages()
                                    showJoinDialog = true
                                }
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "Tus bolsillos (${pockets.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        items(pockets, key = { it.id }) { pocket ->
                            PocketListCard(
                                pocket = pocket,
                                isOwner = pocket.ownerId == currentUserId,
                                formatCurrency = viewModel::formatCurrency,
                                onClick = { viewModel.selectPocket(pocket.id) },
                                onInviteClick = { invitePocketId = pocket.id }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateOrEditPocketDialog(
            initialPocket = null,
            isProcessing = isProcessing,
            onDismiss = { showCreateDialog = false },
            onSave = { name, initialAmount, targetAmount, iconKey, colorKey ->
                viewModel.createPocket(
                    name = name,
                    initialAmount = initialAmount,
                    targetAmount = targetAmount,
                    iconKey = iconKey,
                    colorKey = colorKey
                ) {
                    showCreateDialog = false
                }
            }
        )
    }

    if (showJoinDialog) {
        JoinPocketByCodeDialog(
            isProcessing = isProcessing,
            errorMessage = errorMessage,
            onDismiss = {
                viewModel.clearMessages()
                showJoinDialog = false
            },
            onJoin = { code ->
                viewModel.joinPocketByCode(code) {
                    showJoinDialog = false
                }
            }
        )
    }

    if (showSelectPocketToInvite) {
        SelectPocketToInviteDialog(
            ownedPockets = ownedPockets,
            formatCurrency = viewModel::formatCurrency,
            onSelectPocket = { chosen ->
                showSelectPocketToInvite = false
                invitePocketId = chosen.id
            },
            onDismiss = { showSelectPocketToInvite = false }
        )
    }

    val activeInvitePocket = invitePocketId?.let { id -> pockets.firstOrNull { it.id == id } }
    if (activeInvitePocket != null) {
        SharePocketInviteDialog(
            pocket = activeInvitePocket,
            isProcessing = isProcessing,
            onGenerateCode = { viewModel.generateOrRegenerateShareCode(activeInvitePocket) },
            onDeactivateCode = { viewModel.deactivateShareCode(activeInvitePocket) },
            onCopiedCode = {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Código copiado al portapapeles")
                }
            },
            onDismiss = { invitePocketId = null }
        )
    }
}

@Composable
private fun SavingsSummaryCard(
    summary: SavingsUiSummary,
    hasOwnedPockets: Boolean,
    formatCurrency: (Long) -> String,
    onCreateClick: () -> Unit,
    onInviteClick: () -> Unit,
    onJoinClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "TOTAL AHORRADO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.9.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = formatCurrency(summary.totalSaved),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("text_total_savings_amount")
                )

                AnimatedVisibility(visible = summary.pocketCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SummaryStatPill(
                            icon = Icons.Default.Person,
                            label = "Personales (${summary.pocketCount - summary.sharedCount})",
                            amount = formatCurrency(summary.personalSaved),
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatPill(
                            icon = Icons.Default.Group,
                            label = "Compartidos (${summary.sharedCount})",
                            amount = formatCurrency(summary.sharedSaved),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                var shareMenuExpanded by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onCreateClick,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_new_pocket"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nuevo bolsillo", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Box {
                        FilledTonalButton(
                            onClick = { shareMenuExpanded = true },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_share_pocket_menu"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir", maxLines = 1)
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Opciones para compartir",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = shareMenuExpanded,
                            onDismissRequest = { shareMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Invitar a un bolsillo")
                                        if (!hasOwnedPockets) {
                                            Text(
                                                text = "Crea un bolsillo primero",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                                },
                                enabled = hasOwnedPockets,
                                onClick = {
                                    shareMenuExpanded = false
                                    onInviteClick()
                                },
                                modifier = Modifier.testTag("menu_item_invite_pocket")
                            )
                            DropdownMenuItem(
                                text = { Text("Unirse con código") },
                                leadingIcon = {
                                    Icon(Icons.Default.Key, contentDescription = null)
                                },
                                onClick = {
                                    shareMenuExpanded = false
                                    onJoinClick()
                                },
                                modifier = Modifier.testTag("menu_item_join_pocket")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatPill(
    icon: ImageVector,
    label: String,
    amount: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = amount,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PocketListCard(
    pocket: SavingsPocket,
    isOwner: Boolean,
    formatCurrency: (Long) -> String,
    onClick: () -> Unit,
    onInviteClick: () -> Unit
) {
    val accentColor = PocketVisuals.getColor(pocket.colorKey)
    val icon = PocketVisuals.getIcon(pocket.iconKey)
    val isShared = pocket.memberUids.size > 1

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("pocket_card_${pocket.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pocket.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isShared) Icons.Default.Group else Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isShared) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isShared) "Compartido (${pocket.memberUids.size})" else "Personal",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isShared) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatCurrency(pocket.currentAmount),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = accentColor
                )
            }

            val target = pocket.targetAmount?.takeIf { it > 0L }
            if (target != null) {
                val fraction = (pocket.currentAmount.toFloat() / target.toFloat()).coerceIn(0f, 1f)
                val pct = ((pocket.currentAmount.toDouble() / target.toDouble()) * 100.0).toInt().coerceAtMost(999)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = accentColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Meta: ${formatCurrency(target)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                    }
                }
            }

            if (isOwner) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeCode = pocket.shareCode?.takeIf { it.isNotBlank() }
                    val isUnexpired = pocket.shareCodeExpiresAt?.toDate()?.after(java.util.Date()) == true
                    if (activeCode != null && isUnexpired) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "Código: ${com.example.data.repository.SavingsRepository.formatDisplayCode(activeCode)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    TextButton(
                        onClick = onInviteClick,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("btn_card_invite_${pocket.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Invitar")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySavingsCard(
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Savings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                text = "Crea tu primer bolsillo de ahorro",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Organiza tus ahorros por metas, registra cada aporte o retiro y comparte bolsillos con tu pareja mediante un código temporal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCreateClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Crear bolsillo")
                }
                OutlinedButton(
                    onClick = onJoinClick,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Tengo código")
                }
            }
        }
    }
}
