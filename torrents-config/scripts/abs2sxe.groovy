// filebot -script fn:abs2sxe /path/to/files --log off

def stage0 = args.getFiles{ it.isVideo() }.sort()

// normalize absolute number format and add title and release date info that we can match by in the second step
def stage1 = rename(file:stage0.sort(), action:'rename', format:'{n} - {[e.pad(5)]} - {t} - {[d]}', db:'TheTVDB', order:'absolute', filter:'episode.season == null && episode.special == null', strict:false)

if (stage1 == null) die('Failure at Stage 0')

// process again, match by title/date and output SxE according to --format
def stage2 = rename(file:stage1.sort(), db:'TheTVDB', order:'airdate', filter:'episode.special == null', strict:false)


// print output statistics
log.info "\n-------------------- abs2sxe --------------------\n"

def renameMap = getRenameLog()
stage0.each{ f0 ->
	def f1 = renameMap[f0]
	def f2 = renameMap[f1]
	println([f0, f1, f2].name.join('\t->\t'))
}
