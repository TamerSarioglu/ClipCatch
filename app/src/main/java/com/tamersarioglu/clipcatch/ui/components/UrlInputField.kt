package com.tamersarioglu.clipcatch.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType


@Composable
fun UrlInputField(
    url: String,
    onUrlChange: (String) -> Unit,
    isValid: Boolean,
    errorMessage: String? = null,
    isValidating: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Column(modifier = modifier) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { 
                Text(
                    text = "YouTube URL",
                    modifier = Modifier.semantics {
                        contentDescription = "Enter YouTube video URL"
                    }
                )
            },
            placeholder = { 
                Text(
                    text = "https://youtube.com/watch?v=...",
                    modifier = Modifier.semantics {
                        contentDescription = "YouTube URL placeholder"
                    }
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "URL icon",
                    tint = when {
                        errorMessage != null -> MaterialTheme.colorScheme.error
                        isValid && url.isNotEmpty() -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            },
            trailingIcon = {
                if (url.isNotEmpty()) {
                    IconButton(
                        onClick = { onUrlChange("") },
                        modifier = Modifier.semantics {
                            contentDescription = "Clear URL input"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear URL",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            supportingText = {
                when {
                    errorMessage != null -> {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics {
                                contentDescription = "URL validation error: $errorMessage"
                            }
                        )
                    }
                    isValidating -> {
                        Text(
                            text = "Validating URL...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics {
                                contentDescription = "Validating URL"
                            }
                        )
                    }
                    isValid && url.isNotEmpty() -> {
                        Text(
                            text = "Valid YouTube URL",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics {
                                contentDescription = "Valid YouTube URL"
                            }
                        )
                    }
                    url.isNotEmpty() -> {
                        Text(
                            text = "Enter a valid YouTube URL",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics {
                                contentDescription = "Enter a valid YouTube URL"
                            }
                        )
                    }
                }
            },
            isError = errorMessage != null,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = when {
                        errorMessage != null -> "YouTube URL input field with error: $errorMessage"
                        isValid && url.isNotEmpty() -> "YouTube URL input field with valid URL"
                        else -> "YouTube URL input field"
                    }
                }
        )
    }
}