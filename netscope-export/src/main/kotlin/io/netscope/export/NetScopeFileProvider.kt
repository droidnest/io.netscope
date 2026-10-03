package io.netscope.export

import androidx.core.content.FileProvider

/** Own subclass so the manifest entry never clashes with a host app's FileProvider. */
class NetScopeFileProvider : FileProvider()
