// filebot -script fn:xattr --action clear /path/to/files

args.getFiles{ f -> f.xattr.size() > 0 }.each{ f ->
	println f

	f.xattr.each{ k, v ->
		println "\t$k: $v"

		// auto-update pre-v41 xattr keys
		if (k in ['metadata', 'filename']) {
			def newk = "net.filebot.$k"
			if (f.xattr[newk] == null) {
				// UPDATE 
				println "\t\tIMPORT xattr $k => $newk"
				f.xattr[newk] = v.replace("net.sourceforge.filebot", "net.filebot")
			}
		}
	}

	// clear xattr mode
	if (_args.action == 'clear') {
		f.xattr.clear()
		println '*** CLEARED ***'
	}

	// import xattr metadata into Mac OS X Finder tags (UAYOR)
	if (_args.action == 'import') {
		def xkey = 'com.apple.metadata:_kMDItemUserTags'
		def info = getMediaInfo(f, '''{if (movie) 'Movie'};{if (episode) 'Episode'};{source};{vf};{sdhd}''')
		def tags = info.split(';')*.trim().findAll{ it.length() > 0 }

		def plist = '''<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n''' + XML{
			plist(version:'1.0') {
				array {
					tags.each{
						string(it)
					}
				}
			}
		}

		println "*** Write tag plist to xattr [$xkey]: $tags ***"
		f.xattr[xkey] = plist
	}
}
