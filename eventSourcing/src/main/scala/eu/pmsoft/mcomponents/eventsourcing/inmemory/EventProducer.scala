package eu.pmsoft.mcomponents.eventsourcing.inmemory

import eu.pmsoft.mcomponents.eventsourcing.atomic.Atomic
import eu.pmsoft.mcomponents.eventsourcing.{ EventStoreRange, VersionedEvent, EventStoreVersion, DomainSpecification }
import eu.pmsoft.mcomponents.eventsourcing.eventstore.{ EventStoreRead, EventStoreReference }
import rx.{ Producer, Subscriber }

import scalaz.{ -\/, \/-, \/ }

class EventProducer[D <: DomainSpecification](
    eventStoreReference: EventStoreReference[D],
    startFromVersion:    EventStoreVersion,
    subscriber:          Subscriber[_ >: VersionedEvent[D]]
) extends Producer {

  //public for testing
  val producerState: Atomic[ProducerState[D]] = Atomic(ProducerState[D](startFromVersion))

  def bindEventStore(eventStoreRead: EventStoreRead[D]): Unit = {
      def checkThatEventStoreIsNotBind(state: ProducerState[D]): \/[String, ProducerState[D]] = {
        state.status match {
          case Init                       => \/-(state)
          case EventStoreBind(eventStore) => -\/("event store already bind")
          case ErrorState(e)              => -\/("event store already bind")
          case Completed                  => -\/("event store already bind")
          case Shutdown                   => -\/("event store already bind")
        }
      }
      def updateEventStoreRef(state: ProducerState[D]): ProducerState[D] = {
        state.copy(status = EventStoreBind(eventStoreRead), stateVersion = state.stateVersion + 1)
      }
    producerState.updateAndGetWithCondition(updateEventStoreRef, checkThatEventStoreIsNotBind) match {
      case -\/(a) => //already bind, ignore event store reference
      case \/-(b) =>
        eventStoreRead.eventStoreVersionUpdates().subscribe(new Subscriber[EventStoreVersion]() {
          override def onError(e: Throwable): Unit = {
            producerState.updateAndGet { state =>
              state.copy(status = ErrorState(e), stateVersion = state.stateVersion + 1)
            }
            triggerEventPublication()
          }

          override def onCompleted(): Unit = {
            producerState.updateAndGet { state =>
              state.copy(status = Completed, stateVersion = state.stateVersion + 1)
            }
            triggerEventPublication()
          }

          override def onNext(t: EventStoreVersion): Unit = triggerEventPublication()
        })
        triggerEventPublication()
    }
  }

  override def request(n: Long): Unit = {
      def updateNrOfRequestedEvents(state: ProducerState[D]): ProducerState[D] = {
        val total: Long = state.nrOfRequestedEvents + n
        // check if overflow occurred
        val toSend = if (total < 0) {
          Long.MaxValue
        }
        else {
          total
        }
        state.copy(nrOfRequestedEvents = toSend, stateVersion = state.stateVersion + 1)
      }
    producerState.updateAndGet(updateNrOfRequestedEvents)
    triggerEventPublication()
  }

  def triggerEventPublication(): Unit = {
      def ensurePreviousStateWasNotSending(state: ProducerState[D]): \/[String, ProducerState[D]] = {
        state.sending match {
          case SendingInactive => {
            state.status match {
              case Init                       => -\/("nothing to send")
              case EventStoreBind(eventStore) => \/-(state)
              case ErrorState(e)              => \/-(state)
              case Completed                  => \/-(state)
              case Shutdown                   => -\/("stream closed")
            }
          }
          case SendingActive => -\/("already sending")
        }
      }
      def markAsSendingEvents(state: ProducerState[D]): ProducerState[D] = {
        state.copy(sending = SendingActive)
      }

    //increment to be aware of the triggered action
    producerState.updateAndGet { state =>
      state.copy(stateVersion = state.stateVersion + 1)
    }
    //try to enter to SendingActive state
    producerState.updateAndGetWithCondition(markAsSendingEvents, ensurePreviousStateWasNotSending) match {
      case -\/(a)            => // ignore trigger because sending was already active or status do not allow to send any thing
      case \/-(sendingState) => sendDataToStream(sendingState)
    }
  }

  private[this] def sendDataToStream(sendingState: ProducerState[D]): Unit = {

      def shutDownStream(state: ProducerState[D]): ProducerState[D] = {
        state.copy(
          status = Shutdown,
          sending = SendingInactive,
          stateVersion = state.stateVersion + 1
        )
      }

      def backToSendingInactive(state: ProducerState[D]): ProducerState[D] = {
        state.copy(
          sending = SendingInactive,
          active = !subscriber.isUnsubscribed,
          stateVersion = state.stateVersion + 1
        )
      }

    val updateFunction = sendingState.status match {
      case EventStoreBind(eventStore) =>
        if (sendingState.nrOfRequestedEvents > 0) {
          sendEvents(sendingState, eventStore.asInstanceOf[EventStoreRead[D]])
        }
        else {
          backToSendingInactive _
        }
      case ErrorState(e) => {
        subscriber.onError(e)
        shutDownStream _
      }
      case _ => {
        subscriber.onCompleted()
        shutDownStream _
      }
    }

    val afterSendState = producerState.updateAndGet(updateFunction)
    if (afterSendState.stateVersion != (sendingState.stateVersion + 1)) {
      //some triggers were ignored, so check the new state for sending data
      triggerEventPublication()
    }
  }

  def sendEvents(sendingState: ProducerState[D], eventStore: EventStoreRead[D]): ProducerState[D] => ProducerState[D] = {

    val toLoad = sendingState.sendingVersionMarker.add(sendingState.nrOfRequestedEvents)
    val loadRangeEnd = if (toLoad.storeVersion < 0) {
      None
    }
    else {
      Some(toLoad)
    }
    val range = EventStoreRange(sendingState.sendingVersionMarker, loadRangeEnd)
    val currentEventVersion = if (sendingState.sendingVersionMarker == EventStoreVersion.zero) {
      EventStoreVersion.zero.add(1)
    }
    else {
      sendingState.sendingVersionMarker
    }
    val events = eventStore.loadEvents(range)
    val lastEventVersion = (currentEventVersion /: events) {
      case (version, event) =>
        subscriber.onNext(VersionedEvent[D](version, event))
        version.add(1)
    }

      def updateStateAfterSend(state: ProducerState[D]): ProducerState[D] = {
        val requestTotal = if (state.nrOfRequestedEvents == Long.MaxValue) {
          Long.MaxValue
        }
        else {
          state.nrOfRequestedEvents - events.size
        }
        state.copy(
          sending = SendingInactive,
          sendingVersionMarker = lastEventVersion,
          active = !subscriber.isUnsubscribed,
          stateVersion = state.stateVersion + 1,
          nrOfRequestedEvents = requestTotal
        )
      }
    updateStateAfterSend _
  }

}

case class ProducerState[D <: DomainSpecification](
    sendingVersionMarker: EventStoreVersion,
    nrOfRequestedEvents:  Long                  = 0L,
    nrOfSendEvents:       Long                  = 0L,
    stateVersion:         Long                  = 0L,
    active:               Boolean               = true,
    sendingEvents:        Boolean               = false,
    error:                Option[Throwable]     = None,
    status:               ProducerStatus        = Init,
    sending:              ProducerSendingStatus = SendingInactive
) {
  assert(nrOfRequestedEvents >= 0)
}

sealed trait ProducerStatus

case object Init extends ProducerStatus

case class EventStoreBind[D <: DomainSpecification](eventStore: EventStoreRead[D]) extends ProducerStatus

case class ErrorState(e: Throwable) extends ProducerStatus

case object Completed extends ProducerStatus

case object Shutdown extends ProducerStatus

sealed trait ProducerSendingStatus

case object SendingInactive extends ProducerSendingStatus
case object SendingActive extends ProducerSendingStatus