with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    lines = f.readlines()

out = []
skip = False
for i, line in enumerate(lines):
    if "if (success) {" in line:
        skip = True
        out.append('            if (audioBytes != null) {\n')
        out.append('                runOnUiThread {\n')
        out.append('                    statusMessage = "Kurma: $llmResponse\\nSpeaking..."\n')
        out.append('                    sendCommandToArduino("K_ON\\n")\n')
        out.append('                }\n')
        out.append('                try {\n')
        out.append('                    runOnUiThread { mediaPlayer?.release() }\n')
        out.append('                    val audioTrack = android.media.AudioTrack(\n')
        out.append('                        android.media.AudioManager.STREAM_MUSIC,\n')
        out.append('                        22050,\n')
        out.append('                        android.media.AudioFormat.CHANNEL_OUT_MONO,\n')
        out.append('                        android.media.AudioFormat.ENCODING_PCM_16BIT,\n')
        out.append('                        audioBytes.size,\n')
        out.append('                        android.media.AudioTrack.MODE_STATIC\n')
        out.append('                    )\n')
        out.append('                    val startOffset = if (audioBytes.size > 44 && audioBytes[0] == \'R\'.code.toByte() && audioBytes[1] == \'I\'.code.toByte()) 44 else 0\n')
        out.append('                    val length = audioBytes.size - startOffset\n')
        out.append('                    audioTrack.write(audioBytes, startOffset, length)\n')
        out.append('                    audioTrack.play()\n')
        out.append('                    val durationMs = (length / (22050.0 * 2)) * 1000\n')
        out.append('                    kotlinx.coroutines.delay(durationMs.toLong() + 300)\n')
        out.append('                    audioTrack.release()\n')
        out.append('                    resumeChatbotMode()\n')
        out.append('                } catch(e: Exception) {\n')
        out.append('                    resumeChatbotMode()\n')
        out.append('                }\n')
        out.append('            } else {\n')
        out.append('                resumeChatbotMode()\n')
        out.append('            }\n')
        continue
        
    if skip:
        if "            } else {" in line:
            pass # already added
        elif "                resumeChatbotMode()" in line and "}" in lines[i+1]:
            pass
        elif "            }" in line and i < len(lines)-1 and "        }" in lines[i+1]:
            skip = False
        continue
        
    out.append(line)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.writelines(out)
