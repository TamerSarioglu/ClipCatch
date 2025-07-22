import com.tamersarioglu.clipcatch.data.util.UrlValidator

fun main() {
    val urlValidator = UrlValidator()
    
    val result = urlValidator.validateUrlWithDetails("https://www.youtube.com/watch?v=invalid")
    println("Result: isValid=${result.isValid}, message='${result.message}', videoId=${result.videoId}")
}