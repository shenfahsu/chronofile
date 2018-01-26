package com.chaidarun.chronofile

import com.jakewharton.rxrelay2.BehaviorRelay
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import org.jetbrains.anko.toast

/** All actions must be immutable */
sealed class Action {
  data class AddEntry(
    val activity: String,
    val note: String?,
    val latLong: List<Double>?
  ) : Action()

  data class EditEntry(
    val oldStartTime: Long,
    val newStartTime: String,
    val activity: String,
    val note: String
  ) : Action()

  data class RemoveEntries(val entries: Collection<Long>) : Action()
  data class SetConfigFromText(val text: String) : Action()
  data class SetConfigFromFile(val config: Config) : Action()
  data class SetGraphGrouping(val grouped: Boolean) : Action()
  data class SetGraphIncludeSleep(val includeSleep: Boolean) : Action()
  data class SetGraphMetric(val metric: Metric) : Action()
  data class SetGraphRangeEnd(val timestamp: Long) : Action()
  data class SetGraphRangeStart(val timestamp: Long) : Action()
  data class SetHistory(val history: History) : Action()
}

/** This class must be deeply immutable and preferably printable */
data class State(
  val config: Config? = null,
  val history: History? = null,
  val graphConfig: GraphConfig = GraphConfig()
)

private val reducer: (State, Action) -> State = { state, action ->
  with(state) {
    val start = System.currentTimeMillis()
    val nextState = when (action) {
      is Action.AddEntry -> copy(
        history = history?.withNewEntry(
          action.activity, action.note, action.latLong
        )
      )
      is Action.EditEntry -> copy(
        history = history?.withEditedEntry(
          action.oldStartTime, action.newStartTime, action.activity, action.note
        )
      )
      is Action.RemoveEntries -> copy(history = history?.withoutEntries(action.entries))
      is Action.SetConfigFromText -> {
        try {
          val config = Config.fromText(action.text)
          App.ctx.toast("Saved config")
          copy(config = config)
        } catch (e: Throwable) {
          App.ctx.toast("Failed to save invalid config")
          this
        }
      }
      is Action.SetConfigFromFile -> copy(config = action.config)
      is Action.SetGraphGrouping -> copy(
        graphConfig = graphConfig.copy(grouped = action.grouped)
      )
      is Action.SetGraphIncludeSleep -> copy(
        graphConfig = graphConfig.copy(includeSleep = action.includeSleep)
      )
      is Action.SetGraphMetric -> copy(graphConfig = graphConfig.copy(metric = action.metric))
      is Action.SetGraphRangeEnd -> {
        val timestamp = action.timestamp
        val newSettings = if (timestamp >= (state.graphConfig.startTime ?: 0)) {
          graphConfig.copy(endTime = timestamp)
        } else {
          graphConfig.copy(endTime = timestamp, startTime = timestamp)
        }
        copy(graphConfig = newSettings)
      }
      is Action.SetGraphRangeStart -> {
        val timestamp = action.timestamp
        val newSettings = if (timestamp <= (state.graphConfig.endTime ?: Long.MAX_VALUE)) {
          graphConfig.copy(startTime = timestamp)
        } else {
          graphConfig.copy(endTime = timestamp, startTime = timestamp)
        }
        copy(graphConfig = newSettings)
      }
      is Action.SetHistory -> copy(history = action.history)
    }

    // Print reduction stats
    val elapsed = System.currentTimeMillis() - start
    val stateDiff = with(dumbDiff(this, nextState)) {
      "`${ellipsize(this.first)}` => `${ellipsize(this.second)}`"
    }
    val message = "Reduced ${ellipsize(action)} in $elapsed ms. State diff: $stateDiff"
    logDW(message, elapsed > 20)
    nextState
  }
}

/** API heavily inspired by Redux */
object Store {

  private val stateRelay: BehaviorRelay<State> = BehaviorRelay.create()
  private val actionRelay = PublishRelay.create<Action>().apply {
    scan(State(), reducer).distinctUntilChanged().subscribe { stateRelay.accept(it) }
  }

  val state: State
    get() = stateRelay.value

  val observable: Observable<State>
    get() = stateRelay

  fun dispatch(action: Action) = actionRelay.accept(action)
}
