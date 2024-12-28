package com.beakoninc.locusnotes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagInput(
    tags: List<String>,
    onTagsChanged: (List<String>) -> Unit
) {
    var newTag by remember { mutableStateOf("") }

    Column {
        TextField(
            value = newTag,
            onValueChange = { newTag = it },
            label = { Text("Add tag") },
            trailingIcon = {
                IconButton(
                    onClick = {
                        if (newTag.isNotEmpty() && !tags.contains(newTag)) {
                            onTagsChanged(tags + newTag)
                            newTag = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, "Add tag")
                }
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (newTag.isNotEmpty() && !tags.contains(newTag)) {
                        onTagsChanged(tags + newTag)
                        newTag = ""
                    }
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Start,
            maxItemsInEachRow = Int.MAX_VALUE
        ) {
            tags.forEach { tag ->
                AssistChip(
                    onClick = { },
                    label = { Text(tag) },
                    modifier = Modifier.padding(end = 4.dp),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                onTagsChanged(tags - tag)
                            },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove tag",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}