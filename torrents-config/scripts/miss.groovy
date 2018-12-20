// filebot -script fn:miss /path/to/media

def episodes = []
def shows = []

args.getFiles().each{ f ->
	if (f.isVideo()) {
		def episode = f.metadata
		def show = episode?.seriesInfo

		log.finest "$show | $episode | $f"

		if (episode != null && show != null) {
			episodes << episode
			shows << show
		}
	}
}


def episodeList = shows.collectMany{
	return WebServices.getEpisodeListProvider(it.database).getEpisodeList(it.id, it.order as SortOrder, new Locale(it.language))
} as LinkedHashSet

episodeList.removeAll(episodes)


// print missing episodes
episodeList.each{
	println it
}
