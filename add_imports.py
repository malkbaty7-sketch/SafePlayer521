with open('app/src/main/java/com/example/ui/HomeScreen.kt', 'r') as f:
    content = f.read()

imports = """
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.graphics.Color
"""

if "import androidx.compose.material.icons.filled.MoreVert" not in content:
    lines = content.split('\n')
    for i, line in enumerate(lines):
        if line.startswith('import '):
            lines.insert(i, imports.strip())
            break
    
    with open('app/src/main/java/com/example/ui/HomeScreen.kt', 'w') as f:
        f.write('\n'.join(lines))
        print("Imports added")
else:
    print("Imports already exist")
