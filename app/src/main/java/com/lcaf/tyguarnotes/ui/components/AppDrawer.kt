package com.lcaf.tyguarnotes.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lcaf.tyguarnotes.R

enum class DrawerItem { NOTES, GALLERY, DOCUMENTS }

@Composable
fun AppDrawerContent(
    selectedItem: DrawerItem,
    onItemClick: (DrawerItem) -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = 300.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Cursive
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
        }

        Spacer(modifier = Modifier.height(24.dp))

        NavigationDrawerItem(
            label = { Text(stringResource(R.string.notes)) },
            selected = selectedItem == DrawerItem.NOTES,
            onClick = { onItemClick(DrawerItem.NOTES) },
            icon = { Icon(Icons.Default.Description, contentDescription = null) },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.gallery)) },
            selected = selectedItem == DrawerItem.GALLERY,
            onClick = { onItemClick(DrawerItem.GALLERY) },
            icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.documents)) },
            selected = selectedItem == DrawerItem.DOCUMENTS,
            onClick = { onItemClick(DrawerItem.DOCUMENTS) },
            icon = { Icon(Icons.Default.InsertDriveFile, contentDescription = null) },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}
