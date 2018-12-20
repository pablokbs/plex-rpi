// filebot -script "fn:amc" --output "X:/media" --action copy --conflict override --def subtitles=en music=y artwork=y "ut_dir=%D" "ut_file=%F" "ut_kind=%K" "ut_title=%N" "ut_label=%L" "ut_state=%S"


// log input parameters
log.fine("Run script [${_args.script}] at [${now}]")
_def.each{ n, v -> log.finer('Parameter: ' + [n, n =~ /pushover|pushbullet|mail|myepisodes/ ? '*****' : v].join(' = ')) }
args.each{ log.finer("Argument: $it") }

// initialize variables
def input = []
def failOnError = _args.conflict.equalsIgnoreCase('fail')
def isTest = _args.action.equalsIgnoreCase('test')

// --output folder must be a valid folder
def outputFolder = new File(_args.output ?: '.').getCanonicalFile()

// enable/disable features as specified via --def parameters
def unsorted  = tryQuietly{ unsorted.toBoolean() }
def music     = tryQuietly{ music.toBoolean() }
def subtitles = tryQuietly{ subtitles.split(/\W+/) as List }
def artwork   = tryQuietly{ artwork.toBoolean() && !isTest }
def extras    = tryQuietly{ extras.toBoolean() }
def clean     = tryQuietly{ clean.toBoolean() }
def exec      = tryQuietly{ exec.toString() }

// array of kodi/plex/emby hosts
def kodi = tryQuietly{ any{kodi}{xbmc}.split(/[ ,|]+/) }
def plex = tryQuietly{ plex.split(/[ ,|]+/)*.split(/:/).collect{ it.length >= 2 ? [host:it[0], token:it[1]] : [host:it[0]] } }
def emby = tryQuietly{ emby.split(/[ ,|]+/)*.split(/:/).collect{ it.length >= 2 ? [host:it[0], token:it[1]] : [host:it[0]] } }

// extra options, myepisodes updates and email notifications
def storeReport = tryQuietly{ storeReport.toBoolean() }
def skipExtract = tryQuietly{ skipExtract.toBoolean() }
def deleteAfterExtract = tryQuietly{ deleteAfterExtract.toBoolean() }
def excludeList = tryQuietly{ (excludeList as File).isAbsolute() ? (excludeList as File) : new File(outputFolder, excludeList as String).getCanonicalFile() }
def myepisodes = tryQuietly{ myepisodes.split(':', 2) }
def gmail = tryQuietly{ gmail.split(':', 2) }
def mail = tryQuietly{ mail.split(':', 3) }
def pushover = tryQuietly{ pushover.split(':', 2) }
def pushbullet = tryQuietly{ pushbullet.toString() }
def reportError = tryQuietly{ reportError.toBoolean() }

// user-defined filters
def label = tryQuietly{ ut_label } ?: null
def ignore = tryQuietly{ ignore } ?: null
def minFileSize = tryQuietly{ minFileSize.toLong() }; if (minFileSize == null) { minFileSize = 50 * 1000L * 1000L }
def minLengthMS = tryQuietly{ minLengthMS.toLong() }; if (minLengthMS == null) { minLengthMS = 10 * 60 * 1000L }



// series/anime/movie format expressions
def format = [
	tvs:   any{ seriesFormat }{ '{plex}' },
	anime: any{ animeFormat  }{ '{plex}' },
	mov:   any{ movieFormat  }{ '{plex}' },
	music: any{ musicFormat  }{ '{plex}' },
	unsorted: any{ unsortedFormat }{ 'Unsorted/{file.structurePathTail}' }
]



// force Movie / TV Series / Anime behaviour
def forceMovie = { f ->
	label =~ /^(?i:Movie|Film|Concert)/ || f.dir.listPath().any{ it.name ==~ /(?i:Movies)/ } || f.isMovie()
}

def forceSeries = { f ->
	label =~ /^(?i:TV|Show|Series|Documentary)/ || f.dir.listPath().any{ it.name ==~ /(?i:TV.Shows)/ } || f.path =~ /(?i:tvs-|tvp-|E[P]?\d{1,3}|Season[\D]?\d{1,2}|\d{4}.S\d{2})/ || parseEpisodeNumber(f.path, true) || parseDate(f.path)
}

