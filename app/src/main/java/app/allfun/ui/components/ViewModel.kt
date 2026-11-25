package app.allfun.ui.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/** Builds a ViewModel with an explicit factory, no DI framework needed. */
@Composable
fun <T : ViewModel> rememberViewModel(key: String? = null, create: () -> T): T {
    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
    @Suppress("UNCHECKED_CAST")
    return viewModel<ViewModel>(key = key, factory = factory) as T
}
