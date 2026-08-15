package com.capacitorjs.plugins.localnotifications

import androidx.core.content.FileProvider

/**
 * Dedicated [FileProvider] used to serve notification sound files copied out of
 * the app's web assets. A distinct subclass (and authority) avoids clashing with
 * the host app's or other plugins' FileProviders.
 */
class LocalNotificationsAssetProvider : FileProvider()
