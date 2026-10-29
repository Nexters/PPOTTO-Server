package com.github.nexters.ppotto.global.jooq

import com.github.nexters.ppotto.global.identifier.BoardInvitationId
import org.jooq.Converter
import java.util.UUID

class BoardInvitationIdConverter :
    Converter<UUID, BoardInvitationId> by Converter.ofNullable(
        UUID::class.java,
        BoardInvitationId::class.java,
        ::BoardInvitationId,
        BoardInvitationId::value,
    )
