package io.netscope.export

import androidx.core.content.FileProvider

/** Its own class, so that it does not collide with a `FileProvider` the app declares. */
internal class NetScopeFileProvider : FileProvider()
