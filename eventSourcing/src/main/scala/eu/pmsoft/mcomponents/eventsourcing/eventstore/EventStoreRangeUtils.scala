package eu.pmsoft.mcomponents.eventsourcing.eventstore

import eu.pmsoft.mcomponents.eventsourcing.EventStoreRange

object EventStoreRangeUtils {

  def extractRangeFromList[E](events: List[E], range: EventStoreRange): List[E] = {
    val startFrom = range.from.storeVersion.toInt
    val toDrop = Math.max(startFrom - 1, 0)

      def dropFrom(events: List[E]): List[E] = {
        if (toDrop > 0) {
          events.drop(toDrop)
        }
        else {
          events
        }
      }
      def takeLimit(events: List[E]): List[E] = range.to match {
        case Some(limit) => events.take((limit.storeVersion - toDrop).toInt)
        case None        => events
      }
    takeLimit(dropFrom(events))
  }
}