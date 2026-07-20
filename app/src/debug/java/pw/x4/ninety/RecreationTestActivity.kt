package pw.x4.ninety

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

/** Debug-only lifecycle probe used by managed-device tests without starting the Hero video decoder. */
class RecreationTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Ninety recreation probe") }
    }
}
