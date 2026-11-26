package com.gitgud.tunerack.data.youtube

import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import java.io.IOException

/** This particular song can't be played or downloaded; [reason] is shown to the user. */
class UnplayableException(val reason: String, cause: Throwable) : IOException(reason, cause)

/**
 * Turns a NewPipe failure into what the app should do with it: songs YouTube won't serve become
 * [UnplayableException]; YouTube blocking this device ("confirm you're not a bot") stays a plain
 * IOException, because skipping to the next song would fail the same way.
 */
fun ExtractionException.toPlaybackException(): IOException = when (this) {
    is SignInConfirmNotBotException -> IOException("YouTube is temporarily blocking this device. Try again later.", this)
    is AgeRestrictedContentException -> UnplayableException("Age-restricted on YouTube", this)
    is GeographicRestrictionException -> UnplayableException("Not available in your country", this)
    is PaidContentException, is YoutubeMusicPremiumContentException -> UnplayableException("Needs a paid YouTube subscription", this)
    is ContentNotAvailableException -> UnplayableException("Not available on YouTube", this)
    else -> UnplayableException("Couldn't load this song", this)
}

/** The first [UnplayableException] in this throwable's cause chain, if any. */
fun Throwable.findUnplayable(): UnplayableException? =
    generateSequence(this) { it.cause }.take(10).filterIsInstance<UnplayableException>().firstOrNull()
