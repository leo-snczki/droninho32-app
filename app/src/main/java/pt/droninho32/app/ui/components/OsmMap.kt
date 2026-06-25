package pt.droninho32.app.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Mapa OpenStreetMap (osmdroid) reutilizável.
 *
 * @param route     pontos da rota a desenhar como polilinha.
 * @param livePoint posição "ao vivo" do drone (marcador), opcional.
 * @param follow    se true, centra a câmara no [livePoint] sempre que muda.
 *
 * O [MapView] é um View clássico embrulhado em AndroidView. O seu ciclo de vida
 * (onResume/onPause/onDetach) é gerido pelo DisposableEffect.
 */
@Composable
fun OsmMap(
    modifier: Modifier = Modifier,
    route: List<GeoPoint> = emptyList(),
    livePoint: GeoPoint? = null,
    follow: Boolean = false,
    markerTitle: String = "Drone",
) {
    val context = LocalContext.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
        }
    }

    val polyline = remember { Polyline().apply { outlinePaint.color = AndroidColor.rgb(0x15, 0x65, 0xC0); outlinePaint.strokeWidth = 8f } }
    val marker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            // Atualiza a polilinha da rota.
            view.overlays.remove(polyline)
            if (route.isNotEmpty()) {
                polyline.setPoints(route)
                view.overlays.add(polyline)
            }

            // Atualiza o marcador "ao vivo".
            view.overlays.remove(marker)
            val center = livePoint ?: route.lastOrNull() ?: route.firstOrNull()
            if (livePoint != null) {
                marker.position = livePoint
                marker.title = markerTitle
                view.overlays.add(marker)
            }

            if (center != null) {
                if (follow && livePoint != null) {
                    view.controller.animateTo(center)
                } else {
                    // Apenas centra uma vez se a câmara ainda estiver na posição inicial (0,0).
                    val mapCenter = view.mapCenter
                    if (mapCenter.latitude == 0.0 && mapCenter.longitude == 0.0) {
                        view.controller.setCenter(center)
                    }
                }
            }
            view.invalidate()
        },
    )
}
