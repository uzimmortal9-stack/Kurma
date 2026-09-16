with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

content = content.replace("androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))", "androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = 16.dp))")

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