def forceAnime = { f ->
	label =~ /^(?i:Anime)/ || f.dir.listPath().any{ it.name ==~ /(?i:Anime)/ } || (f.isVideo() && (f.name =~ /(?i:HorribleSubs)/ || f.name =~ /[\(\[]\p{XDigit}{8}[\]\)]/ || (getMediaInfo(f, '''{media.AudioLanguageList} {media.TextCodecList}''').tokenize().containsAll(['Japanese', 'ASS']) && (parseEpisodeNumber(f.name, false) != null || getMediaInfo(f, '{minutes}').toInteger() < 60))))
}

def forceAudio = { f ->
	label =~ /^(?i:audio|music|music.video)/ || (f.isAudio() && !f.isVideo())
}

def forceIgnore = { f ->
	label =~ /^(?i:games|ebook|other|ignore|seeding)/ || f.path.findMatch(ignore) != null
}



// include artwork/nfo, pushover/pushbullet and ant utilities as required
if (artwork || kodi || plex || emby) { include('lib/htpc') }
if (pushover || pushbullet ) { include('lib/web') }
if (gmail || mail) { include('lib/ant') }



// error reporting functions
def sendEmailReport = { title, message, messagetype ->
	if (gmail) {
		sendGmail(
			subject: title, message: message, messagemimetype: messagetype,
			to: any{ mailto } { gmail[0].contains('@') ? gmail[0] : gmail[0] + '@gmail.com' },		// mail to self by default
			user: gmail[0].contains('@') ? gmail[0] : gmail[0] + '@gmail.com', password: gmail[1]
		)
	}
	if (mail) {
		sendmail(
			subject: title, message: message, messagemimetype: messagetype,
			mailhost: mail[0], mailport: mail[1], from: mail[2], to: mailto
		)
	}
}

def fail = { message ->
	if (reportError) {
		sendEmailReport('[FileBot] Failure', message, 'text/plain')
	}
	die(message)
}



// sanity checks
args.findAll{ !it.exists() }.each{ fail("File not found: $it") }

// check user-defined pre-condition
if (tryQuietly{ !(ut_state ==~ ut_state_allow) }) {
	fail("Illegal state: ut_state = $ut_state (expected $ut_state_allow)")
}

// check ut mode vs standalone mode
if ((tryQuietly{ ut_dir } == '/') || (tryQuietly{ ut_dir } == '%D') || (args.size() > 0 && (tryQuietly{ ut_dir }?.size() > 0 || tryQuietly{ ut_file }?.size() > 0)) || (args.size() == 0 && (tryQuietly{ ut_dir } == null && tryQuietly{ ut_file } == null))) {
	fail("Illegal usage: bad file arguments or ut_dir/ut_file parameters")
}

// make sure input and output folders do not overlap
if (args.any{ outputFolder.path.startsWith(it.canonicalPath) } || tryQuietly{ outputFolder.path.startsWith(ut_dir) } ) {
	fail("Illegal usage: input folder must not contain output folder")
}



// define and load exclude list (e.g. to make sure files are only processed once)
def excludePathSet = new FileSet()
if (excludeList) {
	if (excludeList.exists()) {
		try {
			excludePathSet.load(excludeList)
		} catch(Exception e) {
			fail("Failed to load excludeList: ${e}")
		}
		log.finest "Using excludes: ${excludeList} (${excludePathSet.size()})"
	} else {
		log.finest "Creating excludes: ${excludeList}"
		if ((!excludeList.parentFile.isDirectory() && !excludeList.parentFile.mkdirs()) || (!excludeList.isFile() && !excludeList.createNewFile())) {
			fail("Failed to create excludeList: ${excludeList}")
		}
	}
}


// specify how to resolve input folders, e.g. grab files from all folders except disk folders and already processed folders (i.e. folders with movie/tvshow nfo files)
def resolveInput(f) {
	if (f.isDirectory()) {
		// ignore system and hidden folders
		if (f.isHidden() || f.name ==~ /[.@].+|bin|initrd|opt|sbin|var|dev|lib|proc|sys|var.defaults|etc|lost.found|root|tmp|etc.defaults|mnt|run|usr|System.Volume.Information/) {
			log.finest "Ignore hidden folder: $f"
			return []
		}

		// resolve folder recursively, except disk folders
		if (f.isDisk()) {
			return f
		}

		// resolve folder recursively
		def files = f.listFiles() as List ?: []
		return files.findResults{ resolveInput(it) }
	}

	// ignore non-standard files
	if (f.isFile() && !f.isHidden()) {
		return f
	}
	return []
}

