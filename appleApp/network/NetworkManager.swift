import Common
import Foundation

final class NetworkManager: NSObject, HttpTransport {
    private let session: URLSession

    init(session: URLSession? = nil) {
        self.session = session ?? URLSession(
            configuration: .ephemeral, delegate: RedirectDelegate(), delegateQueue: nil
        )
    }

    func execute(request: HttpRequest, completion: @escaping (NetworkResult<HttpResponse>) -> Void) {
        guard let url = URL(string: request.url), ["http", "https"].contains(url.scheme?.lowercased() ?? ""),
              let host = url.host, !host.isEmpty else {
            let handler = ResponseHandler { _, _, _ in
                completion(NetworkResult(value: nil, error: "Invalid URL", failure: .transport))
            }
            DispatchQueue.main.async { handler.handle(nil, nil, nil) }
            return
        }
        var nativeRequest = URLRequest(url: url, timeoutInterval: 30)
        request.headers.forEach { nativeRequest.setValue($0.value, forHTTPHeaderField: $0.key) }
        let handler = ResponseHandler { data, response, error in
            guard error == nil, let response = response as? HTTPURLResponse, let data,
                  data.count <= Int32.max else {
                completion(NetworkResult(value: nil, error: "Network request failed", failure: .transport))
                return
            }
            let bytes = KotlinByteArray(size: Int32(data.count))
            for (index, byte) in data.enumerated() {
                bytes.set(index: Int32(index), value: Int8(bitPattern: byte))
            }
            completion(NetworkResult(value: HttpResponse(statusCode: Int32(response.statusCode), body: bytes),
                error: nil, failure: nil))
        }
        let task = session.dataTask(with: nativeRequest) { data, response, error in
            DispatchQueue.main.async { handler.handle(data, response, error) }
        }
        task.taskDescription = request.followRedirects ? nil : "block-redirects"
        task.resume()
    }
}

final class RedirectDelegate: NSObject, URLSessionTaskDelegate {
    func urlSession(
        _ session: URLSession, task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        // Keep authenticated requests on the original endpoint; images may follow redirects.
        completionHandler(task.taskDescription != "block-redirects"
            ? request : nil)
    }
}

// The immutable callback is transferred from URLSession to the main queue and invoked there once.
private final class ResponseHandler: @unchecked Sendable {
    let handle: (Data?, URLResponse?, Error?) -> Void

    init(_ handle: @escaping (Data?, URLResponse?, Error?) -> Void) {
        self.handle = handle
    }
}
