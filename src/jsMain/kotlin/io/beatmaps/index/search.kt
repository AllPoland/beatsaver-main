package io.beatmaps.index

import external.DateRangePicker
import external.Moment
import external.ReactSlider
import io.beatmaps.api.SearchOrder
import io.beatmaps.dateFormat
import kotlinx.browser.document
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.InputType
import kotlinx.html.id
import kotlinx.html.js.onChangeFunction
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.events.Event
import react.*
import react.dom.*

external interface SearchProps: RProps {
    var maxNps: Int
    var updateSearchParams: (SearchParams) -> Unit
}

data class SearchState(var minNps: Float = 0f, var maxNps: Float = 16f, val filterMap: MutableMap<FilterInfo, Boolean> = mutableMapOf(),
                       var order: SearchOrder = SearchOrder.Relevance, var focusedInput: String? = null, var startDate: Moment? = null,
                       var endDate: Moment? = null, var filtersOpen: Boolean = false) : RState
data class PresetDateRange(val startDate: Moment?, val endDate: Moment?)

val presets = mapOf(
    "Last 24h" to PresetDateRange(Moment().subtract(1, "day"), null),
    "Last week" to PresetDateRange(Moment().subtract(1, "week"), null),
    "Last month" to PresetDateRange(Moment().subtract(1, "month"), null),
    "Last 3 months" to PresetDateRange(Moment().subtract(3, "month"), null),
    "All" to PresetDateRange(null, null),
)

enum class FilterCategory {
    NONE, GENERAL, REQUIREMENTS
}
data class FilterInfo(val key: String, val name: String, val cat: FilterCategory, val fromParams: (SearchParams) -> Boolean)
val filters = listOf(
    FilterInfo("bot", "AI", FilterCategory.GENERAL) { it.automapper == true },
    FilterInfo("ranked", "Ranked", FilterCategory.GENERAL) { it.ranked == true },
    FilterInfo("fs", "Full Spread", FilterCategory.GENERAL) { it.fullSpread == true },

    FilterInfo("chroma", "Chroma", FilterCategory.REQUIREMENTS) { it.chroma == true },
    FilterInfo("noodle", "Noodle", FilterCategory.REQUIREMENTS) { it.noodle == true },
    FilterInfo("me", "Mapping Extensions", FilterCategory.REQUIREMENTS) { it.me == true },
    FilterInfo("cinema", "Cinema", FilterCategory.REQUIREMENTS) { it.cinema == true },
)

@JsExport
class Search : RComponent<SearchProps, SearchState>() {
    private val filterRefs = filters.associateWith { createRef<HTMLInputElement>() }

    private val inputRef = createRef<HTMLInputElement>()
    private val sortRef = createRef<HTMLSelectElement>()
    private val dropdownRef = createRef<HTMLButtonElement>()
    private val dropdownDivRef = createRef<HTMLDivElement>()

    init {
        state = SearchState()
    }

    override fun componentWillMount() {
        setState {
            maxNps = props.maxNps.toFloat()
        }
    }

    override fun componentDidMount() {
        dropdownRef.current?.addEventListener("mouseup", ::stopProp)
        dropdownDivRef.current?.addEventListener("mouseup", ::stopProp)
        document.addEventListener("mouseup", ::hideFilters)
    }

    private fun stopProp(it: Event) {
        it.stopPropagation()
    }

    private fun hideFilters(it: Event) {
        setState {
            filtersOpen = false
        }
    }

    override fun componentWillUnmount() {
        document.removeEventListener("mouseup", ::hideFilters)
        dropdownDivRef.current?.addEventListener("mouseup", ::stopProp)
        dropdownRef.current?.removeEventListener("mouseup", ::stopProp)
    }

    private fun RDOMBuilder<DIV>.toggle(id: String, text: String, localRef: RReadableRef<HTMLInputElement>, block: (Boolean) -> Unit) {
        div("custom-control custom-switch") {
            input(InputType.checkBox, classes = "custom-control-input") {
                attrs.id = id
                ref = localRef
                attrs.onChangeFunction = {
                    block(localRef.current?.checked ?: false)
                }
            }
            label("custom-control-label") {
                attrs.htmlFor = id
                +text
            }
        }
    }

    private fun RDOMBuilder<DIV>.slider(text: String, currentMin: Float, currentMax: Float, max: Int, block: (Array<Int>) -> Unit) {
        div("form-group col-sm-3") {
            val maxSlider = max * 10
            ReactSlider.default {
                attrs.ariaLabel = arrayOf("Min NPS", "Max NPS")
                attrs.value = arrayOf((state.minNps * 10).toInt(), (state.maxNps * 10).toInt())
                attrs.max = maxSlider
                attrs.defaultValue = arrayOf(0, maxSlider)
                attrs.minDistance = 5
                attrs.onChange = block
            }
            p("m-0 float-left") {
                +text
            }
            p("m-0 float-right") {
                val maxStr = if (currentMax >= max) "∞" else currentMax.toString()
                +"$currentMin - $maxStr"
            }
        }
    }