// collect input fileset as specified by the given --def parameters
def roots = []
if (args.empty) {
	// assume we're called with utorrent parameters (account for older and newer versions of uTorrents)
	if (ut_kind == 'single' || (ut_kind != 'multi' && ut_dir && ut_file)) {
		roots += new File(ut_dir, ut_file) // single-file torrent
	} else {
		roots += new File(ut_dir) // multi-file torrent
	}
} else {
	// assume we're called normally with arguments
	roots += args
}

// sanitize input
roots = roots.findAll{ it?.exists() }.collect{ it.canonicalFile }.unique() // roots could be folders as well as files

// flatten nested file structure
input = roots.flatten{ f -> resolveInput(f) }

// ignore archives that are on the exclude path list
input = input.findAll{ f -> !excludePathSet.contains(f.path) }

// extract archives (zip, rar, etc) that contain at least one video file
def extractedArchives = []
def tempFiles = []
input = input.flatten{ f ->
	if (!skipExtract && (f.isArchive() || f.hasExtension('001'))) {
		def extractDir = new File(f.dir, f.nameWithoutExtension)
		def extractFiles = extract(file: f, output: new File(extractDir, f.dir.name), conflict: 'auto', filter: { it.isArchive() || it.isVideo() || it.isSubtitle() || (music && it.isAudio()) }, forceExtractAll: true) ?: []

		extractedArchives += f
		tempFiles += extractDir
		tempFiles += extractFiles

		return extractFiles
	}
	return f
}


// ignore files that are on the exclude path list
input = input.findAll{ f -> !excludePathSet.contains(f.path) }

// update exclude list with all input that will be processed during this run
if (excludeList && !isTest) {
	excludePathSet.append(excludeList, extractedArchives, input)
}


// helper function to work with the structure relative path rather than the whole absolute path
def relativeInputPath = { f ->
	def r = roots.find{ r -> f.path.startsWith(r.path) && r.isDirectory() && f.isFile() }
	if (r != null) {
		return f.path.substring(r.path.length() + 1)
	}
	return f.name
}


// keep original input around so we can print excluded files later
def originalInputSet = new LinkedHashSet(input)

// process only media files
input = input.findAll{ f -> (f.isVideo() && !tryQuietly{ f.hasExtension('iso') && !f.isDisk() }) || f.isSubtitle() || (f.isDirectory() && f.isDisk()) || (music && f.isAudio()) }

// ignore clutter files
input = input.findAll{ f -> !(relativeInputPath(f) =~ /(?<=\b|_)(?i:sample|trailer|extras|music.video|scrapbook|behind.the.scenes|extended.scenes|deleted.scenes|mini.series|s\d{2}c\d{2}|S\d+EXTRA|\d+xEXTRA|NCED|NCOP|(OP|ED)\d+|Formula.1.\d{4})(?=\b|_)/) }

// ignore video files that don't conform with the file-size and video-length limits
input = input.findAll{ f -> !(f.isVideo() && ((minFileSize > 0 && f.length() < minFileSize) || (minLengthMS > 0 && tryQuietly{ getMediaInfo(f, '{duration}').toLong() < minLengthMS }))) }

// ignore subtitles files that are not stored in the same folder as the movie
input = input.findAll{ f -> !(f.isSubtitle() && !input.findAll{ it.isVideo() }.any{ v -> f.isDerived(v) || f.path.startsWith(v.parentFile.path) }) }

// ensure that the final input set is sorted
input = input.sort()

// print exclude and input sets for logging
input.each{ f -> log.finer("Input: $f") }
(originalInputSet - input).each{ f -> log.finest("Exclude: $f") }

// early abort if there is nothing to do
if (input.size() == 0) die("No files selected for processing")



