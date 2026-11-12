package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PocketMovement
import com.example.data.model.SavingsPocket
import com.example.data.repository.SavingsRepository
import com.example.ui.components.PocketVisuals
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateOrEditPocketDialog(
    initialPocket: SavingsPocket? = null,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, initialAmount: Long, targetAmount: Long?, iconKey: String, colorKey: String) -> Unit
) {
    val isEditing = initialPocket != null
    var name by remember(initialPocket) { mutableStateOf(initialPocket?.name ?: "") }
    var initialAmountText by remember(initialPocket) { mutableStateOf("") }
    var hasGoal by remember(initialPocket) { mutableStateOf((initialPocket?.targetAmount ?: 0L) > 0L) }
    var targetAmountText by remember(initialPocket) {
        mutableStateOf(initialPocket?.targetAmount?.takeIf { it > 0L }?.toString() ?: "")
    }
    var selectedIcon by remember(initialPocket) { mutableStateOf(initialPocket?.iconKey ?: "savings") }
    var selectedColor by remember(initialPocket) { mutableStateOf(initialPocket?.colorKey ?: "emerald") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Text(
                text = if (isEditing) "Editar bolsillo" else "Nuevo bolsillo de ahorro",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= 80) {
                            name = it
                            errorMsg = null
                        }
                    },
                    label = { Text("Nombre del bolsillo") },
                    placeholder = { Text("Ej: Viaje, Fondo emergencia...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_pocket_name")
                )

                if (!isEditing) {
                    OutlinedTextField(
                        value = initialAmountText,
                        onValueChange = { raw ->
                            initialAmountText = raw.filter { it.isDigit() }.take(11)
                            errorMsg = null
                        },
                        label = { Text("Saldo inicial (opcional)") },
                        placeholder = { Text("0") },
                        prefix = { Text("$ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_pocket_initial_amount")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Definir meta de ahorro",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Opcional para ver barra de progreso",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hasGoal,
                        onCheckedChange = {
                            hasGoal = it
                            errorMsg = null
                        },
                        modifier = Modifier.testTag("switch_pocket_goal")
                    )
                }

                if (hasGoal) {
                    OutlinedTextField(
                        value = targetAmountText,
                        onValueChange = { raw ->
                            targetAmountText = raw.filter { it.isDigit() }.take(11)
                            errorMsg = null
                        },
                        label = { Text("Meta de ahorro (COP)") },
                        placeholder = { Text("Ej: 3000000") },
                        prefix = { Text("$ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_pocket_target_amount")
                    )
                }

                Text(
                    text = "Ícono",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val accent = PocketVisuals.getColor(selectedColor)
                    PocketVisuals.iconChoices.forEach { choice ->
                        val isSelected = choice.key == selectedIcon
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) accent.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) accent else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedIcon = choice.key }
                                .testTag("pocket_icon_${choice.key}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = choice.icon,
                                contentDescription = choice.label,
                                tint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PocketVisuals.colorChoices.forEach { choice ->
                        val isSelected = choice.key == selectedColor
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(choice.color)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = choice.key }
                                .testTag("pocket_color_${choice.key}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = choice.label,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    val initial = initialAmountText.toLongOrNull() ?: 0L
                    val target = if (hasGoal) targetAmountText.toLongOrNull() else null
                    when {
                        cleanName.isBlank() -> errorMsg = "Ingresa un nombre para el bolsillo."
                        hasGoal && (target == null || target <= 0L) ->
                            errorMsg = "Ingresa un monto de meta mayor a cero o desactiva la meta."
                        else -> onSave(cleanName, initial, target, selectedIcon, selectedColor)
                    }
                },
                enabled = !isProcessing,
                modifier = Modifier.testTag("btn_confirm_save_pocket")
            ) {
                Text(if (isEditing) "Guardar" else "Crear")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun PocketMovementDialog(
    pocket: SavingsPocket,
    movementType: String,
    formattedBalance: String,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (amount: Long, note: String?) -> Unit
) {
    val isDeposit = movementType == PocketMovement.TYPE_DEPOSIT
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Text(
                text = if (isDeposit) "Aportar a ${pocket.name}" else "Retirar de ${pocket.name}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Saldo disponible: $formattedBalance",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { raw ->
                        amountText = raw.filter { it.isDigit() }.take(11)
                        errorMsg = null
                    },
                    label = { Text("Monto (COP)") },
                    placeholder = { Text("Ej: 150000") },
                    prefix = { Text("$ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_movement_amount")
                )

                OutlinedTextField(
                    value = noteText,
                    onValueChange = {
                        if (it.length <= 200) noteText = it
                    },
                    label = { Text("Nota (opcional)") },
                    placeholder = {
                        Text(if (isDeposit) "Ej: Aporte quincena" else "Ej: Compra tiquetes")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_movement_note")
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: 0L
                    when {
                        amount <= 0L -> errorMsg = "Ingresa un monto mayor a cero."
                        !isDeposit && amount > pocket.currentAmount ->
                            errorMsg = "No puedes retirar más del saldo disponible ($formattedBalance)."
                        else -> onConfirm(amount, noteText.trim().takeIf { it.isNotEmpty() })
                    }
                },
                enabled = !isProcessing,
                colors = if (isDeposit) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                } else {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.testTag("btn_confirm_movement")
            ) {
                Text(if (isDeposit) "Aportar" else "Retirar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun JoinPocketByCodeDialog(
    isProcessing: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Text("Unirse a un bolsillo", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Ingresa el código temporal que te compartió tu pareja para ver y editar el bolsillo juntos:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = {
                        if (it.length <= 16) {
                            codeInput = it.uppercase()
                            localError = null
                        }
                    },
                    label = { Text("Código de invitación") },
                    placeholder = { Text("Ej: AHO-7M4P2") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_join_pocket_code")
                )

                val visibleError = localError ?: errorMessage
                if (!visibleError.isNullOrBlank()) {
                    Text(
                        text = visibleError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (codeInput.trim().length < 4) {
                        localError = "Ingresa el código completo."
                    } else {
                        onJoin(codeInput)
                    }
                },
                enabled = !isProcessing,
                modifier = Modifier.testTag("btn_confirm_join_pocket")
            ) {
                Text("Unirse")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun SharePocketInviteDialog(
    pocket: SavingsPocket,
    isProcessing: Boolean,
    onGenerateCode: () -> Unit,
    onDeactivateCode: () -> Unit,
    onCopiedCode: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val dateFormatter = remember {
        SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale("es", "CO"))
    }
    val activeCode = pocket.shareCode?.takeIf { it.isNotBlank() }
    val expiresDate = pocket.shareCodeExpiresAt?.toDate()
    val isValidActiveCode = activeCode != null && expiresDate != null && expiresDate.after(Date())

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Invitar a '${pocket.name}'",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Comparte este código temporal con tu pareja para que pueda ver este bolsillo, aportar, retirar, editar la meta o salirse cuando quiera.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (activeCode == null) {
                    Button(
                        onClick = onGenerateCode,
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_dialog_generate_invite_code"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Generar código de invitación")
                    }
                } else {
                    val formattedCode = SavingsRepository.formatDisplayCode(activeCode)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formattedCode,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.6.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("text_dialog_invite_code")
                                )

                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(formattedCode))
                                        onCopiedCode()
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("btn_dialog_copy_invite_code")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copiar código",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Text(
                                text = if (!isValidActiveCode) {
                                    "Este código venció. Regenera uno nuevo para invitar."
                                } else {
                                    "Válido hasta: ${dateFormatter.format(expiresDate!!)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (!isValidActiveCode) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isValidActiveCode) {
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
                                .testTag("btn_dialog_share_invite_intent"),
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
                            onClick = onGenerateCode,
                            enabled = !isProcessing,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_dialog_regenerate_invite_code")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Regenerar")
                        }

                        TextButton(
                            onClick = onDeactivateCode,
                            enabled = !isProcessing,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_dialog_deactivate_invite_code")
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Desactivar")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun SelectPocketToInviteDialog(
    ownedPockets: List<SavingsPocket>,
    formatCurrency: (Long) -> String,
    onSelectPocket: (SavingsPocket) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("¿A qué bolsillo deseas invitar?", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Elige el bolsillo que quieres compartir con tu pareja:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ownedPockets.forEach { pocket ->
                    val accent = PocketVisuals.getColor(pocket.colorKey)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectPocket(pocket) }
                            .testTag("select_invite_pocket_${pocket.id}"),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = PocketVisuals.getIcon(pocket.iconKey),
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pocket.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatCurrency(pocket.currentAmount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Invitar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

