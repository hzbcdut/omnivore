package app.omnivore.omnivore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.omnivore.omnivore.core.data.LocalHtmlImporter
import app.omnivore.omnivore.feature.reader.PDFReaderActivity
import app.omnivore.omnivore.feature.reader.WebReaderLoadingContainerActivity
import app.omnivore.omnivore.feature.root.RootView
import app.omnivore.omnivore.feature.theme.OmnivoreTheme
import com.pspdfkit.PSPDFKit
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var localHtmlImporter: LocalHtmlImporter

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen()

        super.onCreate(savedInstanceState)


        lifecycleScope.launch(Dispatchers.IO) {
            val licenseKey = getString(R.string.pspdfkit_license_key)

            if (licenseKey.length > 30) {
                PSPDFKit.initialize(this@MainActivity, licenseKey)
            } else {
                PSPDFKit.initialize(this@MainActivity, null)
            }
        }

        enableEdgeToEdge()

        setContent {
            OmnivoreTheme {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    RootView()
                }
            }
        }

        handleIncomingDocument(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingDocument(intent)
    }

    /**
     * Accepts `ACTION_SEND` / `ACTION_VIEW` for local .html / .pdf documents and
     * drops the reader straight into the imported item.
     */
    private fun handleIncomingDocument(intent: Intent?) {
        val uri: Uri = when (intent?.action) {
            Intent.ACTION_SEND ->
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        } ?: return

        lifecycleScope.launch {
            val item = localHtmlImporter.importUri(uri) ?: return@launch
            val activityClass = if (item.contentReader == "PDF") {
                PDFReaderActivity::class.java
            } else {
                WebReaderLoadingContainerActivity::class.java
            }
            startActivity(
                Intent(this@MainActivity, activityClass).putExtra("SAVED_ITEM_SLUG", item.slug)
            )
        }
    }
}