// group episodes/movies and rename according to Plex standards
def groups = input.groupBy{ f ->
	// print xattr metadata
	if (f.metadata) {
		log.finest "xattr: [$f.name] => [$f.metadata]"
	}

	// skip auto-detection if possible
	if (forceIgnore(f))
		return []
	if (music && forceAudio(f)) // process audio only if music mode is enabled
		return [music: f.dir.name]
	if (forceMovie(f))
		return [mov:   detectMovie(f, false)]
	if (forceSeries(f))
		return [tvs:   detectSeriesName(f) ?: detectSeriesName(input.findAll{ s -> f.dir == s.dir && s.isVideo() })]
	if (forceAnime(f))
		return [anime: detectAnimeName(f) ?: detectAnimeName(input.findAll{ s -> f.dir == s.dir && s.isVideo() })]


	def tvs = detectSeriesName(f)
	def mov = detectMovie(f, false)
	log.fine("$f.name [series: $tvs, movie: $mov]")

	// DECIDE EPISODE VS MOVIE (IF NOT CLEAR)
	if (tvs && mov) {
		def norm = { s -> s.ascii().normalizePunctuation().lower().space(' ') }
		def dn = norm(guessMovieFolder(f)?.name ?: '')
		def fn = norm(f.nameWithoutExtension)
		def sn = norm(tvs)
		def mn = norm(mov.name)
		def my = mov.year as String

		// S00E00 | 2012.07.21 | One Piece 217 | Firefly - Serenity | [Taken 1, Taken 2, Taken 3, Taken 4, ..., Taken 10]
		def metrics = [
			[tvs: -1, mov:  0, fun: { mn == fn } ],
			[tvs: -1, mov:  0, fun: { mov.year >= 1950 && f.listPath().reverse().take(3).find{ it.name.contains(my) && parseEpisodeNumber(it.name.after(my), false) == null } } ],
			[tvs: -1, mov:  0, fun: { mn =~ sn && [dn, fn].find{ it =~ /\b(19|20)\d{2}\b/ && parseEpisodeNumber(it.after(/\b(19|20)\d{2}\b/), false) == null } } ],
			[tvs:  5, mov: -1, fun: { parseEpisodeNumber(fn, true) || parseDate(fn) } ],
			[tvs:  5, mov: -1, fun: { f.dir.listFiles{ it.isVideo() && (dn =~ sn || norm(it.name) =~ sn) && it.name =~ /\d{1,3}/}.findResults{ it.name.matchAll(/\d{1,3}/) as Set }.unique().size() >= 10 } ],
			[tvs:  1, mov: -1, fun: { fn.after(sn) ==~ /.{0,3}\s-\s.+/ && matchMovie(fn) == null } ],
			[tvs:  1, mov: -1, fun: { [dn, fn].find{ it =~ sn && matchMovie(it) == null } && (parseEpisodeNumber(stripReleaseInfo(fn.after(sn), false), false) || stripReleaseInfo(fn.after(sn), false) =~ /\D\d{1,2}\D{1,3}\d{1,2}\D/) && matchMovie(fn) == null } ],
			[tvs: -1, mov:  1, fun: { fn =~ /tt\d{7}/ } ],
			[tvs: -1, mov:  1, fun: { f.nameWithoutExtension ==~ /[\D\s_.]+/ } ],
			[tvs: -1, mov:  5, fun: { detectMovie(f, true) && [dn, fn].find{ it =~ /(19|20)\d{2}/ } != null } ],
			[tvs: -1, mov:  1, fun: { fn.contains(mn) && parseEpisodeNumber(fn.after(mn), false) == null } ],
			[tvs: -1, mov:  1, fun: { mn.getSimilarity(fn) >= 0.8 || [dn, fn].find{ it.findAll( ~/\d{4}/ ).findAll{ y -> [mov.year-1, mov.year, mov.year+1].contains(y.toInteger()) }.size() > 0 } != null } ],
			[tvs: -1, mov:  1, fun: { [dn, fn].find{ it =~ mn && !(it.after(mn) =~ /\b\d{1,3}\b/) && (it.getSimilarity(mn) > 0.2 + it.getSimilarity(sn)) } != null } ],
			[tvs: -1, mov:  1, fun: { detectMovie(f, false).aliasNames.find{ fn.contains(norm(it)) } } ]
		]

		def score = [tvs: 0, mov: 0]		
		metrics.each{
			if (tvs && mov && it.fun()) {
				score.tvs += it.tvs
				score.mov += it.mov

				if (score.tvs >= 1 && score.mov <= -1) {
					log.fine("Exclude Movie: $mov")
					mov = null
				} else if (score.mov >= 1 && score.tvs <= -1) {
					log.fine("Exclude Series: $tvs")
					tvs = null
				}
			}
		}

	}

	// CHECK CONFLICT
	if (((mov && tvs) || (!mov && !tvs))) {
		if (failOnError) {
			fail("Media detection failed")
		} else {
			log.fine("Unable to differentiate: [$f.name] => [$tvs] VS [$mov]")
			return [:]
		}
	}

	return [tvs: tvs, mov: mov]
}

