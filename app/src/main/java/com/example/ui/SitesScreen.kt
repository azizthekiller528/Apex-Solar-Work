package com.example.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.SiteLocationRepository
import com.example.data.model.SiteLocation
import com.example.data.model.SiteLocationParser
import com.example.ui.theme.LiquidGlassCard
import com.example.ui.theme.LiquidGlassPillButton
import com.example.ui.theme.LiquidGlassTone
import com.example.ui.theme.liquidGlassSurface
import com.example.ui.theme.liquidSquircleShape
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitesScreen(
    sites: List<SiteLocation>,
    isSaving: Boolean,
    onSaveSite: (existingId: String?, name: String, siteType: String, address: String, locationInput: String, notes: String, isPinned: Boolean) -> Unit,
    onTogglePin: (SiteLocation) -> Unit,
    onDeleteSite: (SiteLocation) -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier,
    topContentPadding: Dp = 12.dp,
    bottomContentPadding: Dp = 104.dp
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingSite by remember { mutableStateOf<SiteLocation?>(null) }
    var pendingDeleteSite by remember { mutableStateOf<SiteLocation?>(null) }

    fun launchMapsUri(primaryUri: String, fallbackUri: String, preferGoogleMapsApp: Boolean = true) {
        if (primaryUri.isBlank() && fallbackUri.isBlank()) {
            onFeedback("No GPS coordinates or Google Maps link saved for this site.")
            return
        }
        val uri = Uri.parse(primaryUri.ifBlank { fallbackUri })
        if (preferGoogleMapsApp) {
            try {
                val mapsIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                context.startActivity(mapsIntent)
                return
            } catch (_: ActivityNotFoundException) {
                // Fall back to default browser or maps handler
            }
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUri.ifBlank { primaryUri })))
        } catch (_: Exception) {
            onFeedback("Could not open Maps app or browser on this phone.")
        }
    }

    fun openInGoogleMaps(site: SiteLocation) {
        val rawUrl = site.googleMapsUrl.trim()
        val geoUri = if (site.hasCoordinates) {
            String.format(
                Locale.US,
                "geo:%.6f,%.6f?q=%.6f,%.6f(%s)",
                site.latitude!!,
                site.longitude!!,
                site.latitude,
                site.longitude,
                Uri.encode(site.name)
            )
        } else {
            ""
        }
        val webFallback = site.canonicalMapsWebUrl()
        // If user pasted a direct Google Maps link (like maps.app.goo.gl), open it in Maps so the exact Place pin opens; otherwise use geo URI
        if (rawUrl.isNotBlank()) {
            launchMapsUri(primaryUri = rawUrl, fallbackUri = webFallback)
        } else {
            launchMapsUri(primaryUri = geoUri, fallbackUri = webFallback)
        }
    }

    fun openDirections(site: SiteLocation) {
        if (site.hasCoordinates) {
            val navUri = String.format(Locale.US, "google.navigation:q=%.6f,%.6f", site.latitude!!, site.longitude!!)
            val webDir = String.format(
                Locale.US,
                "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f",
                site.latitude,
                site.longitude
            )
            launchMapsUri(primaryUri = navUri, fallbackUri = webDir)
        } else {
            openInGoogleMaps(site)
        }
    }

    fun shareSiteLocation(site: SiteLocation) {
        val shareText = SiteLocationRepository.formatWhatsAppShareText(site)
        try {
            val waIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                setPackage("com.whatsapp")
            }
            context.startActivity(waIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                val genericIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(genericIntent, "Share Site Location"))
            } catch (_: Exception) {
                onFeedback("No sharing app found")
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topContentPadding,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "sites_summary_header") {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = liquidSquircleShape(24.dp),
                    tone = LiquidGlassTone.CARD
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .liquidGlassSurface(
                                        shape = RoundedCornerShape(14.dp),
                                        tone = LiquidGlassTone.ACCENT
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warehouse,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${sites.size} Saved Site Location${if (sites.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Store GPS pins & Google Maps links synced live.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        LiquidGlassPillButton(
                            onClick = {
                                editingSite = null
                                showEditorDialog = true
                            },
                            tone = LiquidGlassTone.ACCENT,
                            elevation = 6.dp,
                            backdrop = null,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_site_fab")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(19.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Site", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (sites.isEmpty()) {
                item {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = liquidSquircleShape(24.dp),
                        tone = LiquidGlassTone.CARD
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "No site locations added yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tap + Add Site above to save a GPS coordinate or Google Maps link for drivers and field team.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(sites, key = { it.id }) { site ->
                SiteLocationCard(
                    site = site,
                    onOpenMaps = { openInGoogleMaps(site) },
                    onDirections = { openDirections(site) },
                    onCopyGps = {
                        val copyText = if (site.hasCoordinates) {
                            site.formattedCoordinates()
                        } else {
                            site.googleMapsUrl
                        }
                        clipboard.setText(AnnotatedString(copyText))
                        onFeedback("Copied GPS / Maps link to clipboard")
                    },
                    onShare = { shareSiteLocation(site) },
                    onTogglePin = { onTogglePin(site) },
                    onEdit = {
                        editingSite = site
                        showEditorDialog = true
                    },
                    onDelete = { pendingDeleteSite = site }
                )
            }
        }
    }

    if (showEditorDialog) {
        SiteEditorDialog(
            initial = editingSite,
            onDismiss = {
                showEditorDialog = false
                editingSite = null
            },
            onSave = { name, siteType, address, locationInput, notes, isPinned ->
                val targetId = editingSite?.id
                showEditorDialog = false
                editingSite = null
                onSaveSite(targetId, name, siteType, address, locationInput, notes, isPinned)
            }
        )
    }

    pendingDeleteSite?.let { siteToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSite = null },
            title = { Text("Delete site location?") },
            text = { Text("Remove \"${siteToDelete.name}\" from stored site locations across all synced phones?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteSite = null
                        onDeleteSite(siteToDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSite = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SiteLocationCard(
    site: SiteLocation,
    onOpenMaps: () -> Unit,
    onDirections: () -> Unit,
    onCopyGps: () -> Unit,
    onShare: () -> Unit,
    onTogglePin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isStore = site.siteType == SiteLocation.TYPE_STORE
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("site_card_${site.id}"),
        shape = RoundedCornerShape(24.dp),
        tone = LiquidGlassTone.CARD
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (isStore) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        shape = CircleShape
                    ) {
                        Text(
                            text = SiteLocation.typeDisplayLabel(site.siteType).uppercase(Locale.US),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isStore) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                    if (site.isPinned) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "PINNED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (site.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin site",
                            tint = if (site.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit site",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete site",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = site.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (site.addressOrVillage.isNotBlank()) {
                    Text(
                        text = site.addressOrVillage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // GPS Coordinates / Link readout well
            if (site.hasCoordinates || site.googleMapsUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassSurface(
                            shape = RoundedCornerShape(16.dp),
                            tone = LiquidGlassTone.SUBTLE
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (site.hasCoordinates) {
                                Text(
                                    text = "GPS: ${site.formattedCoordinates()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (site.googleMapsUrl.isNotBlank()) {
                                Text(
                                    text = site.googleMapsUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = onCopyGps, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy GPS",
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (site.notes.isNotBlank()) {
                Text(
                    text = site.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1-Tap Google Maps Redirect & Driver Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassPillButton(
                    onClick = onOpenMaps,
                    tone = LiquidGlassTone.ACCENT,
                    elevation = 4.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_maps_btn_${site.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Google Maps",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                LiquidGlassPillButton(
                    onClick = onDirections,
                    tone = LiquidGlassTone.SUBTLE,
                    elevation = 0.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = "Directions",
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Go", maxLines = 1, softWrap = false, fontWeight = FontWeight.Medium)
                }

                LiquidGlassPillButton(
                    onClick = onShare,
                    tone = LiquidGlassTone.SUBTLE,
                    elevation = 0.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share on WhatsApp",
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteEditorDialog(
    initial: SiteLocation?,
    onDismiss: () -> Unit,
    onSave: (name: String, siteType: String, address: String, locationInput: String, notes: String, isPinned: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var siteType by remember { mutableStateOf(initial?.siteType ?: SiteLocation.TYPE_SOLAR_SITE) }
    var address by remember { mutableStateOf(initial?.addressOrVillage ?: "") }
    var locationInput by remember {
        mutableStateOf(
            when {
                initial == null -> ""
                initial.googleMapsUrl.isNotBlank() && initial.hasCoordinates ->
                    "${initial.googleMapsUrl} (${initial.formattedCoordinates()})"
                initial.googleMapsUrl.isNotBlank() -> initial.googleMapsUrl
                initial.hasCoordinates -> initial.formattedCoordinates()
                else -> ""
            }
        )
    }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var isPinned by remember { mutableStateOf(initial?.isPinned ?: false) }

    val parsedPreview = remember(locationInput) { SiteLocationParser.parseUserInput(locationInput) }
    val hasLocationData = parsedPreview.googleMapsUrl.isNotBlank() ||
        (parsedPreview.latitude != null && parsedPreview.longitude != null)

    val siteTypes = listOf(
        SiteLocation.TYPE_STORE to "Material Store",
        SiteLocation.TYPE_SOLAR_SITE to "Solar Site",
        SiteLocation.TYPE_DEPOT to "Kalpataru Depot",
        SiteLocation.TYPE_OTHER to "Other"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Site / Store Location" else "Edit Site Location") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    label = { Text("Site / Store Name *") },
                    placeholder = { Text("e.g. Unchagawn Khadar Plant") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "LOCATION TYPE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    siteTypes.forEach { (key, label) ->
                        FilterChip(
                            selected = siteType == key,
                            onClick = { siteType = key },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = locationInput,
                    onValueChange = { locationInput = it.take(500) },
                    label = { Text("Google Maps Link or GPS Coordinates *") },
                    placeholder = { Text("https://maps.app.goo.gl/... OR 28.2644, 78.0489") },
                    minLines = 2,
                    maxLines = 3,
                    supportingText = {
                        Text(
                            when {
                                parsedPreview.latitude != null && parsedPreview.longitude != null ->
                                    String.format(Locale.US, "GPS detected: %.6f, %.6f", parsedPreview.latitude, parsedPreview.longitude)
                                parsedPreview.googleMapsUrl.isNotBlank() ->
                                    "Google Maps link ready — app will auto-extract GPS on Save"
                                else ->
                                    "Paste a Google Maps share link or lat, lng coordinates"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.take(240) },
                    label = { Text("Village / Landmark / Address") },
                    placeholder = { Text("e.g. Near Pump House, Syana") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(400) },
                    label = { Text("Notes (Materials / Supervisor)") },
                    placeholder = { Text("e.g. Key with Aziz / All cement & bolts here") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pin at top of Sites list",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    FilterChip(
                        selected = isPinned,
                        onClick = { isPinned = !isPinned },
                        label = { Text(if (isPinned) "Pinned" else "Pin") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && hasLocationData) {
                        onSave(name.trim(), siteType, address.trim(), locationInput.trim(), notes.trim(), isPinned)
                    }
                },
                enabled = name.isNotBlank() && hasLocationData
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
