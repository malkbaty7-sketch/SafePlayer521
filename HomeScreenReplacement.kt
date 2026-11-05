import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

@Composable
fun FolderListItem(
    title: String,
    count: Int,
    countText: String? = null,
    countColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isRecent: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isRecent) Icons.Default.Schedule else Icons.Default.Folder,
            contentDescription = "Folder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = countText ?: count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = countColor
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        IconButton(onClick = { /* More options */ }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