// group entries by unique tvs/mov descriptor
groups = groups.groupBy{ group, files -> group.collectEntries{ type, query -> [type, query ? query.toString().ascii().normalizePunctuation().lower() : null] } }.collectEntries{ group, maps -> [group, maps.values().flatten()] }

// log movie/series/anime detection results
groups.each{ group, files -> log.finest("Group: $group => ${files*.name}") }

// keep track of unsorted files or files that could not be processed for some reason
def unsortedFiles = []

// process each batch
groups.each{ group, files ->
	// fetch subtitles (but not for anime)
	if (group.anime == null && subtitles != null && files.findAll{ it.isVideo() }.size() > 0) {
		subtitles.each{ languageCode ->
			def subtitleFiles = getMissingSubtitles(file:files, lang:languageCode, strict:true, output:'srt', encoding:'UTF-8', format:'MATCH_VIDEO_ADD_LANGUAGE_TAG') ?: []
			files += subtitleFiles
			input += subtitleFiles // make sure subtitles are added to the exclude list and other post processing operations
			tempFiles += subtitleFiles // if downloaded for temporarily extraced files delete later
		}
	}

	// EPISODE MODE
	if ((group.tvs || group.anime) && !group.mov) {
		// choose series / anime config
		def config = group.tvs ? [name:group.tvs, format:format.tvs, db:'TheTVDB'] : [name:group.anime, format:format.anime, db:'AniDB']
		def dest = rename(file: files, format: config.format, db: config.db)

		if (dest != null) {
			if (artwork) {
				dest.mapByFolder().each{ dir, fs ->
					def hasSeasonFolder = any{ dir =~ /Specials|Season.\d+/ || dir.parentFile.structurePathTail.listPath().size() > 0 }{ false }	// MAY NOT WORK FOR CERTAIN FORMATS

					fs.findResults{ it.metadata }.findAll{ it.seriesInfo.database == 'TheTVDB' }.collect{ [name: it.seriesName, season: it.special ? 0 : it.season, id: it.seriesInfo.id] }.unique().each{
						log.fine "Fetching series artwork for [$it.name / Season $it.season] to [$dir]"
						fetchSeriesArtworkAndNfo(hasSeasonFolder ? dir.parentFile : dir, dir, it.id, it.season, false, _args.locale)
					}
				}
			}
		} else if (failOnError) {
			fail("Failed to rename series: $config.name")
		} else {
			unsortedFiles += files
		}
	}

	// MOVIE MODE
	else if (group.mov && !group.tvs && !group.anime) {
		def dest = rename(file:files, format:format.mov, db:'TheMovieDB')

		if (dest != null) {
			if (artwork) {
				dest.mapByFolder().each{ dir, fs ->
					def movieFile = fs.findAll{ it.isVideo() || it.isDisk() }.sort{ it.length() }.reverse().findResult{ it }
					if (movieFile) {
						def movieInfo = movieFile.metadata
						log.fine "Fetching movie artwork for [$movieInfo] to [$dir]"
						fetchMovieArtworkAndNfo(dir, movieInfo, movieFile, extras, false, _args.locale)
					}
				}
			}
		} else if (failOnError) {
			fail("Failed to rename movie: $group.mov")
		} else {
			unsortedFiles += files
		}
	}

	// MUSIC MODE
	else if (group.music) {
		def dest = rename(file:files, format:format.music, db:'ID3')

		if (dest != null) {
			// music artwork not supported
		} else if (failOnError) {
			fail("Failed to rename music: $group.music")
		} else {
			unsortedFiles += files
		}
	}

	// UNSORTED
	else {
		unsortedFiles += files
	}
}


