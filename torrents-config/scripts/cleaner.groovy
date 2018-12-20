// filebot -script fn:cleaner [--action test] /path/to/media/

def deleteRootFolder = any{ root.toBoolean() }{ false }

def ignore  = any{ ignore }{ /extrathumbs/ }
def exts    = any{ exts }{ /jpg|jpeg|png|gif|ico|nfo|info|xml|htm|html|log|srt|sub|idx|smi|sup|md5|sfv|txt|rtf|url|db|dna|log|tgmd|json|data|ignore|srv|srr|nzb|rar|par\d+|part\d+/ }
def terms   = any{ terms }{ /sample|trailer|extras|deleted.scenes|music.video|scrapbook|DS_Store/ }
def minsize = any{ minsize.toLong() }{ 20 * 1024 * 1024 }
def maxsize = any{ maxsize.toLong() }{ 100 * 1024 * 1024 }

def extensionExcludePattern = "(?i)($exts)"
def pathExcludePattern      = "(?i)\\b($terms)\\b"


/*
 * Delete orphaned "clutter" files like nfo, jpg, etc and sample files
 */
def isClutter = { f ->
	// whitelist
	if (f.path.findMatch(ignore))
		return false

	// file is either too small to have meaning, or to large to be considered clutter
	def fsize = f.length()

	// path contains blacklisted terms or extension is blacklisted
	if (f.extension ==~ extensionExcludePattern && fsize < maxsize)
		return true

	if (f.path =~ pathExcludePattern && fsize < maxsize)
		return true

	// NOTE: some smb filesystem implementations are buggy and known to incorrectly return filesize 0 for all files
	if ((f.isVideo() || f.isAudio()) && fsize < minsize && fsize > 0)
		return true

	return false
}


def clean = { f ->
	println "Delete $f"

	// do a dry run via --action test
	if (_args.action == 'test') {
		return false
	}

	return f.isDirectory() ? f.deleteDir() : f.delete()
}


// memoize media folder status for performance
def hasMediaFiles = { dir -> dir.isDirectory() && dir.getFiles().find{ (it.isVideo() || it.isAudio()) && !isClutter(it) } }.memoize()

// delete clutter files in orphaned media folders
args.getFiles{ isClutter(it) && !hasMediaFiles(it.dir) }.each{ clean(it) }

// delete empty folders but exclude given args
args.getFolders().sort().reverse().each{
	if (it.isDirectory() && it.listFiles{ it.isDirectory() || !isClutter(it) }.isEmpty()) {
		if (deleteRootFolder || !args.contains(it)) 
			clean(it)
	}
}
