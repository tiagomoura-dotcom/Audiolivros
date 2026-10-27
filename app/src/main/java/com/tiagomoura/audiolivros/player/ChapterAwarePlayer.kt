package com.tiagomoura.audiolivros.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Player entregue à sessão: "anterior" e "próximo" (fone, Bluetooth, carro)
 * navegam pelos capítulos em vez de pelos itens da fila.
 */
@OptIn(UnstableApi::class)
class ChapterAwarePlayer(
    player: Player,
    private val timeline: () -> ChapterTimeline,
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .addAll(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_NEXT)
            .build()

    override fun isCommandAvailable(command: Int): Boolean =
        command == Player.COMMAND_SEEK_TO_PREVIOUS ||
            command == Player.COMMAND_SEEK_TO_NEXT ||
            super.isCommandAvailable(command)

    override fun seekToPrevious() {
        seekTo(timeline().previousTarget(currentPosition))
    }

    override fun seekToNext() {
        timeline().nextTarget(currentPosition)?.let { seekTo(it) }
    }
}
