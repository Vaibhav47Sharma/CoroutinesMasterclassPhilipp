package com.plcoding.coroutinesmasterclass.sections.coroutine_cancellation.homework.assignmenttwo

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MoneyTransferViewModel : ViewModel() {

  val state = mutableStateOf(MoneyTransferState())

  private var job: Job? = null
  private var applicationScope: CoroutineScope? = null

  fun onAction(action: MoneyTransferAction) {
    when (action) {
      MoneyTransferAction.TransferFunds -> transferFunds()
      MoneyTransferAction.CancelTransfer -> {
        job?.cancel()
        state.value = state.value.copy(
          isTransferring = false,
          processingState = null,
          resultMessage = null,
        )
      }
    }
  }

  private fun transferFunds() {
    val savingsBalance = state.value.savingsBalance
    val checkingBalance = state.value.checkingBalance
    Log.i("xyz: ", "Savings $savingsBalance, checking $checkingBalance")
    // Use applicationScope if we want to ensure it works even if we go up a screen, otherwise
    // viewModelScope is fine.
    job = applicationScope?.launch {
      withContext(Dispatchers.Default) {
        try {
          state.value = state.value.copy(
            isTransferring = true,
            resultMessage = null,
          )
          val amountToTransfer = state.value.transferAmount.text.toString().toDoubleOrNull()

          if (amountToTransfer == null) {
            state.value = state.value.copy(
              resultMessage = "Invalid Amount"
            )
            return@withContext
          }

          if (amountToTransfer == 0.0) {
            state.value = state.value.copy(
              resultMessage = "Enter amount greater than 0"
            )
            return@withContext
          }

          state.value = state.value.copy(
            processingState = ProcessingState.CheckingFunds,
          )
          val hasEnoughFunds = checkHasEnoughFunds(state.value.savingsBalance, amountToTransfer)

          if (!hasEnoughFunds) {
            state.value = state.value.copy(
              resultMessage = "Insufficient funds",
            )
            return@withContext
          }

          ensureActive()
          debitAccount(state.value.savingsBalance, amountToTransfer)

          Log.i("xyz: ", "Starting the credit")
          ensureActive()
          creditAccount(state.value.checkingBalance, amountToTransfer)

          Log.i("xyz: ", "Transfer funds resume")
          if (isActive) {
            state.value = state.value.copy(
              resultMessage = "Transfer complete!",
            )
          }
        } catch (e: Exception) {
          Log.i("xyz: ", "Error processing transfer: ${e.message}")
          if (e is CancellationException) {
            throw e
          }
          // Restore the values from before
          state.value =
            state.value.copy(checkingBalance = checkingBalance, savingsBalance = savingsBalance)
        } finally {
          withContext(NonCancellable) {
            cleanupResources()
            state.value = state.value.copy(
              processingState = null,
              isTransferring = false,
            )
          }
        }
      }
    }
  }

  private suspend fun creditAccount(toAccountBalance: Double, amount: Double) {
    state.value = state.value.copy(
      processingState = ProcessingState.CreditingAccount,
    )
    Log.i("xyz:", "Crediting account with balance $toAccountBalance and amount $amount")
    delay(3000)
    state.value = state.value.copy(
      checkingBalance = toAccountBalance + amount,
    )
  }

  private suspend fun debitAccount(fromAccountBalance: Double, amount: Double) {
    state.value = state.value.copy(processingState = ProcessingState.DebitingAccount)
    delay(3000)
    state.value = state.value.copy(
      savingsBalance = fromAccountBalance - amount,
    )
  }

  private suspend fun checkHasEnoughFunds(fromAccountBalance: Double, amount: Double): Boolean {
    Log.i("xyz:", "Checking balance with balance $fromAccountBalance and amount $amount")
    delay(2000)
    return amount <= fromAccountBalance
  }

  private suspend fun cleanupResources() {
    state.value = state.value.copy(
      processingState = ProcessingState.CleanupResources,
    )
    delay(2000)
  }

  fun setSApplicationScope(applicationScope: CoroutineScope) {
    this.applicationScope = applicationScope
  }
}