// filebot -script dev:suball /path/to/media -non-strict --def maxAgeDays=7

setDefaultValues(
	minAgeDays: null,
	maxAgeDays: 30,
	minFileSize: 50 * 1000 * 1000L,
	minLengthMS: 10 * 60 * 1000L,
	ignore: null,
	ignoreTextLanguage: '.+'
)


def minFileSize = minFileSize as long
def minLengthMS = minLengthMS as long

def minAgeTimeStamp = any{ now.time - ((minAgeDays as double) * 24 * 60 * 60 * 1000L) as long }{ null }
def maxAgeTimeStamp = any{ now.time - ((maxAgeDays as double) * 24 * 60 * 60 * 1000L) as long }{ null }
def maxAgeDaysLimit = any{ maxAgeDaysLimit.toBoolean() }{ true }


def ignore = { f, m ->
	log.finest "Ignore [$f.name]: $m"
	return false
}


def accept = { f ->
	def creationDate = f.creationDate

	// ignore files that are too old
	if (maxAgeTimeStamp != null && creationDate < maxAgeTimeStamp) {
		return ignore(f, 'File creation date is too far in the past')
	}

	// ignore files that are too young
	if (minAgeTimeStamp != null && creationDate > minAgeTimeStamp) {
		return ignore(f, 'File creation date is too recent')
	}

	// ignore files that match the give ignore pattern
	if (ignore != null && f.path =~ ignore) {
		return ignore(f, 'File path matches the ignore pattern')
	}

	// ignore files that are too small	
	if (minFileSize > 0 && f.length() < minFileSize) {
		return ignore(f, 'File size is too small')
	}

	// ignore files that are too short
	if (minLengthMS > 0 && ((getMediaInfo(f, '{duration}') ?: minLengthMS) as double) < minLengthMS) {
		return ignore(f, 'Video duration is too short')
	}

	// ignore files that already have subtitles
	if (ignoreTextLanguage != null && getMediaInfo(f, '{media.TextLanguageList}').findMatch(ignoreTextLanguage) != null) {
		return ignore(f, 'Video file already contains embedded subtitles')
	}

	// sanity check
	if (maxAgeDaysLimit && (maxAgeDays == null || maxAgeDays.toDouble() > 30)) {
		return ignore(f, "Your maxAgeDays limit ($maxAgeDays) is unreasonable")
	}

	return true
}


/*
 * Get subtitles for all your media files  
 */
args.getFiles{ it.isVideo() && accept(it) }.groupBy{ it.dir }.each{ dir, files ->
	log.info "Fetch subtitles for [$dir]"
	getMissingSubtitles(file: files, output:'srt', encoding:'UTF-8')
}
