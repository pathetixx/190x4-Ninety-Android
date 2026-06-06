package pw.x4.ninety

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pw.x4.ninety.ui.NinetyApp
import pw.x4.ninety.ui.theme.NinetyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NinetyTheme {
                NinetyApp()
            }
        }
    }
}
