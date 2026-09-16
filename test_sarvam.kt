import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.Base64

fun main() {
    val ttsUrl = "https://api.sarvam.ai/text-to-speech"
    val apiKey = "sk_da8ttogf_h7ZjLkuwPrHdxc3la5v3FcfG"
    val client = OkHttpClient()
    
    val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    val json = JSONObject().apply {
        put("inputs", JSONArray().apply { put("नमस्ते") })
        put("target_language_code", "hi-IN")
        put("speaker", "shubh")
        put("model", "bulbul:v3")
    }
    
    val request = Request.Builder()
        .url(ttsUrl)
        .addHeader("api-subscription-key", apiKey)
        .post(json.toString().toRequestBody(jsonMediaType))
        .build()
        
    client.newCall(request).execute().use { response ->
        val resStr = response.body?.string() ?: ""
        println(resStr.substring(0, 100))
        val resJson = JSONObject(resStr)
        val base64 = resJson.getJSONArray("audios").getString(0)
        val bytes = Base64.getDecoder().decode(base64)
        println("Bytes length: ${bytes.size}")
        println("Starts with RIFF: ${bytes[0] == 'R'.toByte() && bytes[1] == 'I'.toByte()}")
    }
}
