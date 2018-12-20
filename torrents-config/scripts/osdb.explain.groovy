// filebot -script fn:osdb.explain /path/to/video --def fetch=y

def lang = _args.locale.ISO3Language
def strict = !_args.nonStrict

def fetch = any{ fetch as boolean }{ false }


args.getFiles{ it.isVideo() }.each{ f ->
	println "File: $f"

	// hash search
	def hash = net.filebot.web.OpenSubtitlesHasher.computeHash(f)
	def size = f.length()
	println "Hash/Tag Lookup (hash: $hash, size: $size, lang: $lang, tag: $f.nameWithoutExtension)"

	def hashMatches = net.filebot.subtitle.SubtitleUtilities.lookupSubtitlesByHash(WebServices.OpenSubtitles, [f], lang, true, strict).get(f)
	hashMatches.eachWithIndex{ d, i ->
		println "Result ${i+1}: ${d.properties}"
	}

	def bestHashMatch = net.filebot.subtitle.SubtitleUtilities.getBestMatch(f, hashMatches, strict)
	println "Best Hash Match: ${bestHashMatch?.properties}"

	// name search
	def nameMatches = []
	if (!strict) {
		println "Name Lookup (file: $f.nameWithoutExtension, strict: $strict, lang: $lang)"
		nameMatches = net.filebot.subtitle.SubtitleUtilities.findSubtitlesByName(WebServices.OpenSubtitles, [f], lang, null, true, strict).get(f)
		nameMatches.eachWithIndex{ d, i ->
			println "Result ${i+1}: ${d.properties}"
		}
		println "Best Name Match: ${nameMatches[0]?.properties}"
	}

	// fetch subtitles
	if (fetch) {
		[hashMatches, nameMatches].collectMany{ it ?: [] }.unique{ d -> d.path }.each{ d ->
			def s = f.resolveSibling(d.path)
			if (!s.exists()) {
				println "Fetch $s"
				d.fetch().saveAs(s)
			}
		}
	}
}