// ---------- POST PROCESSING ---------- //

// deal with remaining files that cannot be sorted automatically
if (unsorted) {
	if (unsortedFiles.size() > 0) {
		log.info "Processing ${unsortedFiles.size()} unsorted files"
		rename(map: unsortedFiles.collectEntries{ original ->
			def destination = getMediaInfo(original, format.unsorted) as File
			return [original, destination.isAbsolute() ? destination : new File(outputFolder, destination as String)]
		})
	}
}

// run program on newly processed files
if (exec) {
	getRenameLog().collect{ from, to -> getMediaInfo(to, exec) }.unique().each{
		log.finest("Execute: $it")
		execute(it)
	}
}


// ---------- REPORTING ---------- //


if (getRenameLog().size() > 0) {
	// messages used for kodi / plex / emby pushover notifications
	def getNotificationTitle = { "FileBot finished processing ${getRenameLog().values().findAll{ !it.isSubtitle() }.size()} files" }.memoize()
	def getNotificationMessage = { prefix = '• ', postfix = '\n' -> tryQuietly{ ut_title } ?: (input.any{ !it.isSubtitle() } ? input.findAll{ !it.isSubtitle() } : input).collect{ relativeInputPath(it) as File }*.getRoot()*.getNameWithoutExtension().unique().sort{ it.toLowerCase() }.collect{ prefix + it }.join(postfix).trim() }.memoize()

	// make Kodi scan for new content and display notification message
	if (kodi) {
		kodi.each{ host ->
			log.info "Notify Kodi: $host"
			tryLogCatch{
				showNotification(host, 9090, getNotificationTitle(), getNotificationMessage(), 'http://app.filebot.net/icon.png')
				scanVideoLibrary(host, 9090)
			}
		}
	}

	// make Plex scan for new content
	if (plex) {
		plex.each{ instance ->
			log.info "Notify Plex: ${instance.host}"
			tryLogCatch {
				refreshPlexLibrary(instance.host, 32400, instance.token)
			}
		}
	}

	// make Emby scan for new content
	if (emby) {
		emby.each{ instance ->
			log.info "Notify Emby: ${instance.host}"
			tryLogCatch {
				refreshEmbyLibrary(instance.host, 8096, instance.token)
			}
		}
	}

	// mark episodes as 'acquired'
	if (myepisodes) {
		log.info 'Update MyEpisodes'
		tryLogCatch {
			executeScript('update-mes', [login:myepisodes.join(':'), addshows:true], getRenameLog().values())
		}
	}

	if (pushover) {
		log.info 'Sending Pushover notification'
		tryLogCatch {
			Pushover(pushover[0], pushover.length == 1 ? 'wcckDz3oygHSU2SdIptvnHxJ92SQKK' : pushover[1]).send(getNotificationTitle(), getNotificationMessage())
		}
	}

	// messages used for email / pushbullet reports
	def getReportSubject = { getNotificationMessage('', ', ') }
	def getReportTitle = { '[FileBot] ' + getReportSubject() }
	def getReportMessage = { 
		def renameLog = getRenameLog()
		'''<!DOCTYPE html>\n''' + XML {
			html {
				head {
					meta(charset:'UTF-8')
					style('''
						p{font-family:Arial,Helvetica,sans-serif}
						p b{color:#07a}
						hr{border-style:dashed;border-width:1px 0 0 0;border-color:lightgray}
						small{color:#d3d3d3;font-size:xx-small;font-weight:normal;font-family:Arial,Helvetica,sans-serif}
						table a:link{color:#666;font-weight:bold;text-decoration:none}
						table a:visited{color:#999;font-weight:bold;text-decoration:none}
						table a:active,table a:hover{color:#bd5a35;text-decoration:underline}
						table{font-family:Arial,Helvetica,sans-serif;color:#666;background:#eaebec;margin:15px;border:#ccc 1px solid;border-radius:3px;box-shadow:0 1px 2px #d1d1d1}
						table th{padding:15px;border-top:1px solid #fafafa;border-bottom:1px solid #e0e0e0;background:#ededed}
						table th{text-align:center;padding-left:20px}
						table tr:first-child th:first-child{border-top-left-radius:3px}
						table tr:first-child th:last-child{border-top-right-radius:3px}
						table tr{text-align:left;padding-left:20px}
						table td:first-child{text-align:left;padding-left:20px;border-left:0}
						table td{padding:15px;border-top:1px solid #fff;border-bottom:1px solid #e0e0e0;border-left:1px solid #e0e0e0;background:#fafafa;white-space:nowrap}
						table tr.even td{background:#f6f6f6}
						table tr:last-child td{border-bottom:0}
						table tr:last-child td:first-child{border-bottom-left-radius:3px}
						table tr:last-child td:last-child{border-bottom-right-radius:3px}
						table tr:hover td{background:#f2f2f2}
					''')
					title(getReportTitle())
				}
				body {
					p {
						mkp.yield("FileBot finished processing ")
						b(getReportSubject())
						mkp.yield(" (${renameLog.size()} files).")
					}
					hr(); table {
						tr { th('Original Name'); th('New Name'); th('New Location') }
						renameLog.each{ from, to ->
							tr { [from.name, to.name, to.parent].each{ cell -> td(cell) } }
						}
					}
					hr(); small("// Generated by ${Settings.getApplicationIdentifier()} on ${InetAddress.localHost.hostName} at ${now}")
				}
			}
		}
	}

	// store processing report
	if (storeReport) {
		def reportFolder = new File(Settings.getApplicationFolder(), 'reports').getCanonicalFile()
		def reportFile = getReportMessage().saveAs(new File(reportFolder, "AMC ${now.format('''[yyyy-MM-dd HH'h'mm'm']''')} ${getReportSubject().take(50).trim()}.html".validateFileName()))
		log.finest("Saving report as ${reportFile}")
	}

	// send pushbullet report
	if (pushbullet) {
		log.info 'Sending PushBullet report'
		tryLogCatch {
			PushBullet(pushbullet).sendFile(getNotificationTitle(), getReportMessage(), 'text/html', getNotificationMessage(), tryQuietly{ mailto })
		}
	}

	// send email report
	if (gmail || mail) {
		tryLogCatch {
			sendEmailReport(getReportTitle(), getReportMessage(), 'text/html')
		}
	}
}


