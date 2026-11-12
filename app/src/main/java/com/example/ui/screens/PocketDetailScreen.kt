package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PocketMovement
import com.example.data.model.SavingsPocket
import com.example.data.repository.SavingsRepository
import com.example.ui.components.PocketVisuals
import com.example.ui.viewmodel.SavingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PocketDetailScreen(
    pocket: SavingsPocket,
    movements: List<PocketMovement>,
    currentUserId: String,
    isProcessing: Boolean,
    viewModel: SavingsViewModel,
    onBack: () -> Unit,
    onCopiedCode: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isOwner = pocket.ownerId == currentUserId
    val accentColor = PocketVisuals.getColor(pocket.colorKey)
    val icon = PocketVisuals.getIcon(pocket.iconKey)

    var showEditDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var activeMovementType by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<Pair<String, String>?>(null) }

    val dateFormatter = remember {
        SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale("es", "CO"))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header with Back, Title, Edit, and Delete/Leave
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_back_from_pocket")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver a bolsillos"
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pocket.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (pocket.memberUids.size > 1) {
                            "Bolsillo compartido • ${pocket.memberUids.size} personas"
                        } else {
                            "Bolsillo personal"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isOwner) {
                    IconButton(
                        onClick = { showInviteDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_header_invite_pocket")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Invitar a alguien",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_edit_pocket")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar bolsillo",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (isOwner) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_delete_pocket")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar bolsillo",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Balance Hero & Quick Actions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(accentColor.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SALDO ACTUAL",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = viewModel.formatCurrency(pocket.currentAmount),
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = accentColor
                            )
                        }
                    }

                    val target = pocket.targetAmount?.takeIf { it > 0L }
                    if (target != null) {
                        val fraction = (pocket.currentAmount.toFloat() / target.toFloat()).coerceIn(0f, 1f)
                        val pct = ((pocket.currentAmount.toDouble() / target.toDouble()) * 100.0).toInt().coerceAtMost(999)
                        val remaining = (target - pocket.currentAmount).coerceAtLeast(0L)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Meta: ${viewModel.formatCurrency(target)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = accentColor
                                )
                            }
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = accentColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round
                            )
                            Text(
                                text = if (remaining == 0L) {
                                    "¡Meta alcanzada!"
                                } else {
                                    "Te faltan ${viewModel.formatCurrency(remaining)} para cumplir tu meta"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { activeMovementType = PocketMovement.TYPE_DEPOSIT },
                            enabled = !isProcessing,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_deposit_pocket"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Aportar")
                        }

                        OutlinedButton(
                            onClick = { activeMovementType = PocketMovement.TYPE_WITHDRAW },
                            enabled = !isProcessing && pocket.currentAmount > 0L,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_withdraw_pocket"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retirar")
                        }
                    }
                }
            }
        }

        // Sharing & Participants Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Compartir & Participantes",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Participant pills
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        pocket.memberUids.forEach { uid ->
                            val memberName = pocket.memberNames[uid]?.takeIf { it.isNotBlank() } ?: "Usuario"
                            val roleLabel = if (uid == pocket.ownerId) "Creador" else "Pareja / Invitado"
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (uid == currentUserId) "$memberName (Tú)" else memberName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = roleLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (isOwner && uid != currentUserId) {
                                        IconButton(
                                            onClick = { memberToRemove = uid to memberName },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("btn_remove_member_$uid")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PersonRemove,
                                                contentDescription = "Quitar participante",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isOwner) {
                        val activeCode = pocket.shareCode?.takeIf { it.isNotBlank() }
                        val expiresDate = pocket.shareCodeExpiresAt?.toDate()
                        val isExpired = expiresDate == null || !expiresDate.after(Date())

                        if (activeCode == null) {
                            Button(
                                onClick = { viewModel.generateOrRegenerateShareCode(pocket) },
                                enabled = !isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_generate_share_code"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Compartir con código temporal")
                            }
                        } else {
                            val formattedCode = SavingsRepository.formatDisplayCode(activeCode)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Key,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = formattedCode,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = 1.5.sp
                                                ),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.testTag("text_pocket_share_code")
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(formattedCode))
                                                onCopiedCode()
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("btn_copy_share_code")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copiar código",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Text(
                                        text = if (isExpired) {
                                            "Este código venció. Regenera uno nuevo para invitar."
                                        } else {
                                            "Válido hasta: ${dateFormatter.format(expiresDate!!)}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isExpired) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (!isExpired) {
                                        Button(
                                            onClick = {
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(
                                                        Intent.EXTRA_TEXT,
                                                        "¡Te invito a nuestro bolsillo '${pocket.name}' en Mi Presupuesto! Entra a Ahorros > Unirse e ingresa el código: $formattedCode"
                                                    )
                                                }
                                                context.startActivity(
                                                    Intent.createChooser(sendIntent, "Enviar código de invitación")
                                                )
                                            },
                                            enabled = !isProcessing,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("btn_send_share_invite"),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Enviar invitación")
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.generateOrRegenerateShareCode(pocket) },
                                            enabled = !isProcessing,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("btn_regenerate_share_code")
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Regenerar")
                                        }

                                        TextButton(
                                            onClick = { viewModel.deactivateShareCode(pocket) },
                                            enabled = !isProcessing,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("btn_deactivate_share_code")
                                        ) {
                                            Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Desactivar")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showLeaveConfirm = true },
                            enabled = !isProcessing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("btn_leave_pocket")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salirme de este bolsillo")
                        }
                    }
                }
            }
        }

        // Movements Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Historial de movimientos (${movements.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        if (movements.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "Aún no hay movimientos registrados. Usa Aportar o Retirar para empezar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(movements, key = { it.id }) { mov ->
                MovementRowCard(
                    movement = mov,
                    formattedAmount = viewModel.formatCurrency(mov.amount),
                    formattedBalanceAfter = viewModel.formatCurrency(mov.balanceAfter),
                    formattedDate = mov.createdAt?.toDate()?.let { dateFormatter.format(it) } ?: "Reciente"
                )
            }
        }
    }

    if (showEditDialog) {
        CreateOrEditPocketDialog(
            initialPocket = pocket,
            isProcessing = isProcessing,
            onDismiss = { showEditDialog = false },
            onSave = { name, _, target, iconKey, colorKey ->
                viewModel.updatePocketDetails(pocket.id, name, target, iconKey, colorKey) {
                    showEditDialog = false
                }
            }
        )
    }

    if (showInviteDialog) {
        SharePocketInviteDialog(
            pocket = pocket,
            isProcessing = isProcessing,
            onGenerateCode = { viewModel.generateOrRegenerateShareCode(pocket) },
            onDeactivateCode = { viewModel.deactivateShareCode(pocket) },
            onCopiedCode = onCopiedCode,
            onDismiss = { showInviteDialog = false }
        )
    }

    activeMovementType?.let { type ->
        PocketMovementDialog(
            pocket = pocket,
            movementType = type,
            formattedBalance = viewModel.formatCurrency(pocket.currentAmount),
            isProcessing = isProcessing,
            onDismiss = { activeMovementType = null },
            onConfirm = { amount, note ->
                viewModel.recordMovement(pocket.id, type, amount, note) {
                    activeMovementType = null
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar bolsillo") },
            text = {
                Text("¿Seguro que deseas eliminar '${pocket.name}' y todo su historial de movimientos? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deletePocket(pocket)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_delete_pocket")
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Salirse del bolsillo") },
            text = {
                Text("Dejarás de ver y editar '${pocket.name}' en tu cuenta. Podrás volver solo si te comparten un nuevo código.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirm = false
                        viewModel.leavePocket(pocket.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_leave_pocket")
                ) {
                    Text("Salirme")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    memberToRemove?.let { (uid, name) ->
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text("Quitar participante") },
            text = {
                Text("¿Deseas quitar a $name de '${pocket.name}'? Perderá el acceso en su dispositivo.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        memberToRemove = null
                        viewModel.removeMember(pocket, uid)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Quitar")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun MovementRowCard(
    movement: PocketMovement,
    formattedAmount: String,
    formattedBalanceAfter: String,
    formattedDate: String
) {
    val isWithdraw = movement.type == PocketMovement.TYPE_WITHDRAW
    val amountColor = if (isWithdraw) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    val prefix = if (isWithdraw) "-" else "+"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(amountColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isWithdraw) Icons.Default.Remove else Icons.Default.Add,
                        contentDescription = null,
                        tint = amountColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = movement.note?.takeIf { it.isNotBlank() }
                            ?: when (movement.type) {
                                PocketMovement.TYPE_INITIAL -> "Saldo inicial"
                                PocketMovement.TYPE_WITHDRAW -> "Retiro"
                                else -> "Aporte"
                            },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${movement.userName} • $formattedDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$prefix$formattedAmount",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = amountColor
                )
                Text(
                    text = "Saldo: $formattedBalanceAfter",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
