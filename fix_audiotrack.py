import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

if 'private var currentAudioTrack: android.media.AudioTrack? = null' not in content:
    content = content.replace('private var mediaPlayer: MediaPlayer? = null',
        'private var mediaPlayer: MediaPlayer? = null\n    private var currentAudioTrack: android.media.AudioTrack? = null')

# Update tellRandomStory
content = content.replace('val audioTrack = android.media.AudioTrack(', 
                          'currentAudioTrack = android.media.AudioTrack(')
content = content.replace('audioTrack.write(', 'currentAudioTrack?.write(')
content = content.replace('audioTrack.play()', 'currentAudioTrack?.play()')
content = content.replace('audioTrack.release()', 'currentAudioTrack?.release()\n                    currentAudioTrack = null')

# Update playAudio
target_playaudio = """            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null"""
replacement_playaudio = """            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
            
            try {
                currentAudioTrack?.stop()
            } catch(e: Exception) {}
            currentAudioTrack?.release()
            currentAudioTrack = null"""
content = content.replace(target_playaudio, replacement_playaudio)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
