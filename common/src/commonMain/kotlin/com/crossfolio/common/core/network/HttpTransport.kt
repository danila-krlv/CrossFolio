package com.crossfolio.common.core.network

class HttpRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val followRedirects: Boolean = true,
)

class HttpResponse(val statusCode: Int, val body: ByteArray)

interface HttpTransport {
    // Complete once on the main thread. Never expose transport exception details.
    fun execute(request: HttpRequest, completion: (NetworkResult<HttpResponse>) -> Unit)
}
