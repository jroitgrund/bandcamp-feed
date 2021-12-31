package me.roitgrund.bandcampfeed

import BandcampClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import org.flywaydb.core.Flyway

fun updatePrefixesInBackground(storage: Storage, dbUrl: String, bandcampClient: BandcampClient) {
  CoroutineScope(
          Dispatchers.Default +
              Job() +
              CoroutineExceptionHandler { _, t ->
                BandcampFeedServer.log.error("Unrecoverable error", t)
              })
      .launch {
        Flyway.configure().dataSource(dbUrl, "", "").load().migrate()
        var prefix: BandcampPrefix? = null
        while (true) {
          try {
            prefix = storage.getNextPrefix(prefix)
            if (prefix != null) {
              BandcampFeedServer.log.info("Updating prefix {}", prefix.prefix)
              bandcampClient
                  .getReleases(prefix)
                  .takeWhile { !storage.isReleasePresent(ReleaseId(it.id)) }
                  .asFlow()
                  .map { bandcampClient.getRelease(it) }
                  .collect { storage.addRelease(prefix, it) }
            } else {
              BandcampFeedServer.log.info("No prefix to update")
            }
          } catch (e: RuntimeException) {
            BandcampFeedServer.log.error("Error", e)
          }
          delay(1000)
        }
      }
}
