package io.beatmaps.maps

import external.Axios
import external.generateConfig
import io.beatmaps.api.CurateMap
import io.beatmaps.api.MapDetail
import io.beatmaps.api.MapInfoUpdate
import io.beatmaps.api.StateUpdate
import io.beatmaps.common.api.EMapState
import io.beatmaps.index.ModalButton
import io.beatmaps.index.ModalComponent
import io.beatmaps.index.ModalData
import io.beatmaps.index.links
import kotlinx.browser.window
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.id
import kotlinx.html.js.onClickFunction
import kotlinx.html.title
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RReadableRef
import react.RState
import react.ReactElement
import react.createRef
import react.dom.InnerHTML
import react.dom.RDOMBuilder
import react.dom.a
import react.dom.div
import react.dom.i
import react.dom.img
import react.dom.input
import react.dom.p
import react.dom.textarea
import react.setState
import kotlin.collections.set

external interface MapInfoProps : RProps {
    var mapInfo: MapDetail
    var isOwner: Boolean
    var isAdmin: Boolean?
    var modal: RReadableRef<ModalComponent>
    var reloadMap: () -> Unit
    var deleteMap: () -> Unit
    var updateMapinfo: (MapDetail) -> Unit
}

data class MapInfoState(var loading: Boolean = false, var editing: Boolean = false) : RState

@JsExport
class MapInfo : RComponent<MapInfoProps, MapInfoState>() {
    private val inputRef = createRef<HTMLInputElement>()
    private val textareaRef = createRef<HTMLTextAreaElement>()
    private val reasonRef = createRef<HTMLTextAreaElement>()

    init {
        state = MapInfoState()
    }

    private fun recall() {
        props.mapInfo.publishedVersion()?.hash?.let { hash ->
            Axios.post<String>("/api/testplay/state", StateUpdate(hash, EMapState.Uploaded, props.mapInfo.intId(), reasonRef.current?.asDynamic()?.value as? String), generateConfig<StateUpdate, String>())
                .then({
                    props.reloadMap()

                    setState {
                        loading = false
                    }
                }) {
                    setState {
                        loading = false
                    }
                }
        }
    }

    private fun delete() {
        Axios.post<String>("/api/maps/update", MapInfoUpdate(props.mapInfo.intId(), deleted = true, reason = reasonRef.current?.asDynamic()?.value as? String), generateConfig<MapInfoUpdate, String>()).then({
            props.deleteMap()
        }) {
            setState {
                loading = false
            }
        }
    }

    private fun curate(curated: Boolean = true) {
        Axios.post<String>("/api/maps/curate", CurateMap(props.mapInfo.intId(), curated), generateConfig<CurateMap, String>()).then({
            props.reloadMap()
        }) {
            setState {
                loading = false
            }
        }
    }

