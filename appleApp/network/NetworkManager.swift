import Common
import Foundation

final class NetworkManager: NSObject, NetworkProtocol {
    private let apiKeyProvider: () -> String
    private let session: URLSession

    init(apiKeyProvider: @escaping () -> String, session: URLSession? = nil) {
        self.apiKeyProvider = apiKeyProvider
        self.session = session ?? URLSession(
            configuration: .ephemeral, delegate: RedirectDelegate(), delegateQueue: nil
        )
    }

    func fetchMap(completion: @escaping (NetworkResult<NSArray>) -> Void) {
        request(path: "v1/cryptocurrency/map", parameters: ["start": "1", "limit": "1000"],
            transform: { payload in
                guard let coins = payload["data"] as? [[String: Any]] else {
                    throw RequestError.invalidResponse
                }
                return try coins.map { coin in
                    guard let id = coin["id"] as? NSNumber, let ticker = coin["symbol"] as? String,
                          let name = coin["name"] as? String, let slug = coin["slug"] as? String else {
                        throw RequestError.invalidResponse
                    }
                    return Asset(
                        searchId: id.stringValue, ticker: ticker, searchPlatform: .coinMarketCap,
                        name: name, slug: slug,
                        rank: (coin["rank"] as? NSNumber).map { KotlinInt(int: $0.int32Value) }
                    )
                } as NSArray
            }, completion: completion)
    }

    func fetchLogoURL(id: String, completion: @escaping (NetworkResult<NSString>) -> Void) {
        fetchLogoUrlArray(idString: id, idArray: [id]) { result in
            if let error = result.error {
                completion(NetworkResult(value: nil, error: error))
            } else if let logo = result.value?[id] as? String {
                completion(NetworkResult(value: logo as NSString, error: nil))
            } else {
                completion(NetworkResult(value: nil, error: "Invalid response or missing data"))
            }
        }
    }

    func fetchLogoUrlArray(
        idString: String, idArray: [String], completion: @escaping (NetworkResult<NSDictionary>) -> Void
    ) {
        request(path: "v2/cryptocurrency/info", parameters: ["id": idString, "aux": "logo"],
            transform: { payload in
                guard let metadata = payload["data"] as? [String: [String: Any]] else {
                    throw RequestError.invalidResponse
                }
                var logos: [String: String] = [:]
                for id in idArray {
                    guard let logo = metadata[id]?["logo"] as? String else {
                        throw RequestError.invalidResponse
                    }
                    logos[id] = logo
                }
                return logos as NSDictionary
            }, completion: completion)
    }

    func fetchImg(url: String, completion: @escaping (NetworkResult<KotlinByteArray>) -> Void) {
        guard let url = URL(string: url), ["http", "https"].contains(url.scheme?.lowercased() ?? ""),
              let host = url.host, !host.isEmpty else {
            deliverFailure("Invalid URL", completion: completion)
            return
        }
        download(URLRequest(url: url, timeoutInterval: 30)) { data in
            guard data.count <= Int32.max else { throw RequestError.invalidResponse }
            let bytes = KotlinByteArray(size: Int32(data.count))
            for (index, byte) in data.enumerated() {
                bytes.set(index: Int32(index), value: Int8(bitPattern: byte))
            }
            return bytes
        } completion: { completion($0) }
    }

    func fetchPriceArray(
        idString: String, idArray: [String], completion: @escaping (NetworkResult<NSDictionary>) -> Void
    ) {
        request(path: "v2/cryptocurrency/quotes/latest", parameters: ["id": idString, "convert": "USD"],
            transform: { payload in
                guard let quotes = payload["data"] as? [String: [String: Any]] else {
                    throw RequestError.invalidResponse
                }
                var prices: [String: KotlinDouble] = [:]
                for id in idArray {
                    guard let quote = quotes[id]?["quote"] as? [String: [String: Any]],
                          let price = quote["USD"]?["price"] as? NSNumber,
                          price.doubleValue.isFinite else {
                        throw RequestError.invalidResponse
                    }
                    prices[id] = KotlinDouble(double: price.doubleValue)
                }
                return prices as NSDictionary
            }, completion: completion)
    }

    private func request<T: AnyObject>(
        path: String, parameters: [String: String], transform: @escaping ([String: Any]) throws -> T,
        completion: @escaping (NetworkResult<T>) -> Void
    ) {
        let apiKey = apiKeyProvider().trimmingCharacters(in: .whitespacesAndNewlines)
        guard !apiKey.isEmpty else {
            deliverFailure("API key is unavailable", completion: completion)
            return
        }
        var components = URLComponents(string: "https://pro-api.coinmarketcap.com/\(path)")!
        components.queryItems = parameters.map { URLQueryItem(name: $0.key, value: $0.value) }
        guard let url = components.url else {
            deliverFailure("Invalid URL", completion: completion)
            return
        }
        var request = URLRequest(url: url, timeoutInterval: 30)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(apiKey, forHTTPHeaderField: "X-CMC_PRO_API_KEY")
        download(request, isAPI: true, transform: { data in
            guard let payload = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                throw RequestError.invalidResponse
            }
            try Self.checkAPIStatus(payload)
            return try transform(payload)
        }, completion: completion)
    }

    private func download<T: AnyObject>(
        _ request: URLRequest, isAPI: Bool = false, transform: @escaping (Data) throws -> T,
        completion: @escaping (NetworkResult<T>) -> Void
    ) {
        let handler = ResponseHandler { data, response, error in
            do {
                guard error == nil else { throw RequestError.transport }
                guard let response = response as? HTTPURLResponse, let data else {
                    throw RequestError.invalidResponse
                }
                guard (200..<300).contains(response.statusCode) else {
                    if isAPI, let payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                        try Self.checkAPIStatus(payload)
                    }
                    throw RequestError.http(response.statusCode)
                }
                completion(NetworkResult(value: try transform(data), error: nil))
            } catch {
                let message = (error as? RequestError)?.message ?? "Invalid response or missing data"
                completion(NetworkResult(value: nil, error: message))
            }
        }
        session.dataTask(with: request) { data, response, error in
            DispatchQueue.main.async { handler.handle(data, response, error) }
        }.resume()
    }

    private static func checkAPIStatus(_ payload: [String: Any]) throws {
        let status = payload["status"] as? [String: Any]
        let code = (status?["error_code"] as? NSNumber)?.intValue
            ?? (status?["error_code"] as? String).flatMap(Int.init)
        guard let code else { throw RequestError.invalidResponse }
        if code != 0 { throw RequestError.api(code) }
    }

    private func deliverFailure<T: AnyObject>(
        _ message: String, completion: @escaping (NetworkResult<T>) -> Void
    ) {
        let handler = ResponseHandler { _, _, _ in completion(NetworkResult(value: nil, error: message)) }
        DispatchQueue.main.async { handler.handle(nil, nil, nil) }
    }
}

private final class RedirectDelegate: NSObject, URLSessionTaskDelegate {
    func urlSession(
        _ session: URLSession, task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        // Keep authenticated requests on the original endpoint; images may follow redirects.
        completionHandler(task.originalRequest?.value(forHTTPHeaderField: "X-CMC_PRO_API_KEY") == nil
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

private enum RequestError: Error {
    case http(Int), api(Int), invalidResponse, transport

    var message: String {
        switch self {
        case .http(let code): "HTTP \(code): Request failed"
        case .api(let code): "CoinMarketCap error \(code): API request rejected"
        case .invalidResponse: "Invalid response or missing data"
        case .transport: "Network request failed"
        }
    }
}
