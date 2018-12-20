// filebot -script fn:duplicates --action delete /path/to/media

args.getFiles{ it.isVideo() }.groupBy{ it.metadata }.each{ m, fs ->
	if (m && fs.size() > 1) {
		log.info "[*] $m"

		fs.toSorted(new VideoQuality()).eachWithIndex{ f, i ->
			if (i == 0) {
				log.fine "[+] 1. $f"
			} else {
				log.fine "[-] ${i+1}. $f"

				if ('delete' == _args.action) {
					log.finest "[DELETE] $f"
					Files.delete(f.toPath())
				}
			}
		}
	}
}
