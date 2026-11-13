package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.model.PocketMovement
import com.example.data.model.SavingsPocket
import com.example.data.repository.SavingsDataSource
import com.example.data.repository.SavingsRepository
import com.example.dev.DevSavingsRepository
import com.example.dev.DevTesterAccount
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

data class SavingsUiSummary(
    val totalSaved: Long = 0L,
    val personalSaved: Long = 0L,
    val sharedSaved: Long = 0L,
    val pocketCount: Int = 0,
    val sharedCount: Int = 0
)

class SavingsViewModel(
    private val repository: SavingsDataSource,
    private val currentUserIdProvider: () -> String = { Firebase.auth.currentUser?.uid ?: "" }
) : ViewModel() {

    private val copFormat = NumberFormat.getCurrencyInstance(Locale("es", "CO")).apply {
        maximumFractionDigits = 0
    }

    private val _selectedPocketId = MutableStateFlow<String?>(null)
    val selectedPocketId: StateFlow<String?> = _selectedPocketId.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val pockets: StateFlow<List<SavingsPocket>> = repository.observePockets()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            _errorMessage.value = (firestoreError ?: error).localizedMessage
                ?: "No se pudieron cargar los bolsillos."
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val summary: StateFlow<SavingsUiSummary> = pockets.map { list ->
        val shared = list.filter { it.memberUids.size > 1 }
        val personal = list.filter { it.memberUids.size <= 1 }
        SavingsUiSummary(
            totalSaved = list.sumOf { it.currentAmount },
            personalSaved = personal.sumOf { it.currentAmount },
            sharedSaved = shared.sumOf { it.currentAmount },
            pocketCount = list.size,
            sharedCount = shared.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SavingsUiSummary())

    val selectedPocket: StateFlow<SavingsPocket?> = combine(
        pockets,
        _selectedPocketId
    ) { list, id ->
        if (id == null) null else list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedPocketMovements: StateFlow<List<PocketMovement>> = _selectedPocketId
        .flatMapLatest { id ->
            if (id.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeMovements(id).catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun currentUserId(): String = currentUserIdProvider()

    fun selectPocket(pocketId: String?) {
        _selectedPocketId.value = pocketId
    }

    fun clearMessages() {
        _feedbackMessage.value = null
        _errorMessage.value = null
    }

    fun formatCurrency(amount: Long): String = copFormat.format(amount)

    fun createPocket(
        name: String,
        initialAmount: Long,
        targetAmount: Long?,
        iconKey: String,
        colorKey: String,
        onCreated: () -> Unit = {}
    ) {
        if (name.isBlank() || _isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            val res = repository.createPocket(name, initialAmount, targetAmount, iconKey, colorKey)
            _isProcessing.value = false
            if (res.isSuccess) {
                _feedbackMessage.value = "Bolsillo creado"
                onCreated()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo crear el bolsillo."
            }
        }
    }

    fun updatePocketDetails(
        pocketId: String,
        name: String,
        targetAmount: Long?,
        iconKey: String,
        colorKey: String,
        onUpdated: () -> Unit = {}
    ) {
        if (name.isBlank() || _isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            val res = repository.updatePocketDetails(pocketId, name, targetAmount, iconKey, colorKey)
            _isProcessing.value = false
            if (res.isSuccess) {
                _feedbackMessage.value = "Bolsillo actualizado"
                onUpdated()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo actualizar el bolsillo."
            }
        }
    }

    fun recordMovement(
        pocketId: String,
        type: String,
        amount: Long,
        note: String?,
        onDone: () -> Unit = {}
    ) {
        if (amount <= 0L || _isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            val res = repository.recordMovement(pocketId, type, amount, note)
            _isProcessing.value = false
            if (res.isSuccess) {
                _feedbackMessage.value = if (type == PocketMovement.TYPE_DEPOSIT) {
                    "Aporte registrado"
                } else {
                    "Retiro registrado"
                }
                onDone()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo registrar el movimiento."
            }
        }
    }

    fun generateOrRegenerateShareCode(pocket: SavingsPocket) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            val res = repository.generateOrRegenerateShareCode(pocket)
            _isProcessing.value = false
            if (res.isSuccess) {
                _feedbackMessage.value = "Código temporal listo por 24 horas"
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo generar el código."
            }
        }
    }

    fun deactivateShareCode(pocket: SavingsPocket) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            val res = repository.deactivateShareCode(pocket)
            _isProcessing.value = false
            if (res.isSuccess) {
                _feedbackMessage.value = "Código desactivado"
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo desactivar el código."
            }
        }
    }

    fun joinPocketByCode(code: String, onJoined: () -> Unit = {}) {
        if (code.isBlank() || _isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            val res = repository.joinPocketByCode(code)
            _isProcessing.value = false
            if (res.isSuccess) {
                val pocketName = res.getOrThrow()
                _feedbackMessage.value = "Te uniste a '$pocketName'"
                onJoined()
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo unir con ese código."
            }
        }
    }

    fun leavePocket(pocketId: String) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            val res = repository.leavePocket(pocketId)
            _isProcessing.value = false
            if (res.isSuccess) {
                _selectedPocketId.value = null
                _feedbackMessage.value = "Saliste del bolsillo"
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No pudiste salir del bolsillo."
            }
        }
    }

    fun removeMember(pocket: SavingsPocket, memberUid: String) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            val res = repository.removeMember(pocket, memberUid)
            _isProcessing.value = false
            if (res.isSuccess) {
                _feedbackMessage.value = "Participante removido"
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo remover al participante."
            }
        }
    }

    fun deletePocket(pocket: SavingsPocket) {
        if (_isProcessing.value) return
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            val res = repository.deletePocket(pocket)
            _isProcessing.value = false
            if (res.isSuccess) {
                _selectedPocketId.value = null
                _feedbackMessage.value = "Bolsillo eliminado"
            } else {
                _errorMessage.value = res.exceptionOrNull()?.localizedMessage ?: "No se pudo eliminar el bolsillo."
            }
        }
    }

    class Factory(
        private val context: Context,
        private val devAccount: DevTesterAccount? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return if (BuildConfig.DEBUG && devAccount != null) {
                SavingsViewModel(
                    repository = DevSavingsRepository(devAccount),
                    currentUserIdProvider = { devAccount.uid }
                ) as T
            } else {
                SavingsViewModel(
                    repository = SavingsRepository(context),
                    currentUserIdProvider = { Firebase.auth.currentUser?.uid ?: "" }
                ) as T
            }
        }
    }
}