    override fun RBuilder.render() {
        val deleted = props.mapInfo.deletedAt != null
        div("card") {
            div("card-header d-flex" + if (deleted) " bg-danger" else "") {
                if (state.editing) {
                    +"Edit map"
                } else {
                    +props.mapInfo.name
                }
                div("ml-auto") {
                    if (!deleted) {
                        props.mapInfo.mainVersion()?.let { version ->
                            links {
                                attrs.map = props.mapInfo
                                attrs.version = version
                                attrs.modal = props.modal
                            }

                            val adminLocal = props.isAdmin

                            if (adminLocal == true || props.isOwner) {
                                a("#") {
                                    attrs.title = "Edit"
                                    attrs.attributes["aria-label"] = "Edit"
                                    attrs.onClickFunction = {
                                        it.preventDefault()
                                        setState {
                                            editing = !state.editing
                                        }
                                        window.setTimeout(
                                            {
                                                inputRef.current?.value = props.mapInfo.name
                                            },
                                            1
                                        )
                                    }
                                    i("fas fa-pen text-warning") { }
                                }
                            }

                            if (adminLocal == true) {
                                a("#") {
                                    attrs.title = "Curate"
                                    attrs.attributes["aria-label"] = "Curate"
                                    attrs.onClickFunction = {
                                        it.preventDefault()
                                        curate(props.mapInfo.curator == null)
                                    }
                                    i("fas fa-award " + if (props.mapInfo.curator != null) "text-danger" else "text-success") { }
                                }
                                a("#") {
                                    attrs.title = "Delete"
                                    attrs.attributes["aria-label"] = "Delete"
                                    attrs.onClickFunction = {
                                        it.preventDefault()
                                        props.modal.current?.showDialog(
                                            ModalData(
                                                "Delete map",
                                                bodyCallback = {
                                                    p {
                                                        +"Delete completely so that no more versions can be added or just unpublish the current version?"
                                                    }
                                                    p {
                                                        +"Reason for action:"
                                                    }
                                                    textarea(classes = "form-control") {
                                                        ref = reasonRef
                                                    }
                                                },
                                                buttons = listOf(ModalButton("DELETE", "danger", ::delete), ModalButton("Unpublish", "primary", ::recall), ModalButton("Cancel"))
                                            )
                                        )
                                    }
                                    i("fas fa-trash text-danger") { }
                                }
                            }
                        }
                    }
                }
            }
            div("card-body mapinfo") {
                img("Cover Image", props.mapInfo.mainVersion()?.coverURL) {
                    attrs.width = "200"
                    attrs.height = "200"
                }
                div("card-text clearfix") {
                    if (state.editing) {
                        input(InputType.text, classes = "form-control m-2") {
                            attrs.id = "name"
                            attrs.disabled = state.loading
                            ref = inputRef
                        }
                        textarea("10", classes = "form-control m-2") {
                            attrs.id = "description"
                            attrs.disabled = state.loading
                            +props.mapInfo.description
                            ref = textareaRef
                        }
                    } else {
                        textToContent(props.mapInfo.description)
                    }
                }

                if (state.editing) {
                    div("text-right") {
                        if (props.isOwner) {
                            if (props.mapInfo.publishedVersion() != null) {
                                a(classes = "btn btn-danger m-1") {
                                    attrs.onClickFunction = {
                                        props.modal.current?.showDialog(
                                            ModalData(
                                                "Are you sure?",
                                                "This will hide your map from other players until you publish a new version",
                                                listOf(ModalButton("OK", "primary", ::recall), ModalButton("Cancel"))
                                            )
                                        )
                                    }
                                    +"Unpublish"
                                }
                            } else {
                                a(classes = "btn btn-danger m-1") {
                                    attrs.onClickFunction = {
                                        props.modal.current?.showDialog(
                                            ModalData(
                                                "Are you sure?",
                                                "You won't be able to recover this map after you delete it",
                                                listOf(ModalButton("DELETE", "primary", ::delete), ModalButton("Cancel"))
                                            )
                                        )
                                    }
                                    +"Delete"
                                }
                            }
                        }
                        a(classes = "btn btn-primary m-1") {
                            attrs.onClickFunction = {
                                val newTitle = inputRef.current?.value ?: ""
                                val newDescription = textareaRef.current?.asDynamic().value as String

                                setState {
                                    loading = true
                                }

                                Axios.post<String>("/api/maps/update", MapInfoUpdate(props.mapInfo.intId(), newTitle, newDescription), generateConfig<MapInfoUpdate, String>()).then({
                                    props.updateMapinfo(props.mapInfo.copy(name = newTitle, description = newDescription))
                                    setState {
                                        loading = false
                                        editing = false
                                    }
                                }) {
                                    setState {
                                        loading = false
                                    }
                                }
                            }
                            +"Save"
                        }
                    }
                }
            }
        }
    }
}

fun String.transformURLIntoLinks() =
    replace("\\b((https?|ftp):\\/\\/)?[-a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[A-Za-z]{2,6}\\b(\\/[-a-zA-Z0-9@:%_\\+.~#?&//=]*)*(?:\\/|\\b)".toRegex()) {
        if (it.groupValues[1].isEmpty()) it.value else "<a target=\"_blank\" href=\"${it.value}\">${it.value}</a>"
    }

fun RBuilder.mapInfo(handler: MapInfoProps.() -> Unit): ReactElement {
    return child(MapInfo::class) {
        this.attrs(handler)
    }
}

// Kotlin IR be adding underscores everywhere
class DangerousHtml(override var __html: String) : InnerHTML

fun <T : Tag> RDOMBuilder<T>.textToContent(text: String) {
    domProps.dangerouslySetInnerHTML = DangerousHtml(
        text
            .replace(Regex("<[^>]+>"), "")
            .transformURLIntoLinks()
            .replace("\n", "<br />")
    )
}
