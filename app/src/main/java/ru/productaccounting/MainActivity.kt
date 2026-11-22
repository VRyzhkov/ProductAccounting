package ru.productaccounting

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.productaccounting.ui.ProductAccountingApp
import ru.productaccounting.ui.theme.ProductAccountingTheme
import ru.productaccounting.update.UpdatePhase
import ru.productaccounting.update.UpdateViewModel

class MainActivity : ComponentActivity() {
    private val updateViewModel: UpdateViewModel by viewModels()
    private val installPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (packageManager.canRequestPackageInstalls()) installUpdate()
        else updateViewModel.showInstallError(
            "Установка не разрешена. Нажмите «Установить» и разрешите установку из ProductAccounting в настройках Android.",
        )
    }

    private fun installUpdate() {
        try {
            if (!packageManager.canRequestPackageInstalls()) {
                updateViewModel.showInstallError(
                    "Разрешите установку из ProductAccounting, затем вернитесь в приложение.",
                )
                installPermission.launch(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName"),
                    ),
                )
            } else {
                startActivity(updateViewModel.installerIntent())
                updateViewModel.showInstallError(
                    "APK готов. Завершите установку в окне Android. Если вы отменили её, нажмите «Установить» ещё раз.",
                )
            }
        } catch (_: Exception) {
            updateViewModel.showInstallError(
                "Не удалось открыть установщик. Проверьте разрешение на установку и наличие системного установщика APK.",
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val updateState by updateViewModel.state.collectAsStateWithLifecycle()
            ProductAccountingTheme {
                ProductAccountingApp(viewModel())
                if (updateState.phase != UpdatePhase.Hidden) {
                    AlertDialog(
                        onDismissRequest = {
                            if (updateState.phase != UpdatePhase.Downloading) updateViewModel.dismiss()
                        },
                        title = {
                            Text("Обновление ${updateState.update?.versionName.orEmpty()}")
                        },
                        text = {
                            Text(
                                if (updateState.phase == UpdatePhase.Offer) {
                                    "Доступна новая версия на GitHub. Скачать обновление?"
                                } else {
                                    updateState.message
                                },
                            )
                        },
                        confirmButton = {
                            when (updateState.phase) {
                                UpdatePhase.Offer, UpdatePhase.Failed -> TextButton(
                                    onClick = updateViewModel::download,
                                ) {
                                    Text(if (updateState.phase == UpdatePhase.Failed) "Повторить" else "Скачать")
                                }
                                UpdatePhase.Ready -> TextButton(onClick = ::installUpdate) {
                                    Text("Установить")
                                }
                                else -> Unit
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = updateViewModel::dismiss) {
                                Text(
                                    if (updateState.phase == UpdatePhase.Downloading) {
                                        "Отменить загрузку"
                                    } else {
                                        "Позже"
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
