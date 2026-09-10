package com.crossfolio.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.crossfolio.shared.HelloViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CrossFolioApp()
        }
    }
}

@Composable
private fun CrossFolioApp(viewModel: HelloViewModel = remember { HelloViewModel() }) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            HelloScreen(greeting = viewModel.greeting)
        }
    }
}

@Composable
private fun HelloScreen(greeting: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = greeting)
    }
}

@Preview(showBackground = true)
@Composable
private fun HelloScreenPreview() {
    MaterialTheme {
        HelloScreen(greeting = "Hello")
    }
}
