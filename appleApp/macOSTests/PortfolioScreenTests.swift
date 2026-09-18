import Common
import XCTest
import AppKit
import SwiftUI

final class PortfolioScreenTests: XCTestCase {
    @MainActor
    func testOpeningPortfolioReloadsStorageOnMacOS() {
        let storage = PortfolioStorageFixture()
        let coordinator = PortfolioCoordinator(assetCatalog: nil, imageLoader: nil,
            logoUrlProvider: { _ in nil }, marketPriceSource: nil, portfolioStorage: storage)
        let initialLoad = expectation(description: "initial load")
        DispatchQueue.main.async { initialLoad.fulfill() }
        wait(for: [initialLoad], timeout: 3)

        // The view model loads once at construction; each screen appearance must load again.
        // This fails when PortfolioScreen's lifecycle callbacks are restricted to iOS.
        for _ in 0..<2 {
            let loaded = expectation(description: "screen reload")
            loaded.assertForOverFulfill = false
            storage.onLoad = { loaded.fulfill() }
            let controller = NSHostingController(rootView: PortfolioScreen(coordinator: coordinator))
            let window = NSWindow(contentViewController: controller)
            window.setContentSize(NSSize(width: 800, height: 600))
            window.orderFront(nil)
            wait(for: [loaded], timeout: 3)
            let view = controller.view
            if let bitmap = view.bitmapImageRepForCachingDisplay(in: view.bounds) {
                view.cacheDisplay(in: view.bounds, to: bitmap)
                if let png = bitmap.representation(using: .png, properties: [:]) {
                    let attachment = XCTAttachment(data: png, uniformTypeIdentifier: "public.png")
                    attachment.name = "macOS-portfolio"
                    attachment.lifetime = .keepAlways
                    add(attachment)
                }
            }
            storage.onLoad = nil
            window.orderOut(nil)
            window.contentViewController = nil
        }
    }
}

private final class PortfolioStorageFixture: PortfolioStorage {
    var onLoad: (() -> Void)?

    func loadPositions(completionHandler: @escaping (StorageResult<NSArray>?, Error?) -> Void) {
        completionHandler(StorageResult(value: NSArray(), failure: nil), nil)
        onLoad?()
    }

    func savePosition(position: PortfolioPosition,
                      completionHandler: @escaping (StorageResult<KotlinUnit>?, Error?) -> Void) {
        completionHandler(StorageResult(value: nil, failure: .write), nil)
    }

    func deletePosition(assetIdentity: AssetIdentity,
                        completionHandler: @escaping (StorageResult<KotlinUnit>?, Error?) -> Void) {
        completionHandler(StorageResult(value: nil, failure: .write), nil)
    }

    func loadLastQuote(assetIdentity: AssetIdentity,
                       completionHandler: @escaping (StorageResult<AssetQuote>?, Error?) -> Void) {
        completionHandler(StorageResult(value: nil, failure: .notFound), nil)
    }

    func saveLastQuote(quote: AssetQuote,
                       completionHandler: @escaping (StorageResult<KotlinUnit>?, Error?) -> Void) {
        completionHandler(StorageResult(value: nil, failure: .write), nil)
    }

    func close() {}
}
