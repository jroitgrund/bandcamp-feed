package me.roitgrund.bandcampfeed

import BandcampClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf

@FlowPreview
fun updatePrefixesInBackground(storage: SqlStorage, bandcampClient: BandcampClient) {
  CoroutineScope(
          Dispatchers.Default +
              Job() +
              CoroutineExceptionHandler { _, t ->
                BandcampFeedServer.log.error("Unrecoverable error", t)
              })
      .launch {
        var prefix: String? = null
        while (true) {
          try {
            prefix = storage.getNextPrefix(prefix)
            if (prefix != null) {
              BandcampFeedServer.log.info("Updating prefix {}", prefix)
              bandcampClient
                  .getReleases(prefix)
                  .takeWhile { !storage.isReleasePresent(it.id) }
                  .asFlow()
                  .flatMapConcat {
                    try {
                      flowOf(bandcampClient.getRelease(it))
                    } catch (e: Exception) {
                      BandcampFeedServer.log.error("Error with release {}", it.url, e)
                      kotlinx.coroutines.flow.emptyFlow()
                    }
                  }
                  .collect { storage.addRelease(prefix, it) }
            } else {
              BandcampFeedServer.log.info("No prefix to update")
            }
          } catch (e: Throwable) {
            BandcampFeedServer.log.error("Error", e)
          }
        }
      }
}