// ---------- CLEAN UP ---------- //


// clean up temporary files that may be left behind after extraction
if (deleteAfterExtract) {
	extractedArchives.each{ a ->
		log.finest("Delete archive $a")
		a.delete()
		a.dir.listFiles().toList().findAll{ v -> v.name.startsWith(a.nameWithoutExtension) && v.extension ==~ /r\d+/ }.each{ v ->
			log.finest("Delete archive volume $v")
			v.delete()
		}
	}
}

// clean empty folders, clutter files, etc after move
if (clean) {
	if (['DUPLICATE', 'COPY', 'HARDLINK'].any{ it.equalsIgnoreCase(_args.action) } && tempFiles.size() > 0) {
		log.info 'Clean temporary extracted files'
		// delete extracted files
		tempFiles.findAll{ it.isFile() }.sort().each{
			log.finest "Delete $it"
			it.delete()
		}
		// delete remaining empty folders
		tempFiles.findAll{ it.isDirectory() }.sort().reverse().each{
			log.finest "Delete $it"
			if (it.getFiles().isEmpty()) it.deleteDir()
		}
	}

	// deleting remaining files only makes sense after moving files
	if ('MOVE'.equalsIgnoreCase(_args.action)) {
		def cleanerInput = !args.empty ? args : ut_kind == 'multi' && ut_dir ? [ut_dir as File] : []
		cleanerInput = cleanerInput.findAll{ f -> f.exists() }
		if (cleanerInput.size() > 0) {
			log.info 'Clean clutter files and empty folders'
			executeScript('cleaner', args.empty ? [root:true, ignore: ignore] : [root:false, ignore: ignore], cleanerInput)
		}
	}
}


if (getRenameLog().size() == 0) fail("Finished without processing any files")
