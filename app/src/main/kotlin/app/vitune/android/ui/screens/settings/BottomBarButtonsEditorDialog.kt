package app.vitune.android.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.R
import app.vitune.android.preferences.PlayerPreferences
import app.vitune.android.ui.components.themed.DefaultDialog
import app.vitune.android.ui.components.themed.IconButton
import app.vitune.android.ui.components.themed.SecondaryTextButton
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance

@Composable
fun BottomBarButtonsEditorDialog(onDismiss: () -> Unit) {
    val (colorPalette, typography) = LocalAppearance.current

    var activeActions by rememberSaveable(
        stateSaver = listSaver(
            save = { list -> list.map { it.name } },
            restore = { names ->
                names.mapNotNull { name ->
                    PlayerPreferences.PlayerBottomAction.entries.find { it.name == name }
                }
            }
        )
    ) {
        mutableStateOf(PlayerPreferences.parsePlayerBottomActions())
    }

    val allActions = PlayerPreferences.PlayerBottomAction.entries
    val availableToAdd = allActions.filter { it !in activeActions }

    DefaultDialog(onDismiss = onDismiss) {
        BasicText(
            text = stringResource(R.string.player_bottom_bar_buttons),
            style = typography.s.semiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        ) {
            BasicText(
                text = stringResource(R.string.player_bottom_bar_active),
                style = typography.xs.semiBold.copy(color = colorPalette.textSecondary),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                activeActions.forEachIndexed { index, action ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorPalette.background2)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            text = action.displayName(),
                            style = typography.xs.semiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (index > 0) {
                            IconButton(
                                icon = R.drawable.chevron_up,
                                color = colorPalette.text,
                                onClick = {
                                    val newList = activeActions.toMutableList()
                                    val item = newList.removeAt(index)
                                    newList.add(index - 1, item)
                                    activeActions = newList
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Spacer(Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        if (index < activeActions.size - 1) {
                            IconButton(
                                icon = R.drawable.chevron_down,
                                color = colorPalette.text,
                                onClick = {
                                    val newList = activeActions.toMutableList()
                                    val item = newList.removeAt(index)
                                    newList.add(index + 1, item)
                                    activeActions = newList
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Spacer(Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            icon = R.drawable.close,
                            color = colorPalette.text,
                            onClick = {
                                activeActions = activeActions.toMutableList().also { it.removeAt(index) }
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (availableToAdd.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                BasicText(
                    text = stringResource(R.string.player_bottom_bar_available),
                    style = typography.xs.semiBold.copy(color = colorPalette.textSecondary),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    availableToAdd.forEach { action ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colorPalette.background2)
                                .clickable(
                                    onClick = {
                                        activeActions = activeActions + action
                                    },
                                    indication = ripple(),
                                    interactionSource = remember { MutableInteractionSource() }
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            BasicText(
                                text = action.displayName(),
                                style = typography.xs.semiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Image(
                                painter = painterResource(R.drawable.add),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(colorPalette.accent),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            SecondaryTextButton(
                text = stringResource(R.string.reset),
                onClick = {
                    activeActions = PlayerPreferences.parsePlayerBottomActions(
                        PlayerPreferences.DEFAULT_PLAYER_BOTTOM_BAR_ACTIONS
                    )
                }
            )
            SecondaryTextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
            SecondaryTextButton(
                text = stringResource(R.string.save),
                onClick = {
                    PlayerPreferences.playerBottomBarActions = activeActions.joinToString(",") { it.name }
                    onDismiss()
                }
            )
        }
    }
}
