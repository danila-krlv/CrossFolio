import Common
import XCTest
@testable import CrossFolio_iOS

final class NetworkManagerTests: XCTestCase {
    func testCatalogUsesCurrentKeyAndKeepsAssetsWithSameTicker() {
        var key = "old-placeholder"
        let manager = makeManager { key }
        key = "test-placeholder"
        let done = expectation(description: "catalog")
        manager.fetchMap { result in
            XCTAssertTrue(Thread.isMainThread)
            XCTAssertNil(result.error)
            let assets = result.value as? [Asset]
            XCTAssertEqual(assets?.map(\.searchId), ["1", "2"])
            XCTAssertEqual(assets?.map(\.ticker), ["BTC", "BTC"])
            done.fulfill()
        }
        wait(for: [done], timeout: 3)
    }

    func testMetadataQuotesAndImage() {
        let manager = makeManager { "test-placeholder" }
        let logo = expectation(description: "logo")
        let logos = expectation(description: "logos")
        let prices = expectation(description: "prices")
        let image = expectation(description: "image")
        manager.fetchLogoURL(id: "1") { result in
            XCTAssertEqual(result.value as String?, "https://example.com/image")
            XCTAssertNil(result.error)
            logo.fulfill()
        }
        manager.fetchLogoUrlArray(idString: "1", idArray: ["1"]) { result in
            XCTAssertEqual(result.value?["1"] as? String, "https://example.com/image")
            XCTAssertNil(result.error)
            logos.fulfill()
        }
        manager.fetchPriceArray(idString: "1", idArray: ["1"]) { result in
            XCTAssertEqual((result.value?["1"] as? KotlinDouble)?.doubleValue, 12.5)
            XCTAssertNil(result.error)
            prices.fulfill()
        }
        manager.fetchImg(url: "https://example.com/image") { result in
            XCTAssertNil(result.error)
            XCTAssertEqual(result.value?.size, 3)
            XCTAssertEqual(result.value?.get(index: 1), -1)
            image.fulfill()
        }
        wait(for: [logo, logos, prices, image], timeout: 3)
    }

    func testFailuresAreDeliveredOnMainThread() {
        let manager = makeManager { "test-placeholder" }
        let missing = expectation(description: "missing metadata")
        let api = expectation(description: "API error")
        let malformed = expectation(description: "malformed response")
        let invalid = expectation(description: "invalid URL")
        let key = expectation(description: "empty key")
        manager.fetchLogoUrlArray(idString: "1", idArray: ["missing"]) { result in
            XCTAssertNil(result.value)
            XCTAssertNotNil(result.error)
            missing.fulfill()
        }
        manager.fetchPriceArray(idString: "error", idArray: ["1"]) { result in
            XCTAssertTrue(Thread.isMainThread)
            XCTAssertEqual(result.error, "CoinMarketCap error 1001: API request rejected")
            XCTAssertNil(result.value)
            api.fulfill()
        }
        manager.fetchPriceArray(idString: "malformed", idArray: ["1"]) { result in
            XCTAssertNil(result.value)
            XCTAssertNotNil(result.error)
            malformed.fulfill()
        }
        manager.fetchImg(url: "file:///image") { result in
            XCTAssertTrue(Thread.isMainThread)
            XCTAssertEqual(result.error, "Invalid URL")
            invalid.fulfill()
        }
        makeManager { "" }.fetchMap { result in
            XCTAssertTrue(Thread.isMainThread)
            XCTAssertEqual(result.error, "API key is unavailable")
            key.fulfill()
        }
        wait(for: [missing, api, malformed, invalid, key], timeout: 3)
    }

    private func makeManager(_ key: @escaping () -> String) -> NetworkManager {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [NetworkFixtureProtocol.self]
        return NetworkManager(apiKeyProvider: key, session: URLSession(configuration: configuration))
    }
}

private final class NetworkFixtureProtocol: URLProtocol, @unchecked Sendable {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let url = request.url!
        let id = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?
            .first { $0.name == "id" }?.value
        let status: Int
        let data: Data
        if url.host == "example.com" {
            XCTAssertNil(request.value(forHTTPHeaderField: "X-CMC_PRO_API_KEY"))
            status = 200
            data = Data([0, 255, 127])
        } else {
            XCTAssertEqual(request.value(forHTTPHeaderField: "X-CMC_PRO_API_KEY"), "test-placeholder")
            status = id == "error" ? 401 : 200
            let body: String
            if id == "error" {
                body = #"{"status":{"error_code":1001,"error_message":"invalid test-placeholder or partial test-place"}}"#
            } else if id == "malformed" {
                body = "invalid json"
            } else if url.path.hasSuffix("/map") {
                body = #"{"status":{"error_code":0},"data":[{"id":1,"symbol":"BTC","name":"Bitcoin","slug":"bitcoin","rank":1},{"id":2,"symbol":"BTC","name":"Other Bitcoin","slug":"other-bitcoin","rank":null}]}"#
            } else if url.path.hasSuffix("/info") {
                body = #"{"status":{"error_code":0},"data":{"1":{"logo":"https://example.com/image"}}}"#
            } else {
                body = #"{"status":{"error_code":0},"data":{"1":{"quote":{"USD":{"price":12.5}}}}}"#
            }
            data = Data(body.utf8)
        }
        let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: nil)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
