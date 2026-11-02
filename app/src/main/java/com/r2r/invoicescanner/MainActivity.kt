package com.r2r.invoicescanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.r2r.invoicescanner.ui.InvoiceScannerApp
import com.r2r.invoicescanner.ui.MainViewModel
import com.r2r.invoicescanner.ui.theme.InvoiceScannerTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InvoiceScannerTheme {
                InvoiceScannerApp(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onAppForeground() // app lock: locks again after a while in the background
    }

    override fun onResume() {
        super.onResume()
        vm.refreshImageStatus() // files may have been deleted or moved in a file manager meanwhile
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.onAppBackground()
    }
}