    fun updateUI(fromParams: SearchParams) {
        inputRef.current?.value = fromParams.search
        sortRef.current?.selectedIndex = fromParams.sortOrder.idx
        setState {
            filterRefs.forEach {
                val newState = it.key.fromParams(fromParams)
                it.value.current?.checked = newState
                filterMap[it.key] = newState
            }

            minNps = fromParams.minNps ?: 0f
            maxNps = fromParams.maxNps ?: props.maxNps.toFloat()
            order = fromParams.sortOrder
            startDate = fromParams.from?.let { Moment(it) }
            endDate = fromParams.to?.let { Moment(it) }
        }
    }

    private fun isFiltered(s: String) =
        filters.first { it.key == s }.let { filter ->
            state.filterMap.getOrElse(filter) { false }
        }

    override fun RBuilder.render() {
        form("") {
            div("row") {
                div("form-group col-lg-9") {
                    input(InputType.search, classes = "form-control") {
                        attrs.placeholder = "Search"
                        attrs.attributes["aria-label"] = "Search"
                        ref = inputRef
                    }
                }
                div("form-group col-lg-3") {
                    button(type = ButtonType.submit, classes = "btn btn-block btn-primary") {
                        attrs.onClickFunction = {
                            it.preventDefault()
                            props.updateSearchParams(
                                SearchParams(
                                    inputRef.current?.value ?: "",
                                    if (isFiltered("bot")) true else null,
                                    if (state.minNps > 0) state.minNps else null,
                                    if (state.maxNps < props.maxNps) state.maxNps else null,
                                    if (isFiltered("chroma")) true else null,
                                    state.order,
                                    state.startDate?.format(dateFormat),
                                    state.endDate?.format(dateFormat),
                                    if (isFiltered("noodle")) true else null,
                                    if (isFiltered("ranked")) true else null,
                                    if (isFiltered("fs")) true else null,
                                    if (isFiltered("me")) true else null,
                                    if (isFiltered("cinema")) true else null
                                )
                            )
                        }
                        +"Search"
                    }
                }
            }
            div("row") {
                div("form-group col-sm-3 text-center") {
                    button(classes = "filter-dropdown") {
                        attrs.onClickFunction = {
                            it.preventDefault()
                            setState {
                                filtersOpen = !state.filtersOpen
                            }
                        }
                        ref = dropdownRef
                        span {
                            val filters = filterRefs.filter { state.filterMap.getOrElse(it.key) { false } }.map { it.key.key }

                            if (filters.isEmpty()) {
                                +"Filters"
                            } else {
                                +filters.joinToString(",")
                            }
                        }
                        i("fas fa-angle-" + if (state.filtersOpen) "up" else "down") {}
                    }
                    div("dropdown-menu" + if (state.filtersOpen) " show" else "") {
                        ref = dropdownDivRef

                        filterRefs.entries.fold(FilterCategory.NONE) { cat, filter ->
                            if (cat != filter.key.cat) {
                                h4(if (cat == FilterCategory.NONE) "" else "mt-4") {
                                    +filter.key.cat.toString()
                                }
                            }

                            toggle(filter.key.key, filter.key.name, filter.value) {
                                setState {
                                    state.filterMap[filter.key] = it
                                }
                            }

                            filter.key.cat
                        }
                    }
                }
                slider("NPS", state.minNps, state.maxNps, props.maxNps) {
                    setState {
                        minNps = it[0] / 10f
                        maxNps = it[1] / 10f
                    }
                }
                div("form-group col-sm-3") {
                    DateRangePicker.default {
                        attrs.startDate = state.startDate
                        attrs.endDate = state.endDate
                        attrs.startDateId = "startobj"
                        attrs.endDateId = "endobj"
                        attrs.onFocusChange = {
                            setState {
                                focusedInput = it
                            }
                        }
                        attrs.onDatesChange = {
                            setState {
                                startDate = it.startDate
                                endDate = it.endDate
                            }
                        }
                        attrs.isOutsideRange = { false }
                        attrs.focusedInput = state.focusedInput
                        attrs.displayFormat = "DD/MM/YYYY"
                        attrs.small = true
                        attrs.numberOfMonths = 1
                        attrs.renderCalendarInfo = {
                            div("presets") {
                                presets.forEach { preset ->
                                    button {
                                        attrs.onClickFunction = {
                                            it.preventDefault()
                                            setState {
                                                startDate = preset.value.startDate
                                                endDate = preset.value.endDate
                                            }
                                        }
                                        +preset.key
                                    }
                                }
                            }
                        }
                    }
                }
                div("form-group col-sm-3") {
                    select("form-control") {
                        ref = sortRef
                        attrs.attributes["aria-label"] = "Sort by"
                        attrs.onChangeFunction = {
                            setState {
                                order = SearchOrder.fromInt(sortRef.current?.selectedIndex ?: 0) ?: SearchOrder.Relevance
                            }
                        }
                        attrs.value = state.order.toString()
                        SearchOrder.values().forEach {
                            option {
                                +it.toString()
                            }
                        }
                    }
                }
            }
        }
    }
}

fun RBuilder.search(handler: SearchProps.() -> Unit): ReactElement {
    return child(Search::class) {
        this.attrs(handler)
    }
}